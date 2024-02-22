package co.anbora.labs.jmeter.plugins.manager.errorHandler

import com.intellij.diagnostic.DiagnosticBundle
import com.intellij.openapi.diagnostic.ErrorReportSubmitter

abstract class INReporterCompat: ErrorReportSubmitter() {

    override fun getPrivacyNoticeText(): String = DiagnosticBundle.message("error.dialog.notice.anonymous")

    override fun getReporterAccount(): String? = null
}