package co.anbora.labs.jmeter.plugins.manager.ide.gui

import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel


class LicenseDialog(
    project: Project
): JEscDialog(project, IdeModalityType.IDE) {

    init {
        title = "Support"
        init()
        initComponents()
    }

    private fun initComponents() {
        val dialogPanel = JPanel(BorderLayout())

        val label = JLabel("Please buy the license if you want to support my work: 5 USD")
        label.preferredSize = Dimension(100, 100)
        dialogPanel.add(label, BorderLayout.CENTER)

        contentPane.add(dialogPanel)
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
}