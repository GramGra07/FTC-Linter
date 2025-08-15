package com.gentrifiedapps.ftc_intellij_plugin

import com.gentrifiedapps.ftc_intellij_plugin.scan.FtcLightScanner
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.module.ModuleUtilCore

class RunCheckerStartup : ProjectActivity {
    override suspend fun execute(project: Project) {
        // Warm-up background scan (TeamCode-only inside scanner)
        FtcLightScanner.scheduleProjectWarmup(project)

        // React to file changes (TeamCode-only)
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    val changed = events.mapNotNull { it.file }
                        .filter { it.fileType == JavaFileType.INSTANCE }
                        .filter { vf ->
                            val module = ModuleUtilCore.findModuleForFile(vf, project)
                            module?.name.equals("TeamCode", ignoreCase = true)
                        }
                    if (changed.isNotEmpty()) {
                        FtcLightScanner.scheduleLightScan(project, changed)
                    }
                }
            }
        )
    }
}