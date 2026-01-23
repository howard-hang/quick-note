package com.quicknote.plugin.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.quicknote.plugin.model.Note
import com.quicknote.plugin.model.NoteConstants
import com.quicknote.plugin.settings.QuickNoteSettings
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level service for full-text search using Apache Lucene
 */
@Service(Service.Level.PROJECT)
class SearchService(private val project: Project) : Disposable {

    private var indexWriter: IndexWriter? = null
    private var searcherManager: SearcherManager? = null
    private val analyzer = StandardAnalyzer()
    @Volatile
    private var searchAvailable: Boolean = true
    @Volatile
    private var queryParserAvailable: Boolean = true
    private val searchEnvironmentLogged = AtomicBoolean(false)

    companion object {
        fun getInstance(project: Project): SearchService {
            return checkNotNull(project.getService(SearchService::class.java)) {
                "SearchService not available"
            }
        }
    }

    init {
        initializeIndex()
    }

    /**
     * Initialize Lucene index
     */
    private fun initializeIndex() {
        try {
            val indexPath = getIndexPath()
            // Avoid FSDirectory.open (MMapDirectory) to prevent MR-JAR class loading issues.
            val directory = NIOFSDirectory(indexPath)
            val config = IndexWriterConfig(analyzer).apply {
                openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
            }

            indexWriter = IndexWriter(directory, config)
            searcherManager = SearcherManager(indexWriter, null)

            thisLogger().info("Search index initialized at: $indexPath")
            logSearchEnvironmentOnce()
        } catch (e: Throwable) {
            searchAvailable = false
            indexWriter = null
            searcherManager = null
            thisLogger().error("Failed to initialize search index; search disabled", e)
            logSearchEnvironmentOnce()
        }
    }

    fun isSearchAvailable(): Boolean = searchAvailable

    /**
     * Recreate the index components, e.g. after changing storage path.
     */
    @Synchronized
    fun resetIndex() {
        try {
            searcherManager?.close()
            indexWriter?.close()
        } catch (e: Exception) {
            thisLogger().warn("Failed to close existing search index", e)
        } finally {
            searcherManager = null
            indexWriter = null
        }
        searchAvailable = true
        initializeIndex()
    }

    /**
     * Get index storage path
     */
    private fun getIndexPath() = Paths.get(
        QuickNoteSettings.getInstance().storageBasePath,
        NoteConstants.INDEX_DIR_NAME,
        project.name
    )

    /**
     * Index a single note
     *
     * @param note Note to index
     */
    fun indexNote(note: Note) {
        if (!searchAvailable) {
            return
        }
        val writer = indexWriter ?: return

        try {
            val searchableContent = buildSearchableContent(note)
            val doc = Document().apply {
                add(StringField(NoteConstants.LUCENE_FIELD_ID, note.id, Field.Store.YES))
                add(TextField(NoteConstants.LUCENE_FIELD_TITLE, note.title, Field.Store.YES))
                add(TextField(NoteConstants.LUCENE_FIELD_CONTENT, searchableContent, Field.Store.YES))
                add(TextField(NoteConstants.LUCENE_FIELD_TAGS, note.tags.joinToString(" "), Field.Store.YES))
                add(TextField(NoteConstants.LUCENE_FIELD_FILE_PATH, note.filePath, Field.Store.NO))
                add(StoredField(NoteConstants.LUCENE_FIELD_FILE_PATH, note.filePath))
                add(StringField(NoteConstants.LUCENE_FIELD_FILE_PATH_EXACT, note.filePath, Field.Store.NO))
                note.gitBranch?.takeIf { it.isNotBlank() }?.let { branch ->
                    add(StringField(NoteConstants.LUCENE_FIELD_GIT_BRANCH, branch, Field.Store.YES))
                }
            }

            writer.updateDocument(Term(NoteConstants.LUCENE_FIELD_ID, note.id), doc)
            writer.commit()
            searcherManager?.maybeRefresh()

            thisLogger().debug("Indexed note: ${note.id}")
        } catch (e: Exception) {
            thisLogger().error("Failed to index note: ${note.id}", e)
        }
    }

