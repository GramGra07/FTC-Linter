package com.gentrifiedapps.ftc_intellij_plugin.settings

import com.gentrifiedapps.ftc_intellij_plugin.prerun.FtcInspectionRunner
import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.util.Key
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationGroupManager

class FtcBeforeRunTaskProvider : BeforeRunTaskProvider<FtcBeforeRunTask>() {
    override fun getId(): Key<FtcBeforeRunTask> = ID
    override fun getName(): String = "FTC Pre-Run Check"
    override fun isConfigurable(): Boolean = false
    override fun createTask(runConfiguration: RunConfiguration): FtcBeforeRunTask = FtcBeforeRunTask(ID)

    override fun executeTask(context: DataContext, configuration: RunConfiguration, env: ExecutionEnvironment, task: FtcBeforeRunTask): Boolean {
        val project = env.project
        val runner = FtcInspectionRunner(project)
        val findings = runner.runFatalInspections()
        if (findings.isNotEmpty()) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("FTC")
                .createNotification("Run blocked: ${findings.size} FTC issue(s) found. See Problems view.", NotificationType.ERROR)
                .notify(project)
            return false
        }
        return true
    }

    companion object { val ID: Key<FtcBeforeRunTask> = Key.create("FTC_BEFORE_RUN") }
}

class FtcBeforeRunTask(providerId: Key<FtcBeforeRunTask>) : BeforeRunTask<FtcBeforeRunTask>(providerId)

