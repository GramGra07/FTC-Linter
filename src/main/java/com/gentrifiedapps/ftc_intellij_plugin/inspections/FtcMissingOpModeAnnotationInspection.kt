package com.gentrifiedapps.ftc_intellij_plugin.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.gentrifiedapps.ftc_intellij_plugin.utils.FtcUtil

class FtcMissingOpModeAnnotationInspection : LocalInspectionTool() {
    override fun getGroupDisplayName(): String = "FTC Linter"

    private class AddTeleOpAnnotationQuickFix : LocalQuickFix {
        override fun getFamilyName(): String = "Add @TeleOp annotation"
        override fun applyFix(project: Project, descriptor: com.intellij.codeInspection.ProblemDescriptor) {
            val cls = descriptor.psiElement?.parent as? PsiClass ?: return
            val factory = JavaPsiFacade.getElementFactory(project)
            val annText = "@com.qualcomm.robotcore.eventloop.opmode.TeleOp(name=\"${cls.name}\")"
            val annotation = factory.createAnnotationFromText(annText, cls)
            WriteCommandAction.runWriteCommandAction(project) {
                val mods = cls.modifierList
                if (mods != null) mods.addBefore(annotation, mods.firstChild) else cls.addBefore(annotation, cls.firstChild)
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                if (!FtcUtil.isInTeamCode(aClass)) return
                if (!FtcUtil.isOpMode(aClass)) return
                val isPublic = aClass.hasModifierProperty(PsiModifier.PUBLIC)
                if (isPublic && !FtcUtil.hasOpModeAnnotation(aClass)) {
                    holder.registerProblem(
                        aClass.nameIdentifier ?: aClass,
                        "Public OpMode is missing @TeleOp or @Autonomous annotation",
                        ProblemHighlightType.WARNING,
                        AddTeleOpAnnotationQuickFix()
                    )
                }
            }
        }
    }
}