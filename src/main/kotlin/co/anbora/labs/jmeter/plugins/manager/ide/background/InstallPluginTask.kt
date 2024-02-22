package co.anbora.labs.jmeter.plugins.manager.ide.background

import co.anbora.labs.jmeter.ide.notifications.JMeterNotifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.apache.jmeter.gui.action.ActionNames
import org.apache.jmeter.gui.action.ActionRouter
import org.jmeterplugins.repository.GenericCallback
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager
import java.awt.event.ActionEvent

class InstallPluginTask(
    private val project: Project,
    private val pluginManager: PluginManager,
    private val plugins: Set<Plugin>,
): Task.Backgroundable(project, "Downloading JMeter plugins..."), GenericCallback<String> {
    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true

        pluginManager.togglePlugins(plugins, true)
        pluginManager.applyChanges(this, true, null)
            .thenApply {
            ActionRouter.getInstance().actionPerformed(
                ActionEvent(this, 0, ActionNames.EXIT_IDE)
            )
            null
        }.whenComplete { t, ex ->
                if (ex != null) {
                    this.showErrorNotification(
                        "Failed to apply changes: " +
                                ex.message
                    )
                }
            }
    }

    private fun showErrorNotification(description: String) {
        val notification = JMeterNotifications.createNotification("Installing...", description, NotificationType.ERROR)
        JMeterNotifications.showNotification(notification, project)
    }

    override fun notify(t: String?) = Unit
}