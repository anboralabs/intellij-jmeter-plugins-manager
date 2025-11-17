package co.anbora.labs.jmeter.plugins.manager.ide.startup

import co.anbora.labs.jmeter.fileTypes.JMeterFileType
import co.anbora.labs.jmeter.ide.notifications.JMeterNotifications
import co.anbora.labs.jmeter.plugins.manager.ide.actions.InstallPluginsAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import org.anbora.labs.jmeter.plugins.manager.license.CheckLicense
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager
import org.jmeterplugins.repository.plugins.PluginSuggester
import java.util.concurrent.TimeUnit

class InitSuggestPluginListener: ProjectActivity, FileEditorManagerListener.Before {

    override suspend fun execute(project: Project) {
        project.messageBus.connect()
            .subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, this)

        val licensed = CheckLicense.isLicensed()
        if (licensed == null) {
            AppExecutorUtil.getAppScheduledExecutorService().schedule({
                val licensed = CheckLicense.isLicensed() ?: false

                if (!licensed && !project.isDisposed) {
                    CheckLicense.requestLicense("Support plugin buying a license.")
                }
            }, 5, TimeUnit.MINUTES)
            return
        }

        if (!licensed) {
            CheckLicense.requestLicense("Support plugin buying a license.")
        }
    }

    override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension(file.extension.orEmpty())
        if (fileType == JMeterFileType) {
            val pluginsToInstall: MutableSet<Plugin> = HashSet()
            val pluginManager = PluginManager()
            val suggester = PluginSuggester(pluginManager)
            pluginsToInstall.addAll(suggester.analyzeTestPlan(file.path))

            if (pluginsToInstall.isNotEmpty()) {
                val description = pluginsToInstall.joinToString(separator = "\n") { "${it.name}:${it.candidateVersion}" }

                val notification = JMeterNotifications.createNotification(
                    "Please install",
                    "${file.name} needs: $description ",
                    NotificationType.WARNING,
                    InstallPluginsAction(source.project, pluginManager, pluginsToInstall)
                )

                JMeterNotifications.showNotification(notification, source.project)
            }
        }
    }
}