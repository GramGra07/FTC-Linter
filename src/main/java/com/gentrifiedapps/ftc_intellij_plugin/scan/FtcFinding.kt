package com.gentrifiedapps.ftc_intellij_plugin.scan

import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer

/** Immutable, PSI-safe finding (no raw PSI outside read actions). */
data class FtcFinding(
    val toolShortName: String,
    val description: String,
    val filePath: String,
    val pointer: SmartPsiElementPointer<PsiElement>? = null
)