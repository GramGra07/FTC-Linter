package com.gentrifiedapps.ftc_intellij_plugin.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

class AttachBeforeRunAction : AnAction(), DumbAware {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        notify(
            project,
            "FTC Linter",
            "To enable the blocker: Run > Edit Configurations… > select your run config > Before launch > + > FTC Linter > move to top.",
            NotificationType.INFORMATION
        )
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("FTC Linter")
            .createNotification(title, content, type)
            .notify(project)
    }
}
