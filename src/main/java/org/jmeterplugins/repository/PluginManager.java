package org.jmeterplugins.repository;

import co.anbora.labs.jmeter.ide.toolchain.JMeterToolchainService;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;
import org.anbora.labs.jmeter.plugins.manager.InstallLib;
import org.apache.jmeter.assertions.Assertion;
import org.apache.jmeter.config.ConfigElement;
import org.apache.jmeter.control.Controller;
import org.apache.jmeter.engine.JMeterEngine;
import org.apache.jmeter.gui.JMeterGUIComponent;
import org.apache.jmeter.processor.PostProcessor;
import org.apache.jmeter.processor.PreProcessor;
import org.apache.jmeter.samplers.SampleListener;
import org.apache.jmeter.samplers.Sampler;
import org.apache.jmeter.testbeans.TestBean;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.reflect.ClassFinder;
import org.jmeterplugins.repository.exception.DownloadException;
import org.jmeterplugins.repository.http.StatsReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class PluginManager {
  private static final Logger log =
      LoggerFactory.getLogger(PluginManager.class);
  private static PluginManager staticManager = new PluginManager();
  private final JARSource jarSource;
  private boolean isSendRepoStats = true;
  protected Map<Plugin, Boolean> allPlugins = new HashMap<>();

  public PluginManager() {
    String sysProp = System.getProperty("jpgc.repo.address",
                                        "https://jmeter-plugins.org/repo/");
    String jmProp = JMeterUtils.getPropDefault("jpgc.repo.address", sysProp);
    File jsonFile = new File(jmProp);
    if (jsonFile.isFile()) {
      jarSource = new JARSourceFilesystem(jsonFile);
    } else {
      jarSource = new JARSourceHTTP(jmProp);
    }
  }

  public boolean hasPlugins() { return allPlugins.size() > 0; }

  public synchronized void load() throws Throwable {
    detectJARConflicts();

    if (hasPlugins()) {
      return;
    }

    JSON json = jarSource.getRepo();

    if (!(json instanceof JSONArray)) {
      throw new RuntimeException("Result is not array");
    }

    for (Object elm : (JSONArray)json) {
      if (elm instanceof JSONObject) {
        Plugin plugin = Plugin.fromJSON((JSONObject)elm);
        if (plugin.getName().isEmpty()) {
          log.debug("Skip empty name: " + plugin);
          continue;
        }

        if (!plugin.isVirtual()) {
          plugin.detectInstalled(getInstalledPlugins());
        }
        allPlugins.put(plugin, plugin.isInstalled());
      } else {
        log.warn("Invalid array element: " + elm);
      }
    }

    // after all usual plugins detected, detect virtual sets
    for (Plugin plugin : allPlugins.keySet()) {
      if (plugin.isVirtual()) {
        plugin.detectInstalled(getInstalledPlugins());
        allPlugins.put(plugin, plugin.isInstalled());
      }
    }

    if (isSendRepoStats &&
        JMeterUtils.getPropDefault("jpgc.repo.sendstats", "true")
            .equals("true")) {
      try {
        StatsReporter reporter = new StatsReporter(jarSource, getUsageStats());
        log.debug("Start sending repo stats");
        reporter.start();
      } catch (Exception e) {
        log.debug("Failed to report usage stats", e);
      }
    }

    log.info("Plugins Status: " + getAllPluginsStatusString());
  }

  private void checkRW()
      throws AccessDeniedException {
      File libext =
              Objects.requireNonNull(JMeterToolchainService.Companion.getToolchainSettings().toolchain().stdlibExtDir()).toFile();
      if (!isWritable(libext)) {
        String msg =
            "Have no write access for JMeter directories, not possible to use Plugins Manager: ";
        throw new AccessDeniedException(msg + libext);
      }
  }

  private boolean isWritable(File path) {
    File sample = new File(path, "empty.txt");
    try {
      sample.createNewFile();
      sample.delete();
      return true;
    } catch (IOException e) {
      log.debug("Write check failed for " + path, e);
      return false;
    }
  }

  public CompletableFuture<Process> startModifications(Set<Plugin> delPlugins,
                                                       Set<Plugin> installPlugins,
                                                       Set<Library.InstallationInfo> installLibs,
                                                       Set<String> libDeletions, boolean doRestart,
                                                       LinkedList<String> additionalJMeterOptions) {
    ChangesMaker maker = new ChangesMaker(allPlugins);
    return CompletableFuture.supplyAsync(() -> {
      try {
        File moveFile = maker.getMovementsFile(delPlugins, installPlugins,
                installLibs, libDeletions);
        File installFile = maker.getInstallFile(installPlugins, installLibs);
        File restartFile;
        if (doRestart) {
          restartFile = maker.getRestartFile(additionalJMeterOptions);
        } else {
          restartFile = null;
        }
        return new InstallLib(moveFile, installFile, restartFile);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }).thenCompose(installLib -> {
      try {
        final ProcessBuilder builder =
                maker.getProcessBuilder(installLib.moveFile(), installLib.installFile(), installLib.restartFile());
        log.info("JAR Modifications log will be saved into: " +
                builder.redirectOutput().file().getPath());
        Process p = builder.start();
        return p.onExit();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  public CompletableFuture<Process> applyChanges(GenericCallback<String> statusChanged,
                           boolean doRestart,
                           LinkedList<String> additionalJMeterOptions) {
    try {
      checkRW();
    } catch (Throwable e) {
      throw new RuntimeException("Cannot apply changes: " + e.getMessage(), e);
    }

    DependencyResolver resolver = new DependencyResolver(allPlugins);
    Set<Plugin> additions = resolver.getAdditions();
    Set<Library.InstallationInfo> libInstalls = new HashSet<>();

    for (Map.Entry<String, String> entry :
         resolver.getLibAdditions().entrySet()) {
      try {
        JARSource.DownloadResult dwn =
            jarSource.getJAR(entry.getKey(), entry.getValue(), statusChanged);
        libInstalls.add(new Library.InstallationInfo(
            entry.getKey(), dwn.getTmpFile(), dwn.getFilename()));
      } catch (Throwable e) {
        String msg = "Failed to download " + entry.getKey();
        log.error(msg, e);
        statusChanged.notify(msg);
        throw new DownloadException(
            "Failed to download library " + entry.getKey(), e);
      }
    }

    for (Plugin plugin : additions) {
      try {
        plugin.download(jarSource, statusChanged);
      } catch (IOException e) {
        String msg = "Failed to download " + plugin;
        log.error(msg, e);
        statusChanged.notify(msg);
        throw new DownloadException("Failed to download plugin " + plugin, e);
      }
    }

    if (doRestart) {
      log.info("Restarting JMeter...");
      statusChanged.notify("Restarting JMeter...");
    }

    Set<String> libDeletions = new HashSet<>();
    for (String lib : resolver.getLibDeletions()) {
      libDeletions.add(Plugin.getLibInstallPath(lib));
    }

    return modifierHook(resolver.getDeletions(), additions, libInstalls, libDeletions,
                 doRestart, additionalJMeterOptions);
  }

  private CompletableFuture<Process> modifierHook(final Set<Plugin> deletions,
                            final Set<Plugin> additions,
                            final Set<Library.InstallationInfo> libInstalls,
                            final Set<String> libDeletions,
                            final boolean doRestart,
                            final LinkedList<String> additionalJMeterOptions) {
    if (deletions.isEmpty() && additions.isEmpty() && libInstalls.isEmpty() &&
        libDeletions.isEmpty()) {
      log.info("Finishing without changes");
      return CompletableFuture.failedFuture(new RuntimeException("Finishing without changes"));
    } else {
      log.info("Plugins manager will apply some modifications");
      return startModifications(deletions, additions, libInstalls, libDeletions,
              doRestart, additionalJMeterOptions);
    }
  }

  protected String[] getUsageStats() {
    ArrayList<String> data = new ArrayList<>();
    data.add(JMeterUtils.getJMeterVersion());

    for (Plugin p : getInstalledPlugins()) {
      data.add(p.getID() + "=" + p.getInstalledVersion());
    }
    log.debug("Usage stats: " + data);
    return data.toArray(new String[0]);
  }

  public String getChangesAsText() {
    DependencyResolver resolver = new DependencyResolver(allPlugins);

    StringBuilder text = new StringBuilder();

    for (Plugin pl : resolver.getDeletions()) {
      text.append("Uninstall plugin: ")
          .append(pl)
          .append(" ")
          .append(pl.getInstalledVersion())
          .append("\n");
    }

    for (String pl : resolver.getLibDeletions()) {
      text.append("Uninstall library: ").append(pl).append("\n");
    }

    for (String pl : resolver.getLibAdditions().keySet()) {
      text.append("Install library: ").append(pl).append("\n");
    }

    for (Plugin pl : resolver.getAdditions()) {
      text.append("Install plugin: ")
          .append(pl)
          .append(" ")
          .append(pl.getCandidateVersion())
          .append("\n");
    }

    return text.toString();
  }

  public Set<Plugin> getInstalledPlugins() {
    Set<Plugin> result = new TreeSet<>(new PluginComparator());
    for (Plugin plugin : allPlugins.keySet()) {
      if (plugin.isInstalled()) {
        result.add(plugin);
      }
    }
    return result;
  }

  public static Set<Plugin>
  getInstalledPlugins(Map<Plugin, Boolean> allPlugins) {
    Set<Plugin> result = new HashSet<>();
    for (Plugin plugin : allPlugins.keySet()) {
      if (plugin.isInstalled()) {
        result.add(plugin);
      }
    }
    return result;
  }

  public Set<Plugin> getAvailablePlugins() {
    Set<Plugin> result = new TreeSet<>(new PluginComparator());
    for (Plugin plugin : allPlugins.keySet()) {
      if (!plugin.isInstalled()) {
        result.add(plugin);
      }
    }
    return result;
  }

  public Set<Plugin> getUpgradablePlugins() {
    Set<Plugin> result = new TreeSet<>(new PluginComparator());
    for (Plugin plugin : allPlugins.keySet()) {
      if (plugin.isUpgradable()) {
        result.add(plugin);
      }
    }
    return result;
  }

  public void togglePlugins(Set<Plugin> pluginsToInstall, boolean isInstall) {
    for (Plugin plugin : pluginsToInstall) {
      toggleInstalled(plugin, isInstall);
    }
  }

  public void toggleInstalled(Plugin plugin, boolean cbState) {
    if (!cbState && !plugin.canUninstall()) {
      log.warn("Cannot uninstall plugin: " + plugin);
      cbState = true;
    }
    allPlugins.put(plugin, cbState);
  }

  public boolean hasAnyUpdates() {
    for (Plugin p : allPlugins.keySet()) {
      if (p.isUpgradable()) {
        return true;
      }
    }
    return false;
  }

  public Plugin getPluginByID(String key) {
    for (Plugin p : allPlugins.keySet()) {
      if (p.getID().equals(key)) {
        return p;
      }
    }
    throw new IllegalArgumentException("Plugin not found in repo: " + key);
  }

  private class PluginComparator implements Comparator<Plugin> {
    @Override
    public int compare(Plugin o1, Plugin o2) {
      return o1.getName().compareTo(o2.getName());
    }
  }

  public void setTimeout(int timeout) { jarSource.setTimeout(timeout); }

  /**
   * @return Static instance of manager, used to spare resources on repo loading
   */
  public static PluginManager getStaticManager() {
    try {
      staticManager.load();
    } catch (Throwable e) {
      throw new RuntimeException("Failed to get plugin repositories", e);
    }
    return staticManager;
  }

  /**
   * @param id ID of the plugin to check
   * @return Version name for the plugin if it is installed, null otherwise
   */
  public static String getPluginStatus(String id) {
    PluginManager manager = getStaticManager();

    for (Plugin plugin : manager.allPlugins.keySet()) {
      if (plugin.id.equals(id)) {
        return plugin.getInstalledVersion();
      }
    }
    return null;
  }

  /**
   * @return Status for all plugins
   */
  public static String getAllPluginsStatus() {
    PluginManager manager = getStaticManager();
    return manager.getAllPluginsStatusString();
  }

  private String getAllPluginsStatusString() {
    ArrayList<String> res = new ArrayList<>();
    for (Plugin plugin : getInstalledPlugins()) {
      res.add(plugin.getID() + "=" + plugin.getInstalledVersion());
    }
    return Arrays.toString(res.toArray());
  }

  /**
   * @return Available plugins
   */
  public static String getAvailablePluginsAsString() {
    PluginManager manager = getStaticManager();
    return manager.getAvailablePluginsString();
  }

  private String getAvailablePluginsString() {
    ArrayList<String> res = new ArrayList<>();
    for (Plugin plugin : getAvailablePlugins()) {
      List<String> versions = new ArrayList<>(plugin.getVersions());
      Collections.reverse(versions);
      res.add(plugin.getID() + "=" + Arrays.toString(versions.toArray()));
    }
    return Arrays.toString(res.toArray());
  }

  /**
   * @return Upgradable plugins
   */
  public static String getUpgradablePluginsAsString() {
    PluginManager manager = getStaticManager();
    return manager.getUpgradablePluginsString();
  }

  private String getUpgradablePluginsString() {
    ArrayList<String> res = new ArrayList<>();
    for (Plugin plugin : getUpgradablePlugins()) {
      res.add(plugin.getID() + "=" + plugin.getMaxVersion());
    }
    return (res.size() != 0) ? Arrays.toString(res.toArray())
                             : "There is nothing to update.";
  }

  public static void detectJARConflicts() {
    String[] paths =
        System.getProperty("java.class.path").split(File.pathSeparator);
    final Map<String, String> jarNames = new HashMap<>();

    for (String path : paths) {
      String name = path;
      int start = path.lastIndexOf(File.separator);
      if (start > 0) {
        name = name.substring(start + 1);
      }
      if (path.endsWith(".jar")) {
        name = name.substring(0, name.length() - 4);
      }
      name = removeJARVersion(name);
      if (jarNames.containsKey(name)) {
        log.warn("Found JAR conflict: " + path + " and " + jarNames.get(name));
      }
      jarNames.put(name, path);
    }
  }

  protected static String removeJARVersion(String path) {
    StringBuilder result = new StringBuilder();
    String data[] = path.split("-");
    for (int i = 0; i < data.length; i++) {
      String ch = data[i];
      if (!ch.isEmpty() &&
          (!Character.isDigit(ch.charAt(0)) || (i < data.length - 1))) {
        result.append(ch);
      }
    }
    return result.toString();
  }

  public void logPluginComponents() {
    StringBuilder report = new StringBuilder("Plugin Components:\n");
    for (Plugin plugin : getInstalledPlugins()) {
      try {
        Class[] superClasses = {
            Sampler.class,       Controller.class,     Timer.class,
            ConfigElement.class, PreProcessor.class,   PostProcessor.class,
            Assertion.class,     SampleListener.class, JMeterGUIComponent.class,
            TestBean.class};
        String[] searchPaths = {plugin.installedPath};
        List<String> list =
            ClassFinder.findClassesThatExtend(searchPaths, superClasses);
        report.append(plugin.id)
            .append("\n")
            .append("\"componentClasses\":")
            .append(JSONSerializer.toJSON(list.toArray()).toString())
            .append(",\n");
      } catch (Throwable e) {
        log.error("Failed to get classes", e);
      }
    }
    log.info(report.toString());
  }

  public boolean isSendRepoStats() { return isSendRepoStats; }

  public void setSendRepoStats(boolean sendRepoStats) {
    isSendRepoStats = sendRepoStats;
  }
}
