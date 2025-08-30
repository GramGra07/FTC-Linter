package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.gentrifiedapps.ftc_intellij_plugin.scan.FtcFindingCache
import com.gentrifiedapps.ftc_intellij_plugin.scan.FtcLightScanner
import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.RunConfigurationExtension
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiDocumentManager
import org.jdom.Element

/**
 * Validates FTC inspections before run. Cancels run if findings exist.
 */
class FtcRunConfigurationExtension : RunConfigurationExtension() {

    override fun getSerializationId(): String = "ftc.linter.run.extension"
    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean = true
    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = Unit
    override fun writeExternal(runConfiguration: RunConfigurationBase<*>, element: Element) = Unit

    override fun isEnabledFor(applicableConfiguration: RunConfigurationBase<*>, runnerSettings: RunnerSettings?): Boolean = true

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        applicableConfiguration: T,
        javaParameters: JavaParameters,
        runnerSettings: RunnerSettings?
    ) {
        validateOrThrow(applicableConfiguration)
    }

    override fun <T : RunConfigurationBase<*>> updateJavaParameters(
        applicableConfiguration: T,
        javaParameters: JavaParameters,
        runnerSettings: RunnerSettings,
        executor: Executor
    ) {
        validateOrThrow(applicableConfiguration)
    }

    private fun validateOrThrow(configuration: RunConfigurationBase<*>) {
        val project = configuration.project

        // Ensure indices ready and PSI committed before scanning
        DumbService.getInstance(project).waitForSmartMode()
        val app = ApplicationManager.getApplication()
        val commit = {
            WriteIntentReadAction.run<RuntimeException> {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
        }
        if (app.isDispatchThread) commit() else app.invokeAndWait { commit() }

        // Run a synchronous scan; if called on EDT, wrap with a progress to avoid assertions.
        if (app.isDispatchThread) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                FtcLightScanner.scanNow(project)
            }, "FTC Linter: Scanning Project", false, project)
        } else {
            FtcLightScanner.scanNow(project)
        }

        val cache = FtcFindingCache.getInstance(project)
        val total = cache.snapshotFatalCount()
        if (total > 0) {
            // Notify user and cancel run
            notify(project, "Run blocked", "$total FTC issue(s) found. Fix them before running.", NotificationType.ERROR)
            throw ExecutionException("FTC Linter blocked run: $total issue(s) found")
        }
    }

    private fun notify(project: com.intellij.openapi.project.Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("FTC Linter")
            .createNotification(title, content, type)
            .notify(project)
    }
}
