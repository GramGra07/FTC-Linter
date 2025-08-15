package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class FtcThreadSleepInspection : AbstractBaseUastLocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class DeleteBlockingCallQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Delete blocking call"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val el = descriptor.psiElement ?: return
            val stmt = generateSequence(el) { it.parent }.filterIsInstance<com.intellij.psi.PsiStatement>().firstOrNull() ?: return
            stmt.delete()
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return UastVisitorAdapter(object : AbstractUastNonRecursiveVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val anchor = node.methodIdentifier?.sourcePsi ?: node.sourcePsi ?: return false
                if (!FtcUtil.isInTeamCode(anchor)) return false

                val resolved = node.resolve() ?: return false
                val methodName = resolved.name
                val classFqn = resolved.containingClass?.qualifiedName ?: return false

                val isBlockingSleep =
                    (classFqn == "java.lang.Thread" && methodName == "sleep") ||
                    (classFqn == "java.util.concurrent.TimeUnit" && methodName == "sleep") ||
                    (classFqn == "android.os.SystemClock" && methodName == "sleep") ||
                    (classFqn == "java.lang.Object" && methodName == "wait") ||
                    (classFqn == "com.qualcomm.robotcore.eventloop.opmode.LinearOpMode" && methodName == "sleep")

                if (!isBlockingSleep) return false

                val uClass = node.getContainingUClass() ?: return false
                val psiClass = uClass.javaPsi as? PsiClass ?: return false
                if (!FtcUtil.isOpMode(psiClass)) return false

                holder.registerProblem(
                    anchor,
                    "Blocking sleep/wait call in OpMode; delete it",
                    ProblemHighlightType.WARNING,
                    DeleteBlockingCallQuickFix()
                )
                return false
            }
        }, true)
    }
}
