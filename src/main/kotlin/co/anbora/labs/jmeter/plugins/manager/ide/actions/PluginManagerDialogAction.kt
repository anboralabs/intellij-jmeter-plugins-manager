package co.anbora.labs.jmeter.plugins.manager.ide.actions

import co.anbora.labs.jmeter.ide.settings.JMeterProjectSettingsConfigurable
import co.anbora.labs.jmeter.ide.toolchain.JMeterToolchainService.Companion.toolchainSettings
import co.anbora.labs.jmeter.plugins.manager.ide.gui.LicenseDialog
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.ProjectManager
import org.anbora.labs.jmeter.plugins.manager.license.CheckLicense
import org.jmeterplugins.repository.PluginManager
import org.jmeterplugins.repository.PluginManagerDialog

class PluginManagerDialogAction: AnAction() {
    override fun actionPerformed(p0: AnActionEvent) {
        val project = p0.project ?: ProjectManager.getInstance().defaultProject
        val toolchain = toolchainSettings.toolchain()
        val licensed = CheckLicense.isLicensed() ?: true

        if (licensed) {
            if (toolchain.isValid()) {
                val dialog = PluginManagerDialog(project, PluginManager())

                dialog.pack()
                dialog.show()
            } else {
                JMeterProjectSettingsConfigurable.show(project)
            }
        } else {
            LicenseDialog(project).showAndGet()
        }
    }
}
