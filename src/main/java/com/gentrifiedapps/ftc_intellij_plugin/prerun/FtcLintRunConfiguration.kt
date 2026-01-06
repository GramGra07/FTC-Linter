package com.gentrifiedapps.ftc_intellij_plugin.prerun

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.KillableColoredProcessHandler
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBLabel
import java.awt.BorderLayout
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.swing.JComponent
import javax.swing.JPanel

class FtcLintRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<Any>(project, factory, name) {

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> = object : SettingsEditor<FtcLintRunConfiguration>() {
        override fun resetEditorFrom(s: FtcLintRunConfiguration) {}
        override fun applyEditorTo(s: FtcLintRunConfiguration) {}
        override fun createEditor(): JComponent = JPanel(BorderLayout()).apply {
            add(JBLabel("Runs FTC Linter checks and fails on issues. No settings."), BorderLayout.CENTER)
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        val project = environment.project

        data class Counts(val javaParse: Int, val ktParse: Int, val javaUnresolved: Int, val teamFiles: Int)
        var counts = Counts(0, 0, 0, 0)
        var totalFindings = 0

        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            DumbService.getInstance(project).waitForSmartMode()

            counts = ReadAction.compute<Counts, RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                
                // Search for Java files
                val javaFiles = LinkedHashSet<VirtualFile>().apply {
                    addAll(FileTypeIndex.getFiles(JavaFileType.INSTANCE, scope))
                    addAll(FilenameIndex.getAllFilesByExt(project, "java", scope))
                }
                
                // Search for Kotlin files by extension to avoid direct dependency on KotlinFileType
                val ktFiles = LinkedHashSet<VirtualFile>().apply {
                    addAll(FilenameIndex.getAllFilesByExt(project, "kt", scope))
                }

                // Fallback: enumerate TeamCode module source roots if indexes are empty
                if (javaFiles.isEmpty() && ktFiles.isEmpty()) {
                    val teamModules = ModuleManager.getInstance(project).modules.filter { it.name.contains("TeamCode", true) }
                    teamModules.forEach { m ->
                        ModuleRootManager.getInstance(m).sourceRoots.forEach { root ->
                            VfsUtilCore.iterateChildrenRecursively(root, null) { vf ->
                                if (!vf.isDirectory) {
                                    when (vf.extension?.lowercase(Locale.getDefault())) {
                                        "java" -> javaFiles.add(vf)
                                        "kt" -> ktFiles.add(vf)
                                    }
                                }
                                true
                            }
                        }
                    }
                }

                var javaParse = 0
                var ktParse = 0
                var javaUnresolved = 0
                var team = 0
                val psiManager = PsiManager.getInstance(project)

                fun isTeam(vf: VirtualFile, psi: PsiFile?): Boolean {
                    val module = com.intellij.openapi.module.ModuleUtilCore.findModuleForFile(vf, project)
                    if (module?.name?.contains("TeamCode", ignoreCase = true) == true) return true
                    val pathLc = vf.path.lowercase(Locale.getDefault())
                    if (pathLc.contains("/teamcode/") || pathLc.contains("\\teamcode\\") || pathLc.endsWith("/teamcode") || pathLc.endsWith("\\teamcode")) return true
                    
                    if (psi is PsiJavaFile) {
                        if (psi.packageName.contains("teamcode", ignoreCase = true)) return true
                    }
                    // For Kotlin files, we check the package name via text to avoid KtFile dependency
                    if (vf.extension == "kt" && psi != null) {
                        val firstLines = psi.text.split("\n").take(10)
                        if (firstLines.any { it.trim().startsWith("package") && it.contains("teamcode", ignoreCase = true) }) return true
                    }
                    return false
                }

                for (vf in javaFiles) {
                    val psi = psiManager.findFile(vf) as? PsiJavaFile
                    if (!isTeam(vf, psi)) continue
                    team++
                    if (psi != null) {
                        val pe = PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java)
                        if (pe != null) javaParse++
                        psi.accept(object : JavaRecursiveElementVisitor() {
                            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                                super.visitReferenceExpression(expression)
                                if (expression.parent is PsiImportStatementBase) return
                                if (expression.resolve() == null) javaUnresolved++
                            }
                        })
                    }
                }
                for (vf in ktFiles) {
                    val psi = psiManager.findFile(vf)
                    if (!isTeam(vf, psi)) continue
                    team++
                    // Check for syntax errors generically
                    val pe = PsiTreeUtil.findChildOfType(psi, PsiErrorElement::class.java)
                    if (pe != null) ktParse++
                }
                Counts(javaParse, ktParse, javaUnresolved, team)
            }

            totalFindings = FtcInspectionRunner(project).runFatalInspections().size
        }, "FTC Linter: Inspecting Project", false, project)

        val parseTotal = counts.javaParse + counts.ktParse
        val unresolved = counts.javaUnresolved
        if (parseTotal > 0 || unresolved > 0 || totalFindings > 0) {
            val parts = ArrayList<String>()
            if (totalFindings > 0) parts += "$totalFindings FTC issue(s)"
            if (parseTotal > 0) parts += "$parseTotal file(s) with syntax errors"
            if (unresolved > 0) parts += "$unresolved unresolved reference(s)"
            val msg = "FTC Lint failed: ${parts.joinToString(", ")}."
            throw ExecutionException(msg)
        }

        return object : CommandLineState(environment) {
            override fun startProcess(): ProcessHandler {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val successMsg = "FTC Lint passed: 0 issues, scanned ${counts.teamFiles} TeamCode file(s)."
                val cmd = if (isWindows) {
                    GeneralCommandLine("cmd", "/c", "echo $successMsg")
                } else {
                    GeneralCommandLine("/bin/sh", "-c", "echo $successMsg")
                }.withCharset(StandardCharsets.UTF_8)
                return KillableColoredProcessHandler(cmd)
            }
        }
    }
}
