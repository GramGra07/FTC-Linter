package com.gentrifiedapps.ftc_intellij_plugin

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

class FtcProjectDetector(private val project: Project) {
    fun isFtcProject(): Boolean {
        val facade = JavaPsiFacade.getInstance(project)
        val scope = GlobalSearchScope.projectScope(project)
        return facade.findClass("com.qualcomm.robotcore.eventloop.opmode.OpMode", scope) != null
    }
}