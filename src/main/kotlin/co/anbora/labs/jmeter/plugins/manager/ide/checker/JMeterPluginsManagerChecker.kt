package co.anbora.labs.jmeter.plugins.manager.ide.checker

import co.anbora.labs.jmeter.ide.checker.CheckerFlavor
import org.anbora.labs.jmeter.plugins.manager.license.CheckLicense

class JMeterPluginsManagerChecker: CheckerFlavor() {
    override fun check(): Boolean = CheckLicense.isLicensed() ?: false
}
