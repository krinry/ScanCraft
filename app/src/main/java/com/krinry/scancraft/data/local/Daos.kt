package com.krinry.scancraft.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.Page

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY modifiedTime DESC")
    fun getAllDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents ORDER BY modifiedTime DESC")
    suspend fun getAllDocumentsList(): List<Document>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY modifiedTime DESC")
    fun getDocumentsInFolder(folderId: Long): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE folderId = :folderId ORDER BY modifiedTime DESC")
    suspend fun getDocumentsListInFolder(folderId: Long): List<Document>

    @Query("SELECT * FROM documents WHERE folderId = -1 ORDER BY modifiedTime DESC")
    fun getRootDocuments(): Flow<List<Document>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): Document?

    @Query("""
        SELECT DISTINCT d.* FROM documents d 
        LEFT JOIN pages p ON d.id = p.documentId 
        WHERE d.title LIKE '%' || :query || '%' OR (p.ocrText IS NOT NULL AND p.ocrText LIKE '%' || :query || '%') 
        ORDER BY d.modifiedTime DESC
    """)
    fun searchDocuments(query: String): Flow<List<Document>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: Document): Long

    @Update
    suspend fun updateDocument(document: Document)

    @Delete
    suspend fun deleteDocument(document: Document)

    @Query("DELETE FROM documents WHERE id IN (:docIds)")
    suspend fun batchDeleteDocuments(docIds: List<Long>)

    @Query("UPDATE documents SET folderId = :folderId WHERE id = :docId")
    suspend fun updateDocumentFolder(docId: Long, folderId: Long)

    @Query("UPDATE documents SET folderId = :folderId WHERE id IN (:docIds)")
    suspend fun batchUpdateFolder(docIds: List<Long>, folderId: Long)

    @Query("UPDATE documents SET folderId = -1 WHERE folderId = :folderId")
    suspend fun unlinkDocumentsFromFolder(folderId: Long)

    @Query("SELECT COUNT(*) FROM documents WHERE folderId = :folderId")
    fun getFolderDocCountFlow(folderId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE folderId = :folderId")
    suspend fun getFolderDocCount(folderId: Long): Int

    @Query("UPDATE documents SET pageCount = :count, modifiedTime = :time WHERE id = :docId")
    suspend fun updatePageCount(docId: Long, count: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET title = :title, modifiedTime = :time WHERE id = :id")
    suspend fun updateDocumentTitle(id: Long, title: String, time: Long = System.currentTimeMillis())
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, createdTime DESC")
    fun getAllFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY orderIndex ASC, createdTime DESC")
    suspend fun getAllFoldersList(): List<Folder>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: Long): Folder?

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): Folder?

    @Query("SELECT * FROM folders WHERE parentFolderId = -1 ORDER BY orderIndex ASC, createdTime DESC")
    fun getRootFolders(): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentFolderId ORDER BY orderIndex ASC, createdTime DESC")
    fun getSubfolders(parentFolderId: Long): Flow<List<Folder>>

    @Query("SELECT * FROM folders WHERE parentFolderId = :parentFolderId ORDER BY orderIndex ASC, createdTime DESC")
    suspend fun getSubfoldersList(parentFolderId: Long): List<Folder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: Folder): Long

    @Update
    suspend fun updateFolder(folder: Folder)

    @Delete
    suspend fun deleteFolder(folder: Folder)

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: Long)

    @Query("DELETE FROM folders WHERE path LIKE :pathPrefix || '%'")
    suspend fun deleteFoldersByPathPrefix(pathPrefix: String)

    @Query("""
        UPDATE folders 
        SET path = :newPrefix || SUBSTR(path, LENGTH(:oldPrefix) + 1)
        WHERE path LIKE :oldPrefix || '%'
    """)
    suspend fun updateFolderPathPrefix(oldPrefix: String, newPrefix: String)

    @Query("""
        WITH RECURSIVE breadcrumb(id, name, createdTime, color, orderIndex, parentFolderId, path, depth) AS (
            SELECT id, name, createdTime, color, orderIndex, parentFolderId, path, 0 AS depth
            FROM folders
            WHERE id = :folderId
            UNION ALL
            SELECT f.id, f.name, f.createdTime, f.color, f.orderIndex, f.parentFolderId, f.path, b.depth + 1
            FROM folders f
            JOIN breadcrumb b ON f.id = b.parentFolderId
        )
        SELECT id, name, createdTime, color, orderIndex, parentFolderId, path
        FROM breadcrumb
        ORDER BY depth DESC
    """)
    suspend fun getBreadcrumbs(folderId: Long): List<Folder>
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    fun getPagesForDocument(docId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE documentId = :docId ORDER BY pageIndex ASC")
    suspend fun getPagesListForDocument(docId: Long): List<Page>

    @Query("SELECT * FROM pages WHERE id = :pageId")
    suspend fun getPageById(pageId: Long): Page?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: Page): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPages(pages: List<Page>): List<Long>

    @Update
    suspend fun updatePage(page: Page)

    @Query("UPDATE pages SET annotationsJson = :json WHERE id = :pageId")
    suspend fun updatePageAnnotations(pageId: Long, json: String)

    @Delete
    suspend fun deletePage(page: Page)

    @Query("DELETE FROM pages WHERE documentId = :docId")
    suspend fun deletePagesForDocument(docId: Long)
}
