package co.anbora.labs.jmeter.plugins.manager.ide.actions

import co.anbora.labs.jmeter.ide.notifications.JMeterNotifications
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.apache.jmeter.gui.action.ActionNames
import org.apache.jmeter.gui.action.ActionRouter
import org.jmeterplugins.repository.GenericCallback
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager
import org.jmeterplugins.repository.exception.DownloadException
import java.awt.event.ActionEvent

class InstallPluginsAction(
    private val project: Project,
    private val pluginManager: PluginManager,
    private val plugins: Set<Plugin>,
    private val testPlan: String
): DumbAwareAction("Install"), GenericCallback<String> {
    override fun actionPerformed(e: AnActionEvent) {
        pluginManager.togglePlugins(plugins, true)
        pluginManager.applyChanges(this, true, null)
            .thenApply {
                ActionRouter.getInstance().actionPerformed(
                    ActionEvent(this, 0, ActionNames.EXIT_IDE)
                )
                null
            }
            .whenComplete { t, ex ->
                if (ex != null) {
                    this.notify(
                        "Failed to apply changes: " +
                                ex.message
                    )
                }
            }
    }

    override fun notify(t: String?) {
        val notification = JMeterNotifications.createNotification(
            "Installing...",
            t.orEmpty(),
            NotificationType.INFORMATION
        )

        JMeterNotifications.showNotification(notification, project)
    }
}