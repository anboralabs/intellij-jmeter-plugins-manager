package org.jmeterplugins.repository;

// import co.anbora.labs.jmeter.ide.settings.Settings;
import co.anbora.labs.jmeter.ide.toolchain.JMeterToolchainService;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.jmeter.engine.JMeterEngine;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Plugin {
  private static final Logger log = LoggerFactory.getLogger(Plugin.class);
  private static final Pattern dependsParser =
      Pattern.compile("([^=<>]+)([=<>]+[0-9.]+)?");
  public static final String VER_STOCK = "0.0.0-STOCK";
  protected JSONObject versions = new JSONObject();
  protected String id;
  protected String markerClass;
  protected String installedPath;
  protected String installedVersion;
  protected String tempName;
  protected String destName;
  protected String name;
  protected String description;
  protected String screenshot;
  protected String helpLink;
  protected String vendor;
  protected String candidateVersion;
  protected String installerClass = null;
  protected List<String> componentClasses;
  protected boolean canUninstall = true;
  private String searchIndexString;

  public Plugin(String aId) { id = aId; }

  public static Plugin fromJSON(JSONObject elm) {
    Plugin inst = new Plugin(elm.getString("id"));
    if (!(elm.get("markerClass") instanceof JSONNull)) {
      inst.markerClass = elm.getString("markerClass");
    }
    inst.componentClasses = new ArrayList<>();
    if (inst.markerClass != null) {
      inst.componentClasses.add(inst.markerClass);
    }
    if (elm.containsKey("componentClasses")) {
      JSONArray componentsJSON = elm.getJSONArray("componentClasses");
      if (componentsJSON.size() > 0) {
        for (int i = 0; i < componentsJSON.size(); i++) {
          inst.componentClasses.add(componentsJSON.getString(i));
        }
      }
    }
    if (elm.get("versions") instanceof JSONObject) {
      inst.versions = elm.getJSONObject("versions");
    }
    inst.name = elm.getString("name");
    inst.description = elm.getString("description");
    if (elm.containsKey("screenshotUrl")) {
      inst.screenshot = elm.getString("screenshotUrl");
    }
    inst.helpLink = elm.getString("helpUrl");
    inst.vendor = elm.getString("vendor");
    if (elm.containsKey("canUninstall")) {
      inst.canUninstall = elm.getBoolean("canUninstall");
    }
    if (elm.containsKey("installerClass")) {
      inst.installerClass = elm.getString("installerClass");
    }
    return inst;
  }

  @Override
  public String toString() {
    return id;
  }

  public void detectInstalled(Set<Plugin> others) {
    if (isVirtual()) {
      detectInstalledVirtual(others);
    } else {
      detectInstalledPlugin();
    }

    if (isInstalled()) {
      candidateVersion = installedVersion;
    } else {
      candidateVersion = getMaxVersion();
    }
  }

  private void detectInstalledPlugin() {
    String path = getJARPath(markerClass);
    if (path != null) {
      try {
        installedPath = URLDecoder.decode(path, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        log.warn("Failed decode plugin installed Path ", e);
        installedPath = path;
      }
    }
    if (installedPath != null) {
      installedVersion = getVersionFromPath(installedPath);
      if (installedVersion.equals(VER_STOCK) && isVersionFrozenToJMeter()) {
        installedVersion = getJMeterVersion();
      }

      log.debug("Found plugin " + this + " version " + installedVersion +
                " at path " + installedPath);
    }
  }

  private void detectInstalledVirtual(Set<Plugin> others) {
    candidateVersion = getMaxVersion();
    log.debug("Detecting virtual " + this + " by depends: " + getDepends());
    for (String depID : getDepends()) {
      installedPath = null;
      for (Plugin plugin : others) {
        if (plugin.getID().equals(depID) && plugin.isInstalled()) {
          installedPath = "";
        }
      }
      if (installedPath == null) {
        break;
      }
    }

    if (isInstalled()) {
      installedVersion = candidateVersion;
    }
  }

  public boolean isVersionFrozenToJMeter() { return versions.containsKey(""); }

  public String getMaxVersion() {
    Set<String> versions = getVersions();
    if (versions.size() > 0) {
      String[] vers = versions.toArray(new String[0]);
      return vers[vers.length - 1];
    }
    return null;
  }

  public Set<String> getVersions() {
    Set<String> versions = new TreeSet<>(new VersionComparator());
    for (Object o : this.versions.keySet()) {
      if (o instanceof String) {
        String ver = (String)o;
        if (ver.isEmpty()) {
          versions.add(getJMeterVersion());
        } else {
          versions.add(ver);
        }
      }
    }

    if (isInstalled()) {
      versions.add(installedVersion);
    }
    return versions;
  }

  public static String getJMeterVersion() {
    String ver = JMeterUtils.getJMeterVersion();
    String[] parts = ver.split(" ");
    if (parts.length > 1) {
      return parts[0];
    }

    return ver;
  }

  public static String getVersionFromPath(String installedPath) {
    Pattern p = Pattern.compile("-([\\.0-9a-zA-Z]+(-[\\w]+)?).jar");
    Matcher m = p.matcher(installedPath);
    if (m.find()) {
      return m.group(1);
    }
    return VER_STOCK;
  }

  public static String getJARPath(String className) {
    Class<?> cls;
    try {
      log.debug("Trying: " + className);
      cls = JMeterUtils.getDynamicLoader().loadClass(className);
    } catch (Throwable e) {
      if (e instanceof ClassNotFoundException) {
        log.debug("Plugin not found by class: " + className);
      } else {
        log.warn("Unable to load class: " + className, e);
      }
      return null;
    }

    // String file =
    // cls.getProtectionDomain().getCodeSource().getLocation().getFile();
    String file = byGetResource(cls);
    if (!file.toLowerCase().endsWith(".jar")) {
      log.warn("Path is not JAR: " + file);
    }

    return file;
  }

  public static String byGetResource(Class clazz) {
    URL classResource = clazz.getResource(clazz.getSimpleName() + ".class");
    if (classResource == null) {
      throw new RuntimeException("class resource is null");
    }
    String url = classResource.toString();
    if (url.startsWith("jar:file:")) {
      // extract 'file:......jarName.jar' part from the url string
      String path = url.replaceAll("^jar:(file:.*[.]jar)!/.*", "$1");
      try {
        return Paths.get(new URL(path).toURI()).toString();
      } catch (Exception e) {
        throw new RuntimeException("Invalid Jar File URL String");
      }
    }
    throw new RuntimeException("Invalid Jar File URL String");
  }

  public static String getLibInstallPath(String lib) {
    try {
      List<String> pathsInternal =
          listFilesUsingDirectoryStream(Objects.requireNonNull(JMeterToolchainService.Companion.getToolchainSettings().toolchain().stdlibExtDir()).toString());
      String[] cp2 = pathsInternal.toArray(String[] ::new);
      String path2 = getLibPath(lib, cp2);
      if (path2 != null)
        return path2;
    } catch (IOException e) {
      log.debug("Error walking plugin lib path: ");
    }

    String[] cp = System.getProperty(DependencyResolver.JAVA_CLASS_PATH)
                      .split(File.pathSeparator);
    return getLibPath(lib, cp);
  }

  public static List<String> listFilesUsingDirectoryStream(String dir)
      throws IOException {
    List<String> fileSet = new LinkedList<>();
    try (DirectoryStream<Path> stream =
             Files.newDirectoryStream(Paths.get(dir))) {
      for (Path path : stream) {
        if (!Files.isDirectory(path)) {
          fileSet.add(path.toAbsolutePath().toString());
        }
      }
    }
    return fileSet;
  }

  public static String getLibPath(String lib, String[] paths) {
    for (String path : paths) {
      Pattern p = Pattern.compile("\\W" + lib + "-([0-9]+\\..+).jar");
      Matcher m = p.matcher(path);
      if (m.find()) {
        log.debug("Found library " + lib + " at " + path);
        return path;
      }
    }
    return null;
  }

  public String getID() { return id; }

  public String getInstalledPath() { return installedPath; }

  public String getDestName() { return destName; }

  public String getTempName() { return tempName; }

  public boolean isInstalled() { return installedPath != null; }

  public void download(JARSource jarSource, GenericCallback<String> notify)
      throws IOException {
    if (isVirtual()) {
      log.debug("Virtual set, won't download: " + this);
      return;
    }

    String version = getCandidateVersion();

    String location = getDownloadUrl(version);

    JARSource.DownloadResult dwn = jarSource.getJAR(id, location, notify);
    tempName = dwn.getTmpFile();
    // File f = new
    // File(JMeterEngine.class.getProtectionDomain().getCodeSource().getLocation().getFile());
    // File f = new File(Plugin.byGetResource(JMeterEngine.class));
    destName = URLDecoder.decode(Objects.requireNonNull(JMeterToolchainService.Companion.getToolchainSettings().toolchain().stdlibExtDir()).toString(), "UTF-8") + File.separator +
               dwn.getFilename();
  }

  /**
   * @param version
   * @return
   */
  public String getDownloadUrl(String version) {
    String location;
    if (isVersionFrozenToJMeter()) {
      String downloadUrl = versions.getJSONObject("").getString("downloadUrl");
      location = String.format(downloadUrl, getJMeterVersion());
    } else {
      if (!versions.containsKey(version)) {
        throw new IllegalArgumentException("Version " + version +
                                           " not found for plugin " + this);
      }
      location = versions.getJSONObject(version).getString("downloadUrl");
    }
    return location;
  }

  public String getName() { return name; }

  public String getDescription() { return description; }

  public String getScreenshot() { return screenshot; }

  public String getHelpLink() { return helpLink; }

  public String getVendor() { return vendor; }

  public String getCandidateVersion() { return candidateVersion; }

  public boolean canUninstall() { return canUninstall; }

  public String getInstalledVersion() {
    if (id.equals("jmeter-core") || id.equals("jpgc-plugins-manager")) {
      return candidateVersion;
    }
    return installedVersion;
  }

  public void setCandidateVersion(String candidateVersion) {
    this.candidateVersion = candidateVersion;
  }

  public boolean isUpgradable() {
    if (!isInstalled()) {
      return false;
    }

    VersionComparator comparator = new VersionComparator();
    return comparator.compare(getInstalledVersion(), getMaxVersion()) < 0;
  }

  public Set<String> getDepends() {
    Set<String> depends = new HashSet<>();
    JSONObject version = versions.getJSONObject(getCandidateVersion());
    if (version.containsKey("depends")) {
      JSONArray list = version.getJSONArray("depends");
      for (Object o : list) {
        if (o instanceof String) {
          String dep = (String)o;
          Matcher m = dependsParser.matcher(dep);
          if (!m.find()) {
            throw new IllegalArgumentException("Cannot parse depend str: " +
                                               dep);
          }
          depends.add(m.group(1));
        }
      }
    }
    return depends;
  }

  public Map<String, String> getLibs(String verStr) {
    Map<String, String> depends = new HashMap<>();
    JSONObject version =
        versions.getJSONObject(isVersionFrozenToJMeter() ? "" : verStr);
    if (version.containsKey("libs")) {
      JSONObject list = version.getJSONObject("libs");
      for (Object o : list.keySet()) {
        if (o instanceof String) {
          String dep = (String)o;
          depends.put(dep, list.getString(dep));
        }
      }
    }
    return depends;
  }

  public Map<String, String> getRequiredLibs(String verStr) {
    Map<String, String> libs = getLibs(verStr);
    Map<String, String> requiredLibs = new HashMap<>();
    for (String libName : libs.keySet()) {
      if (libName.contains(">=")) {
        requiredLibs.put(libName, libs.get(libName));
      }
    }
    return requiredLibs;
  }

  public String getVersionChanges(String versionStr) {
    JSONObject version = versions.getJSONObject(versionStr);
    return version.containsKey("changes") ? version.getString("changes") : null;
  }

  public String getInstallerClass() { return installerClass; }

  public boolean containsComponentClasses(Set<String> classes) {
    for (String cls : componentClasses) {
      if (classes.contains(cls)) {
        return true;
      }
    }
    return false;
  }

  private class VersionComparator implements Comparator<String> {
    @Override
    public int compare(String a, String b) {
      String[] aParts = a.split("\\W+");
      String[] bParts = b.split("\\W+");

      for (int aN = 0; aN < aParts.length; aN++) {
        if (aN < bParts.length) {
          int res = compare2(aParts[aN], bParts[aN]);
          if (res != 0)
            return res;
        }
      }

      return a.compareTo(b);
    }

    private int compare2(String a, String b) {
      if (a.equals(b)) {
        return 0;
      }

      Object ai, bi;
      try {
        ai = Integer.parseInt(a);
      } catch (NumberFormatException e) {
        ai = a;
      }

      try {
        bi = Integer.parseInt(b);
      } catch (NumberFormatException e) {
        bi = b;
      }

      if (ai instanceof Integer && bi instanceof Integer) {
        return Integer.compare((Integer)ai, (Integer)bi);
      } else if (ai instanceof String && bi instanceof String) {
        return ((String)ai).compareTo((String)bi);
      } else if (ai instanceof String) {
        return 1;
      } else {
        return -1;
      }
    }
  }

  public boolean isVirtual() { return markerClass == null; }

  public String getSearchIndexString() {
    if (searchIndexString == null || searchIndexString.isEmpty()) {
      StringBuilder builder = new StringBuilder();
      builder.append(id);
      builder.append(name);
      builder.append(description);
      for (String component : componentClasses) {
        builder.append(component);
      }
      searchIndexString = builder.toString().toLowerCase();
    }

    return searchIndexString;
  }
}
