package com.gentrifiedapps.ftc_intellij_plugin.scan

import com.intellij.codeInspection.*
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.SmartPointerManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcTelemetryUpdateInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcMissingWaitForStartInLinearInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcNestedWhileInActiveLoopInspection
import com.intellij.openapi.module.ModuleUtilCore

object FtcLightScanner {

    private fun fatalInspections(): List<LocalInspectionTool> = listOf(
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcHardwareInConstructorInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcFieldInitializerHardwareInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcGamepadInConstructorInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcThreadSleepInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcMissingWaitForStartInLinearInspection(),
        FtcNestedWhileInActiveLoopInspection(),
        FtcTelemetryUpdateInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcMissingOpModeAnnotationInspection(),
        com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcFieldInitializerHardwareInspection()
    )

    private fun isTeamCode(project: Project, vf: VirtualFile): Boolean {
        val module = ModuleUtilCore.findModuleForFile(vf, project)
        return module?.name?.contains("TeamCode", ignoreCase = true) == true
    }

    /** Warm up the cache in background after startup, TeamCode only. */
    fun scheduleProjectWarmup(project: Project) {
        val cache = FtcFindingCache.getInstance(project)

        ReadAction
            .nonBlocking<Collection<VirtualFile>> {
                val scope = GlobalSearchScope.projectScope(project)
                FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope).filter { isTeamCode(project, it) }
            }
            .inSmartMode(project)
            .withDocumentsCommitted(project)
            .expireWith(project)
            .coalesceBy(cache.warmupCoalesceKey())
            .submit(AppExecutorUtil.getAppExecutorService())
            .onSuccess { files -> scheduleLightScan(project, files) }
    }


    /** Schedule a non-blocking light scan over the given files (TeamCode only). */
    fun scheduleLightScan(project: Project, files: Collection<VirtualFile>) {
        if (files.isEmpty()) return

        val teamFiles = files.filter { isTeamCode(project, it) }
        if (teamFiles.isEmpty()) return

        val cache = FtcFindingCache.getInstance(project)
        val paths = teamFiles.mapNotNull { it.path }
        cache.markDirty(paths)

        ReadAction
            .nonBlocking {
                val mgr = InspectionManager.getInstance(project)
                val spm = SmartPointerManager.getInstance(project)

                for (vf in teamFiles) {
                    ProgressManager.checkCanceled()

                    val psi = ApplicationManager.getApplication().runReadAction<PsiFile?> {
                        PsiManager.getInstance(project).findFile(vf)
                    } as? PsiJavaFile ?: continue


                    val newFindings = ArrayList<FtcFinding>()

                    // Run fatal inspections
                    for (tool in fatalInspections()) {
                        val holder = object : ProblemsHolder(mgr, psi, false) {
                            override fun registerProblem(d: ProblemDescriptor) {
                                super.registerProblem(d)
                                val el = d.psiElement
                                val ptr = if (el != null && el.isValid) spm.createSmartPsiElementPointer(el) else null
                                newFindings += FtcFinding(
                                    tool.shortName,
                                    d.descriptionTemplate,
                                    psi.virtualFile.path,
                                    ptr
                                )
                            }
                        }
                        ApplicationManager.getApplication().runReadAction {
                            psi.accept(tool.buildVisitor(holder, false))
                        }
                    }

                    // Run telemetry update as non-fatal to surface warnings
                    run {
                        val tool = FtcTelemetryUpdateInspection()
                        val holder = object : ProblemsHolder(mgr, psi, false) {
                            override fun registerProblem(d: ProblemDescriptor) {
                                super.registerProblem(d)
                                val el = d.psiElement
                                val ptr = if (el != null && el.isValid) spm.createSmartPsiElementPointer(el) else null
                                newFindings += FtcFinding(
                                    tool.shortName,
                                    d.descriptionTemplate,
                                    psi.virtualFile.path,
                                    ptr
                                )
                            }
                        }
                        ApplicationManager.getApplication().runReadAction {
                            psi.accept(tool.buildVisitor(holder, false))
                        }
                    }

                    cache.put(psi.virtualFile.path, newFindings)
                }
            }
            .inSmartMode(project)
            .withDocumentsCommitted(project)
            .expireWith(project)
            .coalesceBy(cache.coalesceKeyFor(paths))
            .submit(AppExecutorUtil.getAppExecutorService())
    }

}