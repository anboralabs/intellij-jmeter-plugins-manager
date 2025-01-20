package co.anbora.labs.jmeter.plugins.manager.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object JMeterManagerIcons {

    val MANAGER = getIcon("jmeter-repository.svg")

    private fun getIcon(path: String): Icon {
        return IconLoader.findIcon("/icons/$path", JMeterManagerIcons::class.java.classLoader) as Icon
    }
}
