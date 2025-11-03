package co.anbora.labs.jmeter.plugins.manager.errorHandler

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.sentry.Hub
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import java.util.function.Consumer


class SendIssueBackgroundTask(
    project: Project?,
    private val events: Array<out IdeaLoggingEvent>,
    private val consumer: Consumer<Unit>
): Task.Backgroundable(project, "Sending error report") {
    override fun run(indicator: ProgressIndicator) {

        val options = SentryOptions()
        options.dsn = "https://3855b16efaf66ef52f0f87aee4b1327c@o370368.ingest.sentry.io/4506787329146880"
        // Set tracesSampleRate to 1.0 to capture 100% of transactions for performance monitoring.
        // We recommend adjusting this value in production.
        options.tracesSampleRate = 1.0
        val hub = Hub(options)

        val plugin = PluginManager.getInstance().findEnabledPlugin(PluginId.getId("co.anbora.labs.jmeter.plugins.manager"))

        events.forEach {
            val event = SentryEvent()
            event.level = SentryLevel.ERROR

            event.release = plugin?.version
            // set server name to empty to avoid tracking personal data
            event.serverName = ""

            event.throwable = it.throwable

            event.setExtra("last_action", IdeaLogger.ourLastActionId)

            // by default, Sentry is sending async in a background thread
            hub.captureEvent(event)
        }

        consumer.accept(Unit)
    }
}