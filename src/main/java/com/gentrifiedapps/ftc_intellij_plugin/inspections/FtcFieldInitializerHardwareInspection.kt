package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class FtcFieldInitializerHardwareInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class RemoveInitializerQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Remove hardwareMap initializer"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val expr = descriptor.psiElement as? PsiReferenceExpression ?: return
            val field = generateSequence(expr as PsiElement) { it.parent }.filterIsInstance<PsiField>().firstOrNull() ?: return
            field.initializer = null
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitField(field: PsiField) {
                if (!FtcUtil.isInTeamCode(field)) return
                val cls = field.containingClass ?: return
                if (!FtcUtil.isOpMode(cls)) return
                val initializer = field.initializer ?: return
                initializer.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                        if (expression.referenceName == "hardwareMap" || expression.text.startsWith("hardwareMap.")) {
                            val el: PsiElement = expression
                            holder.registerProblem(
                                el,
                                "HardwareMap used in field initializer; move into runOpMode()/init",
                                ProblemHighlightType.WARNING,
                                RemoveInitializerQuickFix()
                            )
                        }
                    }
                })
            }
        }
    }
}