    /**
     * Update note in index
     *
     * @param note Note to update
     */
    fun updateIndex(note: Note) {
        if (!searchAvailable) {
            return
        }
        try {
            indexNote(note)
            thisLogger().debug("Updated index for note: ${note.id}")
        } catch (e: Exception) {
            thisLogger().error("Failed to update index for note: ${note.id}", e)
        }
    }

    /**
     * Remove note from index
     *
     * @param noteId ID of note to remove
     */
    fun removeFromIndex(noteId: String) {
        if (!searchAvailable) {
            return
        }
        val writer = indexWriter ?: return

        try {
            writer.deleteDocuments(Term(NoteConstants.LUCENE_FIELD_ID, noteId))
            writer.commit()
            searcherManager?.maybeRefresh()

            thisLogger().debug("Removed note from index: $noteId")
        } catch (e: Exception) {
            thisLogger().error("Failed to remove note from index: $noteId", e)
        }
    }

    private fun buildSearchableContent(note: Note): String {
        val snippet = note.metadata.snippet?.trim().orEmpty()
        return if (snippet.isNotBlank()) {
            listOf(note.content, snippet).joinToString("\n").trim()
        } else {
            note.content
        }
    }

    /**
     * Search notes by query string
     *
     * @param queryString Search query
     * @param maxResults Maximum number of results to return
     * @return List of matching notes
     */
    fun search(
        queryString: String,
        maxResults: Int = NoteConstants.MAX_SEARCH_RESULTS,
        branchFilter: String? = null,
        filePathFilter: String? = null
    ): List<Note> {
        if (queryString.isBlank()) {
            return emptyList()
        }

        if (!searchAvailable) {
            thisLogger().warn("Search unavailable; using fallback search for query: '$queryString'")
            return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)
        }

        logSearchEnvironmentOnce()

