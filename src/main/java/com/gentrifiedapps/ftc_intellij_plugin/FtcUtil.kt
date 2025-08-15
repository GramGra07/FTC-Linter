package com.gentrifiedapps.ftc_intellij_plugin
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import com.intellij.openapi.module.ModuleUtilCore

object FtcUtil {
    const val OPMODE = "com.qualcomm.robotcore.eventloop.opmode.OpMode"
    const val LINEAR_OPMODE = "com.qualcomm.robotcore.eventloop.opmode.LinearOpMode"
    const val TELEOP = "com.qualcomm.robotcore.eventloop.opmode.TeleOp"
    const val AUTO = "com.qualcomm.robotcore.eventloop.opmode.Autonomous"

    fun findClass(project: com.intellij.openapi.project.Project, fqn: String): PsiClass? =
        JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))

    // Prefer deep inheritance checks (handles indirect inheritance)
    fun isOpMode(cls: PsiClass): Boolean =
        InheritanceUtil.isInheritor(cls, OPMODE)

    fun isLinearOpMode(cls: PsiClass): Boolean =
        InheritanceUtil.isInheritor(cls, LINEAR_OPMODE)

    fun hasOpModeAnnotation(cls: PsiClass): Boolean =
        cls.modifierList?.annotations?.any {
            val qn = it.qualifiedName
            qn == TELEOP || qn == AUTO
        } == true

    // Derive the OpMode display name from @TeleOp/@Autonomous(name = "...") or fallback to class name
    fun opModeName(cls: PsiClass): String? {
        val ann = cls.modifierList?.annotations?.firstOrNull {
            val qn = it.qualifiedName
            qn == TELEOP || qn == AUTO
        } ?: return null
        val value = (ann.findAttributeValue("name") as? PsiLiteralExpression)?.value as? String
        return value ?: cls.name
    }

    fun methodNamed(cls: PsiClass, name: String): PsiMethod? =
        cls.methods.firstOrNull { it.name == name }

    fun problemRange(anchor: PsiElement): PsiElement = anchor

    fun isInTeamCode(element: PsiElement): Boolean {
        val module = ModuleUtilCore.findModuleForPsiElement(element)
        return module?.name?.contains("TeamCode", ignoreCase = true) == true
    }
}
