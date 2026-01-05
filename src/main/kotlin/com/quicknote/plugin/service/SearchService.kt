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
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.*
import org.apache.lucene.store.NIOFSDirectory
import java.nio.file.Paths

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
        } catch (e: Throwable) {
            searchAvailable = false
            indexWriter = null
            searcherManager = null
            thisLogger().error("Failed to initialize search index; search disabled", e)
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
    fun search(queryString: String, maxResults: Int = NoteConstants.MAX_SEARCH_RESULTS): List<Note> {
        if (!searchAvailable) {
            return emptyList()
        }
        if (queryString.isBlank()) {
            return emptyList()
        }

        val manager = searcherManager ?: return emptyList()
        val searcher = manager.acquire()

        try {
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
            // Build multi-field query
            val queryParser = MultiFieldQueryParser(
                fields.toTypedArray(),
                analyzer
            )

            val query = try {
                queryParser.parse(queryString)
            } catch (e: Exception) {
                thisLogger().warn("Failed to parse query: $queryString", e)
                // Fallback to simple query
                queryParser.parse(queryString.replace(Regex("[^\\w\\s]"), ""))
            }

            // Execute search
            val topDocs = searcher.search(query, maxResults)

            // Convert results to notes
            val noteService = NoteService.getInstance(project)
            return topDocs.scoreDocs.mapNotNull { scoreDoc ->
                val doc = loadDocument(searcher, scoreDoc.doc)
                val noteId = doc.get(NoteConstants.LUCENE_FIELD_ID)
                noteService.getNote(noteId)
            }.distinctBy { it.id }
        } catch (e: Exception) {
            thisLogger().error("Search failed for query: $queryString", e)
            return emptyList()
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
}