        val manager = searcherManager
            ?: run {
                thisLogger().warn("Search manager not initialized; using fallback search for query: '$queryString'")
                return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)
            }

        val settings = QuickNoteSettings.getInstance()
        val fields = mutableListOf(
            NoteConstants.LUCENE_FIELD_TITLE,
            NoteConstants.LUCENE_FIELD_CONTENT
        )
        if (settings.searchInTags) {
            fields.add(NoteConstants.LUCENE_FIELD_TAGS)
        }
        if (settings.searchInFilePaths) {
            fields.add(NoteConstants.LUCENE_FIELD_FILE_PATH)
        }
        if (!queryParserAvailable) {
            thisLogger().warn("Lucene query parser unavailable; using fallback search for query: '$queryString'")
            return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)
        }

        val queryParser = try {
            MultiFieldQueryParser(
                fields.toTypedArray(),
                analyzer
            )
        } catch (e: Throwable) {
            if (e is LinkageError) {
                queryParserAvailable = false
            }
            thisLogger().error("Failed to initialize Lucene query parser; using fallback search", e)
            return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)
        }

        val parsedQuery = parseQuery(queryParser, queryString)
            ?: return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)

        val filteredQuery = applyFilters(parsedQuery, branchFilter, filePathFilter)
        val searcher = manager.acquire()

        try {
            // Execute search
            val topDocs = searcher.search(filteredQuery, maxResults)

            // Convert results to notes
            val noteService = NoteService.getInstance(project)
            return topDocs.scoreDocs.mapNotNull { scoreDoc ->
                val doc = loadDocument(searcher, scoreDoc.doc)
                val noteId = doc.get(NoteConstants.LUCENE_FIELD_ID)
                noteService.getNote(noteId)
            }.distinctBy { it.id }
        } catch (e: Throwable) {
            if (e is LinkageError) {
                searchAvailable = false
                thisLogger().error(
                    "Search failed due to incompatible Lucene classes; disabling full-text search",
                    e
                )
            } else {
                thisLogger().error("Search failed for query: $queryString", e)
            }
            return fallbackSearch(queryString, maxResults, branchFilter, filePathFilter)
        } finally {
            manager.release(searcher)
        }
    }

    /**
     * Search notes by tag
     *
     * @param tag Tag to search for
     * @return List of notes with the tag
     */
    fun searchByTag(tag: String): List<Note> {
        if (!searchAvailable) {
            return emptyList()
        }
        if (tag.isBlank()) {
            return emptyList()
        }

        val manager = searcherManager ?: return emptyList()
        val searcher = manager.acquire()

        try {
            val query = TermQuery(Term(NoteConstants.LUCENE_FIELD_TAGS, tag))
            val topDocs = searcher.search(query, NoteConstants.MAX_SEARCH_RESULTS)

            val noteService = NoteService.getInstance(project)
            return topDocs.scoreDocs.mapNotNull { scoreDoc ->
                val doc = loadDocument(searcher, scoreDoc.doc)
                val noteId = doc.get(NoteConstants.LUCENE_FIELD_ID)
                noteService.getNote(noteId)
            }.distinctBy { it.id }
        } catch (e: Exception) {
            thisLogger().error("Tag search failed for: $tag", e)
            return emptyList()
        } finally {
            manager.release(searcher)
        }
    }

    /**
     * Rebuild entire index from scratch
     * Useful for fixing corrupted index or after settings change
     */
    fun rebuildIndex() {
        if (!searchAvailable) {
            return
        }
        val writer = indexWriter ?: return

        try {
            thisLogger().info("Rebuilding search index...")

            // Delete all documents
            writer.deleteAll()

            // Re-index all notes
            val noteService = NoteService.getInstance(project)
            val allNotes = noteService.getAllNotes()

            allNotes.forEach { note ->
                indexNote(note)
            }

            thisLogger().info("Index rebuilt successfully. Indexed ${allNotes.size} notes.")
        } catch (e: Exception) {
            thisLogger().error("Failed to rebuild index", e)
        }
    }

    /**
     * Get index statistics
     *
     * @return Map of statistics (numDocs, etc.)
     */
    fun getIndexStats(): Map<String, Int> {
        if (!searchAvailable) {
            return emptyMap()
        }
        val writer = indexWriter ?: return emptyMap()

        return mapOf(
            "numDocs" to writer.docStats.numDocs,
            "maxDoc" to writer.docStats.maxDoc
        )
    }

    override fun dispose() {
        try {
            searcherManager?.close()
            indexWriter?.close()
            thisLogger().info("Search service disposed")
        } catch (e: Exception) {
            thisLogger().error("Error disposing search service", e)
        }
    }

    // Use reflection to avoid linking against Lucene APIs missing in older IDE builds.
    private fun loadDocument(searcher: IndexSearcher, docId: Int): Document {
        val idFields = setOf(NoteConstants.LUCENE_FIELD_ID)
        val searcherClass = searcher.javaClass

        try {
            val storedFieldsMethod = searcherClass.getMethod("storedFields")
            val storedFields = storedFieldsMethod.invoke(searcher)
            if (storedFields != null) {
                val documentMethod = storedFields.javaClass.getMethod(
                    "document",
                    Int::class.javaPrimitiveType,
                    Set::class.java
                )
                val doc = documentMethod.invoke(storedFields, docId, idFields) as? Document
                if (doc != null) {
                    return doc
                }
            }
        } catch (e: NoSuchMethodException) {
            // Ignore; older Lucene versions do not expose storedFields().
        }

        try {
            val docWithFieldsMethod = searcherClass.getMethod(
                "doc",
                Int::class.javaPrimitiveType,
                Set::class.java
            )
            val doc = docWithFieldsMethod.invoke(searcher, docId, idFields) as? Document
            if (doc != null) {
                return doc
            }
        } catch (e: NoSuchMethodException) {
            // Ignore; fallback to doc(int).
        }

        val docMethod = searcherClass.getMethod("doc", Int::class.javaPrimitiveType)
        return docMethod.invoke(searcher, docId) as Document
    }

    private fun parseQuery(queryParser: MultiFieldQueryParser, queryString: String): Query? {
        return try {
            queryParser.parse(queryString)
        } catch (e: Throwable) {
            if (e is LinkageError) {
                queryParserAvailable = false
            }
            thisLogger().warn("Failed to parse query: $queryString", e)
            val escaped = QueryParser.escape(queryString).trim()
            if (escaped.isBlank()) {
                null
            } else {
                try {
                    queryParser.parse(escaped)
                } catch (fallbackError: Throwable) {
                    if (fallbackError is LinkageError) {
                        queryParserAvailable = false
                    }
                    thisLogger().warn("Failed to parse escaped query: $escaped", fallbackError)
                    null
                }
            }
        }
    }

    private fun applyFilters(baseQuery: Query, branchFilter: String?, filePathFilter: String?): Query {
        val normalizedBranch = branchFilter?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPath = filePathFilter?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedBranch == null && normalizedPath == null) {
            return baseQuery
        }

        val builder = BooleanQuery.Builder()
        builder.add(baseQuery, BooleanClause.Occur.MUST)
        normalizedBranch?.let {
            builder.add(TermQuery(Term(NoteConstants.LUCENE_FIELD_GIT_BRANCH, it)), BooleanClause.Occur.FILTER)
        }
        normalizedPath?.let {
            builder.add(TermQuery(Term(NoteConstants.LUCENE_FIELD_FILE_PATH_EXACT, it)), BooleanClause.Occur.FILTER)
        }
        return builder.build()
    }

    private fun fallbackSearch(
        queryString: String,
        maxResults: Int,
        branchFilter: String?,
        filePathFilter: String?
    ): List<Note> {
        if (queryString.isBlank()) {
            return emptyList()
        }
        val tokens = queryString.lowercase()
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) {
            return emptyList()
        }
        val settings = QuickNoteSettings.getInstance()
        val normalizedBranch = branchFilter?.trim()?.takeIf { it.isNotBlank() }
        val normalizedPath = filePathFilter?.trim()?.takeIf { it.isNotBlank() }

        val noteService = NoteService.getInstance(project)
        return noteService.getAllNotes()
            .asSequence()
            .filter { note ->
                if (normalizedBranch != null && note.gitBranch != normalizedBranch) {
                    return@filter false
                }
                if (normalizedPath != null && note.filePath != normalizedPath) {
                    return@filter false
                }
                val haystack = buildSearchableText(note, settings)
                tokens.any { haystack.contains(it) }
            }
            .take(maxResults)
            .toList()
    }

    private fun buildSearchableText(note: Note, settings: QuickNoteSettings): String {
        val builder = StringBuilder()
        builder.append(note.title).append('\n')
        builder.append(note.content).append('\n')
        note.metadata.snippet?.let { builder.append(it).append('\n') }
        if (settings.searchInTags) {
            builder.append(note.tags.joinToString(" ")).append('\n')
        }
        if (settings.searchInFilePaths) {
            builder.append(note.filePath).append('\n')
        }
        return builder.toString().lowercase()
    }

    private fun logSearchEnvironmentOnce() {
        if (!searchEnvironmentLogged.compareAndSet(false, true)) {
            return
        }
        val indexPath = runCatching { getIndexPath().toString() }.getOrNull() ?: "unknown"
        val storageBasePath = runCatching { QuickNoteSettings.getInstance().storageBasePath }
            .getOrNull()
            ?: "unknown"
        val luceneVersion = runCatching { org.apache.lucene.util.Version.LATEST.toString() }
            .getOrNull()
            ?: "unknown"
        val coreLocation = classLocation(IndexWriter::class.java)
        val analyzerLocation = classLocation(StandardAnalyzer::class.java)
        val queryParserLocation = classLocation("org.apache.lucene.queryparser.classic.MultiFieldQueryParser")

        thisLogger().info(
            "Search environment: available=$searchAvailable, indexPath=$indexPath, " +
                "storageBasePath=$storageBasePath, luceneVersion=$luceneVersion, " +
                "coreJar=$coreLocation, analyzerJar=$analyzerLocation, queryParserJar=$queryParserLocation"
        )
    }

    private fun classLocation(clazz: Class<*>): String {
        return try {
            clazz.protectionDomain?.codeSource?.location?.toString() ?: "unknown"
        } catch (e: Throwable) {
            "unknown"
        }
    }

    private fun classLocation(className: String): String {
        return try {
            classLocation(Class.forName(className))
        } catch (e: Throwable) {
            "missing"
        }
    }
}
