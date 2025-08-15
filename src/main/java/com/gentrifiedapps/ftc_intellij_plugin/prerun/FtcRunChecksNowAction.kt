package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class FtcRunChecksNowAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val findings = FtcInspectionRunner(project).runFatalInspections()
        val msg = if (findings.isEmpty()) "No fatal FTC issues found." else "${findings.size} FTC issue(s) found. See Problems view."
        NotificationGroupManager.getInstance()
            .getNotificationGroup("FTC")
            .createNotification(msg, if (findings.isEmpty()) NotificationType.INFORMATION else NotificationType.WARNING)
            .notify(project)
    }
}