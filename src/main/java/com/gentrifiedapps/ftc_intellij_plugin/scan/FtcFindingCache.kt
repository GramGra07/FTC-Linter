package com.gentrifiedapps.ftc_intellij_plugin.scan

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

@Service(Service.Level.PROJECT)
class FtcFindingCache(private val project: Project) {
    private val findingsByPath = ConcurrentHashMap<String, List<FtcFinding>>()
    private val dirtyPaths = CopyOnWriteArraySet<String>()

    // ✅ Unique tokens for coalescing (objects, not strings)
    private val warmupKey: Any = Any()
    private val batchKeyMap = ConcurrentHashMap<String, Any>()

    fun warmupCoalesceKey(): Any = warmupKey
    fun coalesceKeyFor(paths: Collection<String>): Any {
        val sig = paths.asSequence().sorted().take(64).joinToString("|")
        return batchKeyMap.computeIfAbsent(sig) { Any() }
    }

    fun markDirty(path: String) { dirtyPaths += path }
    fun markDirty(paths: Collection<String>) { dirtyPaths.addAll(paths) }

    fun put(path: String, findings: List<FtcFinding>) {
        if (findings.isEmpty()) findingsByPath.remove(path) else findingsByPath[path] = findings
        dirtyPaths.remove(path)
    }

    fun get(path: String): List<FtcFinding> = findingsByPath[path].orEmpty()
    fun hasFatalIssues(): Boolean = findingsByPath.values.any { it.isNotEmpty() }
    fun snapshotFatalCount(): Int = findingsByPath.values.sumOf { it.size }
    fun dirtyCount(): Int = dirtyPaths.size
    fun dirtyPaths(): Set<String> = HashSet(dirtyPaths)

    companion object { fun getInstance(project: Project) = project.getService(FtcFindingCache::class.java) }
}
