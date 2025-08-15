package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.FtcUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class FtcHardwareInConstructorInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class DeleteStatementQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Delete statement"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val el = descriptor.psiElement ?: return
            val stmt = generateSequence(el) { it.parent }.filterIsInstance<PsiStatement>().firstOrNull() ?: return
            stmt.delete()
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!FtcUtil.isInTeamCode(method)) return
                if (!method.isConstructor) return
                val cls = method.containingClass ?: return
                if (!FtcUtil.isOpMode(cls)) return
                method.body?.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                        super.visitReferenceExpression(expression)
                        if (expression.referenceName == "hardwareMap") {
                            holder.registerProblem(
                                expression,
                                "Accessing hardwareMap in constructor; delete the statement",
                                ProblemHighlightType.WARNING,
                                DeleteStatementQuickFix()
                            )
                        }
                    }
                })
            }
        }
    }
}