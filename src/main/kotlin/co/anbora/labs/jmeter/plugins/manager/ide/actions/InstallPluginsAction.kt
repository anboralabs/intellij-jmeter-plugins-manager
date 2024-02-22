package co.anbora.labs.jmeter.plugins.manager.ide.actions

import co.anbora.labs.jmeter.plugins.manager.ide.background.InstallPluginTask
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager

class InstallPluginsAction(
    private val project: Project,
    private val pluginManager: PluginManager,
    private val plugins: Set<Plugin>,
    private val testPlan: String
): DumbAwareAction("Install") {
    override fun actionPerformed(e: AnActionEvent) {
        ProgressManager.getInstance().run(InstallPluginTask(project, pluginManager, plugins))
    }
}