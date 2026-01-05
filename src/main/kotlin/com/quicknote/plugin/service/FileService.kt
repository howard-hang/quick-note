package com.quicknote.plugin.service

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.quicknote.plugin.util.PathUtils

/**
 * Application-level service for file operations
 */
@Service(Service.Level.APP)
class FileService {

    companion object {
        fun getInstance(): FileService {
            return checkNotNull(
                ApplicationManager.getApplication().getService(FileService::class.java)
            ) { "FileService not available" }
        }
    }

    /**
     * Get relative path from project base to file
     */
    fun getRelativePath(project: Project, file: VirtualFile): String {
        return PathUtils.getRelativePath(project, file)
    }

    /**
     * Get absolute path from project base and relative path
     */
    fun getAbsolutePath(project: Project, relativePath: String): String {
        return PathUtils.getAbsolutePath(project, relativePath)
    }

    /**
     * Detect programming language from file extension
     */
    fun detectLanguage(file: VirtualFile): String {
        return when (file.extension?.lowercase()) {
            "java" -> "java"
            "kt", "kts" -> "kotlin"
            "xml" -> "xml"
            "json" -> "json"
            "gradle" -> "gradle"
            "properties" -> "properties"
            "md", "markdown" -> "markdown"
            "py" -> "python"
            "js" -> "javascript"
            "jsx" -> "javascript"
            "ts" -> "typescript"
            "tsx" -> "typescript"
            "html", "htm" -> "html"
            "css" -> "css"
            "scss", "sass" -> "scss"
            "sql" -> "sql"
            "sh", "bash" -> "bash"
            "yml", "yaml" -> "yaml"
            "cpp", "cc", "cxx" -> "cpp"
            "c" -> "c"
            "h", "hpp" -> "cpp"
            "cs" -> "csharp"
            "go" -> "go"
            "rs" -> "rust"
            "rb" -> "ruby"
            "php" -> "php"
            "swift" -> "swift"
            "r" -> "r"
            "m" -> "objective-c"
            "scala" -> "scala"
            else -> "text"
        }
    }

    /**
     * Get file name without extension
     */
    fun getFileNameWithoutExtension(file: VirtualFile): String {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) {
            name.substring(0, dotIndex)
        } else {
            name
        }
    }

    /**
     * Check if file is a source code file
     */
    fun isSourceFile(file: VirtualFile): Boolean {
        val language = detectLanguage(file)
        return language != "text" && language != "markdown"
    }
}
