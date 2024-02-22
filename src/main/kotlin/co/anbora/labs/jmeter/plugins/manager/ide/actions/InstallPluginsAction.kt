package co.anbora.labs.jmeter.plugins.manager.ide.actions

import co.anbora.labs.jmeter.plugins.manager.ide.background.InstallPluginTask
import co.anbora.labs.jmeter.plugins.manager.ide.gui.LicenseDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.anbora.labs.jmeter.plugins.manager.license.CheckLicense
import org.jmeterplugins.repository.Plugin
import org.jmeterplugins.repository.PluginManager

class InstallPluginsAction(
    private val project: Project,
    private val pluginManager: PluginManager,
    private val plugins: Set<Plugin>
): DumbAwareAction("Install") {
    override fun actionPerformed(e: AnActionEvent) {
        val licensed = CheckLicense.isLicensed() ?: true
        if (licensed) {
            ProgressManager.getInstance().run(InstallPluginTask(project, pluginManager, plugins))
        } else {
            LicenseDialog(project).showAndGet()
        }
    }
}