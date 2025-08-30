package com.gentrifiedapps.ftc_intellij_plugin.inspections;

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

class FtcDualTeamCodeIdentifier : LocalInspectionTool() {

    override fun getGroupDisplayName(): String = "FTC Linter"

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean
    ): Array<ProblemDescriptor>? {
        val vFile = file.virtualFile ?: return null
        val project = file.project

        val thisRoot = containingTeamCodeRoot(vFile) ?: return null
        val rootsByName = allTeamCodeRoots(project)

        val upper = rootsByName["TeamCode"].orEmpty()
        val lower = rootsByName["teamcode"].orEmpty()

        // Allowed: at most one `TeamCode` and one `teamcode`.
        if (upper.size <= 1 && lower.size <= 2) return null

        val isUpper = thisRoot.name == "TeamCode"
        val variantRoots = if (isUpper) upper else lower
        if (variantRoots.size <= 1) return null

        // Flag only non-primary duplicates of the current variant.
        val primary = variantRoots.minByOrNull { it.path.lowercase() } ?: return null
        if (thisRoot == primary) return null

        // Report once per offending folder.
        if (!isRepresentativeFileForRoot(file, thisRoot)) return null

        val message = "Only one 'TeamCode' and one 'teamcode' root are allowed. Multiple '${thisRoot.name}' roots detected."
        val descriptor = manager.createProblemDescriptor(
            file,
            message,
            false,
            emptyArray(),
            ProblemHighlightType.ERROR
        )
        return arrayOf(descriptor)
    }

    private fun containingTeamCodeRoot(file: VirtualFile): VirtualFile? {
        var cur: VirtualFile? = if (file.isDirectory) file else file.parent
        while (cur != null) {
            if (cur.isDirectory && (cur.name == "TeamCode" || cur.name == "teamcode")) return cur
            cur = cur.parent
        }
        return null
    }

    private fun allTeamCodeRoots(project: Project): Map<String, List<VirtualFile>> {
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val teamCode = mutableListOf<VirtualFile>()
            val teamcode = mutableListOf<VirtualFile>()
            val index = ProjectFileIndex.getInstance(project)
            index.iterateContent { vf ->
                if (vf.isDirectory) {
                    when (vf.name) {
                        "TeamCode" -> teamCode.add(vf)
                        "teamcode" -> teamcode.add(vf)
                    }
                }
                true
            }
            val map = mapOf("TeamCode" to teamCode.toList(), "teamcode" to teamcode.toList())
            CachedValueProvider.Result.create(map, ProjectRootManager.getInstance(project))
        }
    }

    private fun isRepresentativeFileForRoot(file: PsiFile, root: VirtualFile): Boolean {
        val vFile = file.virtualFile ?: return false
        if (vFile.parent != root) return false
        val firstChildFileName = root.children
            .asSequence()
            .filter { !it.isDirectory }
            .map { it.name }
            .minOrNull()
        return firstChildFileName != null && vFile.name == firstChildFileName
    }
}