package com.quicknote.plugin.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.quicknote.plugin.model.LogcatConstants
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Project-level service to manage adb logcat recording.
 */
@Service(Service.Level.PROJECT)
class LogcatRecorderService(private val project: Project) : Disposable {
    private val logger = thisLogger()
    private val stateLock = Any()

    private var logcatProcess: Process? = null
    private var currentLogFile: Path? = null

    companion object {
        fun getInstance(project: Project): LogcatRecorderService {
            return project.getService(LogcatRecorderService::class.java)
        }
    }

    fun isRecording(): Boolean {
        return logcatProcess?.isAlive == true
    }

    fun getCurrentLogFile(): Path? {
        return currentLogFile
    }

    fun startRecording(logDir: Path): Result<Path> {
        synchronized(stateLock) {
            if (isRecording()) {
                return Result.failure(IllegalStateException("Logcat recording is already running."))
            }

            try {
                Files.createDirectories(logDir)
            } catch (e: Exception) {
                return Result.failure(IllegalStateException("Failed to create log directory: ${e.message}", e))
            }

            val projectName = sanitizeSegment(project.name.ifBlank { "project" })
            val branchName = sanitizeSegment(resolveGitBranchName())
            val timestamp = SimpleDateFormat(
                LogcatConstants.LOG_TIMESTAMP_FORMAT,
                Locale.US
            ).format(Date())
            val fileName = listOf(projectName, branchName, timestamp)
                .filter { it.isNotBlank() }
                .joinToString("-") + LogcatConstants.LOG_FILE_EXTENSION
            val logFile = logDir.resolve(fileName)

            return try {
                val process = ProcessBuilder("adb", "logcat", "-v", "time")
                    .redirectErrorStream(true)
                    .redirectOutput(logFile.toFile())
                    .start()
                logcatProcess = process
                currentLogFile = logFile
                logger.info("Logcat recording started: ${logFile.toAbsolutePath()}")
                Result.success(logFile)
            } catch (e: Exception) {
                logger.warn("Failed to start logcat recording", e)
                Result.failure(IllegalStateException("Failed to start logcat: ${e.message}", e))
            }
        }
    }

    fun stopRecording(): Result<Path?> {
        synchronized(stateLock) {
            val process = logcatProcess
                ?: return Result.failure(IllegalStateException("Logcat recording is not running."))

            try {
                process.destroy()
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(2, TimeUnit.SECONDS)
                }
            } catch (e: Exception) {
                logger.warn("Failed to stop logcat process gracefully", e)
            } finally {
                logcatProcess = null
            }

            logger.info("Logcat recording stopped: ${currentLogFile?.toAbsolutePath()}")
            return Result.success(currentLogFile)
        }
    }

    override fun dispose() {
        if (isRecording()) {
            stopRecording()
        }
    }

    private fun resolveGitBranchName(): String {
        val basePath = project.basePath?.let { Paths.get(it) } ?: return LogcatConstants.DEFAULT_BRANCH_NAME
        val baseDir = basePath.toFile()
        if (!baseDir.exists()) {
            return LogcatConstants.DEFAULT_BRANCH_NAME
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
                return LogcatConstants.DEFAULT_BRANCH_NAME
            }
            val branch = output.lines().firstOrNull().orEmpty().trim()
            if (process.exitValue() == 0 && branch.isNotBlank() && branch != "HEAD") {
                branch
            } else {
                LogcatConstants.DEFAULT_BRANCH_NAME
            }
        } catch (e: Exception) {
            logger.debug("Unable to resolve git branch", e)
            LogcatConstants.DEFAULT_BRANCH_NAME
        }
    }

    private fun sanitizeSegment(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return "unknown"
        }
        val sanitized = trimmed
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim('_', '-')
        return if (sanitized.isBlank()) "unknown" else sanitized
    }
}
