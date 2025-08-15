package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class FtcTelemetryUpdateInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class InsertTelemetryUpdateQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Insert telemetry.update()"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val anchorEl = descriptor.psiElement ?: return
            val method = generateSequence(anchorEl as PsiElement?) { it.parent }
                .filterIsInstance<PsiMethod>()
                .firstOrNull() ?: return
            val body = method.body ?: return

            val factory = JavaPsiFacade.getElementFactory(project)
            val updateStmt = factory.createStatementFromText("telemetry.update();", null)

            // Try to place right after the offending addData() call statement
            var placed = false
            var p: PsiElement? = anchorEl
            var callExpr: PsiMethodCallExpression? = null
            while (p != null && callExpr == null) {
                callExpr = p as? PsiMethodCallExpression
                p = p.parent
            }
            val stmt = (callExpr?.parent as? PsiExpressionStatement)
            if (stmt != null && stmt.parent is PsiCodeBlock) {
                stmt.parent.addAfter(updateStmt, stmt)
                placed = true
            }
            if (!placed) {
                // Fallback: append at end of method body
                body.add(updateStmt)
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                if (!FtcUtil.isInTeamCode(method)) return
                val cls = method.containingClass ?: return
                if (!FtcUtil.isOpMode(cls)) return
                val body = method.body ?: return

                val addDataCalls = mutableListOf<PsiElement>()
                var hasUpdate = false

                body.accept(object : JavaRecursiveElementVisitor() {
                    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                        super.visitMethodCallExpression(expression)

                        val ref = expression.methodExpression
                        val name = ref.referenceName
                        val qualifierText = ref.qualifierExpression?.text
                        val resolved = expression.resolveMethod()
                        val fqn = resolved?.containingClass?.qualifiedName

                        val isTelemetry = (fqn == "org.firstinspires.ftc.robotcore.external.Telemetry") || (qualifierText == "telemetry")

                        if (!isTelemetry || name == null) return

                        when (name) {
                            "addData" -> {
                                val anchor = ref.referenceNameElement ?: expression
                                addDataCalls += anchor
                            }
                            "update" -> hasUpdate = true
                        }
                    }
                })

                if (!hasUpdate) {
                    val fix = InsertTelemetryUpdateQuickFix()
                    for (anchor in addDataCalls) {
                        holder.registerProblem(
                            anchor,
                            "telemetry.addData() without telemetry.update() in this method",
                            ProblemHighlightType.WARNING,
                            fix
                        )
                    }
                }
            }
        }
    }
}