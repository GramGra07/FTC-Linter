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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import java.util.Locale

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

        DumbService.getInstance(project).waitForSmartMode()
        val app = com.intellij.openapi.application.ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            WriteIntentReadAction.run<RuntimeException> {
                PsiDocumentManager.getInstance(project).commitAllDocuments()
            }
        } else {
            app.invokeAndWait {
                WriteIntentReadAction.run<RuntimeException> {
                    PsiDocumentManager.getInstance(project).commitAllDocuments()
                }
            }
        }

        val manager = InspectionManager.getInstance(project)

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val indicator = ProgressManager.getInstance().progressIndicator
            indicator?.text = "Scanning TeamCode for FTC issues…"

            val files = ReadAction.compute<Collection<VirtualFile>, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope)
            }

            val total = files.size.coerceAtLeast(1)
            var idx = 0

            for (vf in files) {
                ProgressManager.checkCanceled()
                indicator?.fraction = idx.toDouble() / total
                indicator?.text2 = vf.path
                idx++

                val psi = ReadAction.compute<PsiFile?, RuntimeException> {
                    PsiManager.getInstance(project).findFile(vf)
                } ?: continue

                val team = ReadAction.compute<Boolean, RuntimeException> { isTeamCode(psi) }
                if (!team) continue

                // Collect via inspection tools
                val startCount = findings.size
                ReadAction.run<RuntimeException> {
                    runInspectionsOnFile(manager, psi) { toolName, descriptor ->
                        findings += Finding(
                            toolShortName = toolName,
                            description = descriptor.descriptionTemplate,
                            file = psi,
                            element = descriptor.psiElement ?: psi
                        )
                    }
                }

                // Fallback: explicitly detect missing waitForStart in LinearOpMode.runOpMode()
                if (findings.size == startCount) {
                    ReadAction.run<RuntimeException> {
                        (psi as? PsiJavaFile)?.let { javaFile ->
                            javaFile.classes.forEach { cls ->
                                if (isLinearOpMode(cls)) {
                                    val method = cls.methods.firstOrNull { it.name == "runOpMode" && it.body != null }
                                    if (method != null && !bodyCallsWaitForStart(method)) {
                                        findings += Finding(
                                            toolShortName = "FtcMissingWaitForStartInLinearInspection",
                                            description = "LinearOpMode.runOpMode() must call waitForStart()",
                                            file = psi,
                                            element = method.nameIdentifier ?: method
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }, "Running FTC Pre-Run Checks", true, project)

        return findings
    }

    private fun isTeamCode(file: PsiFile): Boolean {
        val vf = file.virtualFile ?: return false
        val module = ModuleUtilCore.findModuleForFile(vf, project)
        if (module?.name?.contains("TeamCode", ignoreCase = true) == true) return true
        val path = vf.path.lowercase(Locale.getDefault())
        if (path.contains("/teamcode/") || path.contains("\\teamcode\\") || path.endsWith("/teamcode") || path.endsWith("\\teamcode")) return true
        val pkg = (file as? PsiJavaFile)?.packageName ?: return false
        return pkg.contains("teamcode", ignoreCase = true)
    }

    private fun runInspectionsOnFile(
        manager: InspectionManager,
        file: PsiFile,
        onProblem: (toolShortName: String, ProblemDescriptor) -> Unit
    ) {
        for (tool in fatalInspections()) {
            val seen = HashSet<String>()

            fun keyOf(d: ProblemDescriptor): String {
                val el = d.psiElement
                val vFile = el?.containingFile?.virtualFile?.path ?: file.virtualFile?.path ?: "?"
                val offset = el?.textRange?.startOffset ?: -1
                return tool.shortName + "|" + vFile + "|" + offset + "|" + d.descriptionTemplate
            }

            fun collect(d: ProblemDescriptor) {
                val k = keyOf(d)
                if (seen.add(k)) onProblem(tool.shortName, d)
            }

            // Batch mode
            try {
                tool.checkFile(file, manager, false)?.forEach { collect(it) }
            } catch (_: Throwable) {}

            // On-the-fly mode
            try {
                tool.checkFile(file, manager, true)?.forEach { collect(it) }
            } catch (_: Throwable) {}

            // Visitor, batch
            try {
                val holder = CollectingProblemsHolder(manager, file, false) { d -> collect(d) }
                val visitor = tool.buildVisitor(holder, false)
                file.accept(visitor)
                holder.flush()
            } catch (_: Throwable) {}

            // Visitor, on-the-fly
            try {
                val holder = CollectingProblemsHolder(manager, file, true) { d -> collect(d) }
                val visitor = tool.buildVisitor(holder, true)
                file.accept(visitor)
                holder.flush()
            } catch (_: Throwable) {}
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

    private fun isLinearOpMode(cls: PsiClass): Boolean {
        return InheritanceUtil.isInheritor(cls, "com.qualcomm.robotcore.eventloop.opmode.LinearOpMode") ||
                (cls.extendsList?.referenceElements?.any { it.qualifiedName?.endsWith("LinearOpMode") == true } == true)
    }

    private fun bodyCallsWaitForStart(method: PsiMethod): Boolean {
        var found = false
        method.body?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                if (expression.methodExpression.referenceName == "waitForStart") found = true
            }
        })
        return found
    }
}