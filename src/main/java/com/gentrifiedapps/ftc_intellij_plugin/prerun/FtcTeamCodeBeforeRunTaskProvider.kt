// Kotlin
package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.gentrifiedapps.ftc_intellij_plugin.scan.FtcFindingCache
import com.gentrifiedapps.ftc_intellij_plugin.scan.FtcLightScanner
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile

class FtcTeamCodeBeforeRunTaskProvider(private val project: Project) :
    BeforeRunTaskProvider<FtcTeamCodeBeforeRunTask>() {

    companion object {
        val ID: Key<FtcTeamCodeBeforeRunTask> = FtcTeamCodeBeforeRunTask.ID
        private const val NOTIF_GROUP = "FTC Linter"
    }

    override fun getId(): Key<FtcTeamCodeBeforeRunTask> = ID
    override fun getName(): String = "FTC TeamCode guard"

    override fun createTask(runConfiguration: RunConfiguration): FtcTeamCodeBeforeRunTask? =
        FtcTeamCodeBeforeRunTask().apply { isEnabled = true }

    override fun executeTask(
        context: DataContext,
        configuration: RunConfiguration,
        environment: ExecutionEnvironment,
        task: FtcTeamCodeBeforeRunTask
    ): Boolean {
        // 1) Guard duplicate TeamCode folders
        val duplicates = findDuplicates(project)
        if (duplicates.isNotEmpty()) {
            val detail = buildString {
                append("Run aborted: multiple TeamCode folders detected.\n\n")
                append("Allowed: at most one `TeamCode` and one `teamcode`.\n")
                append("Duplicates:\n")
                duplicates.forEach { append(" - ${it.path}\n") }
            }
            Messages.showErrorDialog(project, detail, "FTC Linter")
            return false
        }

        // 2) Run FTC inspections in Smart Mode (indexes ready), then gate
        val total = DumbService.getInstance(project).runReadActionInSmartMode<Int> {
            FtcLightScanner.scanNow(project)
        }
        val cache = FtcFindingCache.getInstance(project)
        val fatal = cache.snapshotFatalCount()

        val summary = "FTC inspections found $total issue(s) (fatal: $fatal) in TeamCode."
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIF_GROUP)
            .createNotification("FTC Linter pre-run", summary,
                if (fatal == 0 && total == 0) NotificationType.INFORMATION else NotificationType.WARNING)
            .notify(project)

        // Block the run if there is any finding (strict). Change to `fatal > 0` to only block on fatal.
        val blockOnAnyFinding = true
        val shouldBlock = if (blockOnAnyFinding) total > 0 else fatal > 0
        if (shouldBlock) {
            val countText = if (blockOnAnyFinding && fatal == 0) total else fatal
            val msg = buildString {
                append("Run aborted: FTC Linter found ")
                append(countText)
                append(" issue(s).\n\nOpen the Problems tool window and fix them before running.")
            }
            Messages.showErrorDialog(project, msg, "FTC Linter")
            return false
        }

        return true
    }

    private fun findDuplicates(project: Project): List<VirtualFile> {
        val index = ProjectFileIndex.getInstance(project)
        val upper = mutableListOf<VirtualFile>()
        val lower = mutableListOf<VirtualFile>()
        index.iterateContent { vf ->
            if (vf.isDirectory) {
                when (vf.name) {
                    "TeamCode" -> upper += vf
                    "teamcode" -> lower += vf
                }
            }
            true
        }
        val dups = mutableListOf<VirtualFile>()
        if (upper.size > 1) dups += upper.sortedBy { it.path }.drop(1)
        if (lower.size > 1) dups += lower.sortedBy { it.path }.drop(1)
        return dups
    }
}