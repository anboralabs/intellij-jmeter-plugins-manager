package co.anbora.labs.jmeter.plugins.manager.ide.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jmeterplugins.repository.Plugin

class InstallPluginsAction(
    private val plugins: Set<Plugin>
): DumbAwareAction("Install") {
    override fun actionPerformed(e: AnActionEvent) {

    }
}