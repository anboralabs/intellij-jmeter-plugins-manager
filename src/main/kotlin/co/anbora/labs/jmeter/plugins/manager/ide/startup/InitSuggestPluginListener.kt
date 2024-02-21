package co.anbora.labs.jmeter.plugins.manager.ide.startup

import co.anbora.labs.jmeter.fileTypes.JmxFileType
import co.anbora.labs.jmeter.ide.editor.gui.JmxFileEditor
import co.anbora.labs.jmeter.ide.notifications.JMeterNotifications
import co.anbora.labs.jmeter.plugins.manager.ide.actions.InstallPluginsAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager
import org.jmeterplugins.repository.plugins.PluginSuggester

class InitSuggestPluginListener: ProjectActivity, FileEditorManagerListener.Before {

    override suspend fun execute(project: Project) {
        project.messageBus.connect()
            .subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, this)
    }

    override fun beforeFileOpened(source: FileEditorManager, file: VirtualFile) {
        val fileType = FileTypeRegistry.getInstance().getFileTypeByExtension(file.extension.orEmpty())
        if (fileType == JmxFileType) {
            val pluginsToInstall: MutableSet<Plugin> = HashSet()
            val suggester = PluginSuggester(PluginManager())
            pluginsToInstall.addAll(suggester.analyzeTestPlan(file.path))

            if (pluginsToInstall.isNotEmpty()) {
                val description = pluginsToInstall.joinToString(separator = "\n") { "${it.name}:${it.candidateVersion}" }

                val notification = JMeterNotifications.createNotification(
                    "Please install",
                    "${file.name} needs: $description ",
                    NotificationType.WARNING,
                    InstallPluginsAction(pluginsToInstall)
                )

                JMeterNotifications.showNotification(notification, source.project)
            }
        }
    }
}