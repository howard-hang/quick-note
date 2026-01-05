package com.quicknote.plugin.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Utility object for path manipulation
 */
object PathUtils {
    /**
     * Get relative path from project base path to the given file
     *
     * @param project Current project
     * @param file Virtual file to get relative path for
     * @return Relative path string, or absolute path if relativization fails
     */
    fun getRelativePath(project: Project, file: VirtualFile): String {
        val projectBasePath = project.basePath ?: return file.path
        val projectPath = Paths.get(projectBasePath)
        val filePath = Paths.get(file.path)

        return try {
            projectPath.relativize(filePath).toString()
        } catch (e: Exception) {
            // If relativization fails (e.g., paths are on different drives), return absolute path
            file.path
        }
    }

    /**
     * Get absolute path from project base path and relative path
     *
     * @param project Current project
     * @param relativePath Relative path string
     * @return Absolute path string
     */
    fun getAbsolutePath(project: Project, relativePath: String): String {
        val projectBasePath = project.basePath ?: return relativePath
        return Paths.get(projectBasePath, relativePath).toString()
    }

    /**
     * Normalize path separators to forward slashes
     *
     * @param path Path string to normalize
     * @return Normalized path string
     */
    fun normalizePath(path: String): String {
        return path.replace('\\', '/')
    }

    /**
     * Get shortened path for display (truncate middle if too long)
     *
     * @param path Path string
     * @param maxLength Maximum length
     * @return Shortened path string
     */
    fun getShortenedPath(path: String, maxLength: Int = 50): String {
        if (path.length <= maxLength) {
            return path
        }

        val ellipsis = "..."
        val keepLength = (maxLength - ellipsis.length) / 2

        return path.substring(0, keepLength) +
                ellipsis +
                path.substring(path.length - keepLength)
    }

    /**
     * Extract file name from path
     *
     * @param path Path string
     * @return File name
     */
    fun getFileName(path: String): String {
        return Paths.get(path).fileName?.toString() ?: path
    }

    /**
     * Extract file extension from path
     *
     * @param path Path string
     * @return File extension (without dot), or empty string if no extension
     */
    fun getFileExtension(path: String): String {
        val fileName = getFileName(path)
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex > 0 && dotIndex < fileName.length - 1) {
            fileName.substring(dotIndex + 1).lowercase()
        } else {
            ""
        }
    }

    /**
     * Check if path is absolute
     *
     * @param path Path string
     * @return true if path is absolute, false otherwise
     */
    fun isAbsolutePath(path: String): Boolean {
        return Paths.get(path).isAbsolute
    }

    /**
     * Combine path segments safely
     *
     * @param segments Path segments
     * @return Combined path
     */
    fun combinePath(vararg segments: String): Path {
        require(segments.isNotEmpty()) { "At least one path segment required" }
        return Paths.get(segments[0], *segments.sliceArray(1 until segments.size))
    }
}
