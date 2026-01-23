package com.quicknote.plugin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.quicknote.plugin.model.NoteConstants
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

/**
 * Project-level service for resolving the current Git branch.
 */
@Service(Service.Level.PROJECT)
class GitBranchService(private val project: Project) {
    private val logger = thisLogger()
    @Volatile
    private var cachedBranch: String? = null
    @Volatile
    private var lastResolvedAt: Long = 0L

    companion object {
        private const val CACHE_TTL_MS = 2000L

        fun getInstance(project: Project): GitBranchService {
            return project.getService(GitBranchService::class.java)
        }
    }

    fun getCurrentBranch(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && now - lastResolvedAt < CACHE_TTL_MS) {
            return cachedBranch
        }
        val resolved = resolveGitBranch()
        cachedBranch = resolved
        lastResolvedAt = now
        return resolved
    }

    fun getCurrentBranchOrDefault(forceRefresh: Boolean = false): String {
        return getCurrentBranch(forceRefresh) ?: NoteConstants.DEFAULT_GIT_BRANCH
    }

    fun invalidateCache() {
        cachedBranch = null
        lastResolvedAt = 0L
    }

    private fun resolveGitBranch(): String? {
        val basePath = project.basePath ?: return null
        val baseDir = Paths.get(basePath).toFile()
        if (!baseDir.exists()) {
            return null
        }

        return try {
            val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(baseDir)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText().trim()
            val completed = process.waitFor(2, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return null
            }
            val branch = output.lines().firstOrNull().orEmpty().trim()
            if (process.exitValue() == 0 && branch.isNotBlank() && branch != "HEAD") {
                branch
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug("Unable to resolve git branch", e)
            null
        }
    }
}
