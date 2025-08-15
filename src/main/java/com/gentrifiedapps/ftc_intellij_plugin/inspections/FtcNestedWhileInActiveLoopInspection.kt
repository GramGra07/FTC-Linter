package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.*

class FtcNestedWhileInActiveLoopInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class ReplaceInnerWhileWithIfQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Replace inner while with if"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val whileStmt = descriptor.psiElement.parent as? PsiWhileStatement ?: return
            val factory = JavaPsiFacade.getElementFactory(project)
            val cond = whileStmt.condition?.text ?: return
            val body = whileStmt.body?.text ?: "{}"
            val ifStmt = factory.createStatementFromText("if ($cond) $body", whileStmt)
            whileStmt.replace(ifStmt)
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitWhileStatement(statement: PsiWhileStatement) {
                if (!FtcUtil.isInTeamCode(statement)) return
                val cls = enclosingClass(statement) ?: return
                if (!FtcUtil.isOpMode(cls)) return

                if (isInsideOpModeIsActiveWhile(statement)) {
                    val anchor: PsiElement = statement.firstChild /* 'while' keyword */ ?: statement
                    holder.registerProblem(
                        anchor,
                        "Nested while-loop inside while(opModeIsActive())",
                        ProblemHighlightType.ERROR,
                        ReplaceInnerWhileWithIfQuickFix()
                    )
                }
            }

            private fun isInsideOpModeIsActiveWhile(stmt: PsiWhileStatement): Boolean {
                var p: PsiElement? = stmt.parent
                while (p != null && p !is PsiMethod && p !is PsiClass && p !is PsiFile) {
                    if (p is PsiWhileStatement) {
                        if (conditionHasOpModeIsActive(p.condition)) return true
                    }
                    p = p.parent
                }
                return false
            }

            private fun conditionHasOpModeIsActive(cond: PsiExpression?): Boolean {
                val text = cond?.text ?: return false
                return text.contains("opModeIsActive()")
            }

            private fun enclosingClass(e: PsiElement): PsiClass? {
                var p: PsiElement? = e
                while (p != null && p !is PsiClass) p = p.parent
                return p as? PsiClass
            }
        }
    }
}
