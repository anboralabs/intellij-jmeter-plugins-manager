package co.anbora.labs.jmeter.plugins.manager.router.actions.flavor

import co.anbora.labs.jmeter.router.actions.RouterActionsFlavor
import org.apache.jmeter.gui.action.Command
import org.apache.jmeter.gui.action.ExitIDECommand

class JMeterRepositoryActions: RouterActionsFlavor() {
    override fun getDefaultCommands(): Collection<Command> {
        return listOf(ExitIDECommand())
    }
}