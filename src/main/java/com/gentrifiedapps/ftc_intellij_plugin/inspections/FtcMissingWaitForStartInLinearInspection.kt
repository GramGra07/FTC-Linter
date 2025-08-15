package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.FtcUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class FtcMissingWaitForStartInLinearInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class InsertWaitForStartQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Insert waitForStart()"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val anchorEl = descriptor.psiElement ?: return
            val method = generateSequence(anchorEl as PsiElement?) { it.parent }
                .filterIsInstance<PsiMethod>()
                .firstOrNull() ?: return
            val body = method.body ?: return
            val factory = JavaPsiFacade.getElementFactory(project)
            val stmt = factory.createStatementFromText("waitForStart();", null)

            // Find first if/while whose condition references opModeIsActive(); insert before it
            var insertionPoint: PsiStatement? = null
            body.accept(object : JavaRecursiveElementVisitor() {
                override fun visitIfStatement(statement: PsiIfStatement) {
                    super.visitIfStatement(statement)
                    if (insertionPoint != null) return
                    if (conditionCallsOpModeIsActive(statement.condition)) {
                        insertionPoint = statement
                    }
                }
                override fun visitWhileStatement(statement: PsiWhileStatement) {
                    super.visitWhileStatement(statement)
                    if (insertionPoint != null) return
                    if (conditionCallsOpModeIsActive(statement.condition)) {
                        insertionPoint = statement
                    }
                }

                private fun conditionCallsOpModeIsActive(condition: PsiExpression?): Boolean {
                    var found = false
                    condition?.accept(object : JavaRecursiveElementVisitor() {
                        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                            super.visitMethodCallExpression(expression)
                            if (expression.methodExpression.referenceName == "opModeIsActive") {
                                found = true
                            }
                        }
                    })
                    return found
                }
            })

            val codeBlock = body
            if (insertionPoint != null) {
                codeBlock.addBefore(stmt, insertionPoint)
            } else {
                val first = codeBlock.firstBodyElement
                if (first != null) codeBlock.addBefore(stmt, first) else codeBlock.add(stmt)
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!FtcUtil.isInTeamCode(method)) return
                val cls = method.containingClass ?: return
                if (!FtcUtil.isLinearOpMode(cls)) return
                if (method.name != "runOpMode") return
                val body = method.body ?: return

                var hasWaitForStart = false
                body.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                        super.visitMethodCallExpression(expression)
                        if (expression.methodExpression.referenceName == "waitForStart") {
                            hasWaitForStart = true
                        }
                    }
                })

                if (!hasWaitForStart) {
                    val anchor: PsiElement = method.nameIdentifier ?: method
                    holder.registerProblem(
                        anchor,
                        "LinearOpMode.runOpMode() must call waitForStart()",
                        ProblemHighlightType.ERROR,
                        InsertWaitForStartQuickFix()
                    )
                }
            }
        }
    }
}
