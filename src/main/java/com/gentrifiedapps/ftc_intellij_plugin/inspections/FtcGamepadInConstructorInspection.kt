package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor
import org.jetbrains.uast.visitor.AbstractUastVisitor

class FtcGamepadInConstructorInspection : AbstractBaseUastLocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class DeleteStatementQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Delete statement"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val el = descriptor.psiElement ?: return
            val stmt = generateSequence(el) { it.parent }.filterIsInstance<com.intellij.psi.PsiStatement>().firstOrNull() ?: return
            stmt.delete()
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
            override fun visitMethod(node: UMethod): Boolean {
                val psi = node.javaPsi ?: return false
                if (!FtcUtil.isInTeamCode(psi)) return false
                if (!node.isConstructor) return false
                val uClass = node.getContainingUClass() ?: return false
                val psiClass = uClass.javaPsi
                if (!FtcUtil.isOpMode(psiClass)) return false

                val body = node.uastBody ?: return false
                scanBodyForGamepadRefs(body, holder)
                return false
            }

            fun visitClassInitializer(node: UClassInitializer): Boolean {
                val psi = node.javaPsi ?: return false
                if (!FtcUtil.isInTeamCode(psi)) return false
                val uClass = node.getContainingUClass() ?: return false
                val psiClass = uClass.javaPsi
                if (!FtcUtil.isOpMode(psiClass)) return false
                scanBodyForGamepadRefs(node.uastBody, holder)
                return false
            }

            private fun scanBodyForGamepadRefs(body: UExpression?, holder: ProblemsHolder) {
                if (body == null) return
                body.accept(object : AbstractUastVisitor() {
                    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
                        val name = node.identifier
                        if (name == "gamepad1" || name == "gamepad2") {
                            val anchor = node.sourcePsi ?: return false
                            holder.registerProblem(
                                anchor,
                                "Accessing gamepad in constructor/init; delete the statement",
                                ProblemHighlightType.WARNING,
                                DeleteStatementQuickFix()
                            )
                        }
                        return false
                    }
                })
            }
        }, true)
    }
}
