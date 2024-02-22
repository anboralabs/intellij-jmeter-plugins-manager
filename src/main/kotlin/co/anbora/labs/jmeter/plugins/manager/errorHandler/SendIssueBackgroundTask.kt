package co.anbora.labs.jmeter.plugins.manager.errorHandler

import com.intellij.diagnostic.IdeaReportingEvent
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.idea.IdeaLogger
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import io.sentry.Hub
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import java.util.function.Consumer


class SendIssueBackgroundTask(
    private val project: Project?,
    private val pluginDescriptor: PluginDescriptor,
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

        val event = SentryEvent()
        event.level = SentryLevel.ERROR

        if (pluginDescriptor is IdeaPluginDescriptor) {
            event.release = pluginDescriptor.getVersion()
        }
        // set server name to empty to avoid tracking personal data
        event.serverName = ""

        // now, attach all exceptions to the message
        //List<SentryException> errors = new ArrayList<>(events.length);
        for (ideaEvent in events) {
            // this is the tricky part
            // ideaEvent.throwable is a com.intellij.diagnostic.IdeaReportingEvent.TextBasedThrowable
            // This is a wrapper and is only providing the original stacktrace via 'printStackTrace(...)',
            // but not via 'getStackTrace()'.
            //
            // Sentry accesses Throwable.getStackTrace(),
            // So, we workaround this by retrieving the original exception from the data property
            if (ideaEvent is IdeaReportingEvent) {
                val ex: Throwable = ideaEvent.data.throwable
                event.throwable = ex
                break
            } else {
                // ignoring this ideaEvent, you might not want to do this
            }
        }
        //event.setExceptions(errors);
        // might be useful to debug the exception
        //event.setExceptions(errors);
        // might be useful to debug the exception
        event.setExtra("last_action", IdeaLogger.ourLastActionId)

        // by default, Sentry is sending async in a background thread

        // by default, Sentry is sending async in a background thread
        hub.captureEvent(event)

        consumer.accept(Unit)
    }
}