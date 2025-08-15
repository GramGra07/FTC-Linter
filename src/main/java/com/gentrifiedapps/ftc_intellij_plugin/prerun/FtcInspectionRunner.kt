package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcFieldInitializerHardwareInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcGamepadInConstructorInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcHardwareInConstructorInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcMissingOpModeAnnotationInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcMissingWaitForStartInLinearInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcNestedWhileInActiveLoopInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcTelemetryUpdateInspection
import com.gentrifiedapps.ftc_intellij_plugin.inspections.FtcThreadSleepInspection
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope

class FtcInspectionRunner(private val project: Project) {

    data class Finding(
        val toolShortName: String,
        val description: String,
        val file: PsiFile,
        val element: PsiElement
    )

    private fun fatalInspections(): List<LocalInspectionTool> = listOf(
        FtcHardwareInConstructorInspection(),
        FtcFieldInitializerHardwareInspection(),
        FtcGamepadInConstructorInspection(),
        FtcThreadSleepInspection(),
        FtcMissingWaitForStartInLinearInspection(),
        FtcTelemetryUpdateInspection(),
        FtcMissingOpModeAnnotationInspection(),
        FtcNestedWhileInActiveLoopInspection()
    )

    fun runFatalInspections(): List<Finding> {
        val findings = mutableListOf<Finding>()

        DumbService.Companion.getInstance(project).waitForSmartMode()

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val indicator = ProgressManager.getInstance().progressIndicator
            indicator?.text = "Scanning TeamCode for FTC issues…"

            // collect TeamCode files (READ)
            val files = ApplicationManager.getApplication().runReadAction<Collection<VirtualFile>> {
                val teamCode = ModuleManager.Companion.getInstance(project).findModuleByName("TeamCode")
                if (teamCode == null) emptyList() else FileTypeIndex.getFiles(
                    JavaFileType.INSTANCE,
                    GlobalSearchScope.moduleScope(teamCode)
                )
            }

            val total = files.size.coerceAtLeast(1)
            var idx = 0
            val manager = InspectionManager.getInstance(project)

            for (vf in files) {
                ProgressManager.checkCanceled()
                indicator?.fraction = idx.toDouble() / total
                indicator?.text2 = vf.path
                idx++

                // resolve PSI (READ)
                val psi = ApplicationManager.getApplication().runReadAction<PsiFile?> {
                    PsiManager.getInstance(project).findFile(vf)
                } ?: continue

                // Skip files not in TeamCode just in case
                val module = ModuleUtilCore.findModuleForPsiElement(psi)
                if (module == null || module.name != "TeamCode") continue

                // run inspections (READ)
                ApplicationManager.getApplication().runReadAction {
                    runInspectionsOnFile(manager, psi) { toolName, descriptor ->
                        findings += Finding(
                            toolShortName = toolName,
                            description = descriptor.descriptionTemplate ?: "FTC issue",
                            file = psi,
                            element = descriptor.psiElement ?: psi
                        )
                    }
                }
            }
        }, "Running FTC Pre-Run Checks", true, project)

        return findings
    }


    private fun runInspectionsOnFile(
        manager: InspectionManager,
        file: PsiFile,
        onProblem: (toolShortName: String, ProblemDescriptor) -> Unit
    ) {
        for (tool in fatalInspections()) {
            val holder = CollectingProblemsHolder(manager, file, false) { descriptor ->
                onProblem(tool.shortName, descriptor)
            }
            val visitor = tool.buildVisitor(holder, false)
            file.accept(visitor)
            holder.flush()
        }
    }

    private class CollectingProblemsHolder(
        manager: InspectionManager,
        file: PsiFile,
        isOnTheFly: Boolean,
        private val sink: (ProblemDescriptor) -> Unit
    ) : ProblemsHolder(manager, file, isOnTheFly) {
        override fun registerProblem(descriptor: ProblemDescriptor) {
            super.registerProblem(descriptor)
            sink(descriptor)
        }
        fun flush() { /* no-op */ }
    }
}