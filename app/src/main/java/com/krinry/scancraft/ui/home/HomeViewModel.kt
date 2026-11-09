package com.krinry.scancraft.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.krinry.scancraft.ScanCraftApp
import com.krinry.scancraft.data.model.Document
import com.krinry.scancraft.data.model.Folder
import com.krinry.scancraft.data.model.FolderWithCount
import com.krinry.scancraft.data.repository.ScannerRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DocSortOrder(val title: String) {
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    NAME_ASC("Name (A to Z)"),
    PAGE_COUNT_DESC("Pages (Most first)")
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = (application as ScanCraftApp).database
    private val repository = ScannerRepository(application, db.documentDao(), db.pageDao(), db.folderDao())

    private val _rawDocuments = MutableStateFlow<List<Document>>(emptyList())
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    private val _foldersWithCount = MutableStateFlow<List<FolderWithCount>>(emptyList())
    val foldersWithCount: StateFlow<List<FolderWithCount>> = _foldersWithCount

    private val _allFoldersWithCount = MutableStateFlow<List<FolderWithCount>>(emptyList())
    val allFoldersWithCount: StateFlow<List<FolderWithCount>> = _allFoldersWithCount

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = MutableStateFlow(DocSortOrder.DATE_DESC)
    val sortOrder: StateFlow<DocSortOrder> = _sortOrder

    private val _isSelectMode = MutableStateFlow(false)
    val isSelectMode: StateFlow<Boolean> = _isSelectMode

    private val _selectedDocIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedDocIds: StateFlow<Set<Long>> = _selectedDocIds

    private val _isGridView = MutableStateFlow(false)
    val isGridView: StateFlow<Boolean> = _isGridView

    private val _documents = MutableStateFlow<List<Document>>(emptyList())
    val documents: StateFlow<List<Document>> = _documents

    init {
        loadData()
    }

    private fun loadData() {
        // Collect all folders
        viewModelScope.launch {
            repository.getAllFolders().collectLatest { folderList ->
                _folders.value = folderList
                refreshFolderCounts(folderList, _rawDocuments.value)
            }
        }

        // Collect all documents
        viewModelScope.launch {
            repository.getAllDocuments().collectLatest { docList ->
                _rawDocuments.value = docList
                refreshFolderCounts(_folders.value, docList)
                applyFilterAndSort()
            }
        }
    }

    private fun refreshFolderCounts(folders: List<Folder>, docs: List<Document>) {
        val countMap = docs.filter { it.folderId > 0 }.groupBy { it.folderId }
        val allList = folders.map { folder ->
            FolderWithCount(
                folder = folder,
                docCount = countMap[folder.id]?.size ?: 0
            )
        }
        _allFoldersWithCount.value = allList

        val currentParentId = _selectedFolder.value?.id ?: -1L
        _foldersWithCount.value = allList.filter { it.folder.parentFolderId == currentParentId }
    }

    private fun applyFilterAndSort() {
        val folder = _selectedFolder.value
        val query = _searchQuery.value.trim().lowercase(Locale.getDefault())
        val sort = _sortOrder.value

        var filtered = _rawDocuments.value

        // Filter by folder if selected
        if (folder != null) {
            filtered = filtered.filter { it.folderId == folder.id }
        } else {
            // When at root level and not searching, show only root documents (folderId <= 0 or -1)
            if (query.isBlank()) {
                filtered = filtered.filter { it.folderId <= 0L }
            }
        }

        // Filter by search query
        if (query.isNotBlank()) {
            filtered = filtered.filter { it.title.lowercase(Locale.getDefault()).contains(query) }
        }

        // Apply sort
        filtered = when (sort) {
            DocSortOrder.DATE_DESC -> filtered.sortedByDescending { it.modifiedTime }
            DocSortOrder.DATE_ASC -> filtered.sortedBy { it.modifiedTime }
            DocSortOrder.NAME_ASC -> filtered.sortedBy { it.title.lowercase(Locale.getDefault()) }
            DocSortOrder.PAGE_COUNT_DESC -> filtered.sortedByDescending { it.pageCount }
        }

        _documents.value = filtered
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
        refreshFolderCounts(_folders.value, _rawDocuments.value)
        applyFilterAndSort()
    }

    fun navigateUp() {
        val current = _selectedFolder.value ?: return
        if (current.parentFolderId > 0) {
            viewModelScope.launch {
                val parent = repository.getFolderById(current.parentFolderId)
                selectFolder(parent)
            }
        } else {
            selectFolder(null)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applyFilterAndSort()
    }

    fun setSortOrder(order: DocSortOrder) {
        _sortOrder.value = order
        applyFilterAndSort()
    }

    fun toggleGridView() {
        _isGridView.value = !_isGridView.value
    }

    fun toggleSelectMode() {
        _isSelectMode.value = !_isSelectMode.value
        if (!_isSelectMode.value) {
            _selectedDocIds.value = emptySet()
        }
    }

    fun toggleSelectDoc(id: Long) {
        val current = _selectedDocIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedDocIds.value = current
        if (current.isNotEmpty() && !_isSelectMode.value) {
            _isSelectMode.value = true
        }
    }

    fun selectAllDocs() {
        _selectedDocIds.value = _documents.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
        _isSelectMode.value = false
    }

    fun batchDeleteSelected() {
        val ids = _selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchDeleteDocuments(ids)
            clearSelection()
        }
    }

    fun batchMoveSelected(targetFolderId: Long, onComplete: (() -> Unit)? = null) {
        val ids = _selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchMoveToFolder(ids, targetFolderId)
            clearSelection()
            onComplete?.invoke()
        }
    }

    fun batchCopySelected(targetFolderId: Long, onComplete: (() -> Unit)? = null) {
        val ids = _selectedDocIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.batchCopyDocumentsToFolder(ids, targetFolderId)
            clearSelection()
            onComplete?.invoke()
        }
    }

    fun mergeSelected(
        mergedTitle: String? = null,
        deleteOriginals: Boolean = false,
        onComplete: ((Long) -> Unit)? = null
    ) {
        val ids = _selectedDocIds.value.toList()
        if (ids.size < 2) return
        viewModelScope.launch {
            val targetFolderId = _selectedFolder.value?.id ?: -1L
            val newDocId = repository.mergeDocuments(ids, mergedTitle, targetFolderId, deleteOriginals)
            clearSelection()
            onComplete?.invoke(newDocId)
        }
    }

    fun createFolder(name: String, parentFolderId: Long? = null) {
        if (name.isBlank()) return
        val parentId = parentFolderId ?: (_selectedFolder.value?.id ?: -1L)
        viewModelScope.launch {
            repository.createFolder(name = name, parentFolderId = parentId)
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renameFolder(folderId, newName)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
            if (_selectedFolder.value?.id == folder.id) {
                navigateUp()
            }
        }
    }

    fun moveDocumentToFolder(docId: Long, folderId: Long) {
        viewModelScope.launch {
            repository.moveDocumentToFolder(docId, folderId)
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            repository.deleteDocument(document)
        }
    }

    fun renameDocument(docId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateDocumentTitle(docId, newTitle)
        }
    }

    fun importImagesFromGallery(uris: List<Uri>, onDone: (Long) -> Unit) {
        if (uris.isEmpty()) return
        val currentFolderId = _selectedFolder.value?.id ?: -1L
        viewModelScope.launch(Dispatchers.IO) {
            val title = "Import " + SimpleDateFormat("dd-MM-yyyy HH.mm", Locale.getDefault()).format(Date())
            val docId = repository.createNewDocument(title, currentFolderId)
            val resolver = getApplication<Application>().contentResolver

            for (uri in uris) {
                try {
                    resolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            repository.savePage(
                                documentId = docId,
                                processedBitmap = bmp,
                                originalBitmap = bmp,
                                cropBorderJson = "",
                                enhanceModeId = 1
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                onDone(docId)
            }
        }
    }

    fun importPdfDocument(uri: Uri, onDone: (Long) -> Unit) {
        val currentFolderId = _selectedFolder.value?.id ?: -1L
        viewModelScope.launch(Dispatchers.IO) {
            val docId = repository.importPdfDocument(uri, currentFolderId)
            if (docId > 0) {
                withContext(Dispatchers.Main) {
                    onDone(docId)
                }
            }
        }
    }

    /**
     * Scans the storage vault (Documents/ScanCraft) and restores all backed-up documents.
     */
    fun restoreFromVault(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = com.krinry.scancraft.data.repository.DocumentRecoveryManager.scanAndRestoreFromVault(
                context = getApplication(),
                database = db
            )
            com.krinry.scancraft.data.repository.DocumentRecoveryManager.syncThumbnailsToPrivateCache(
                context = getApplication(),
                database = db
            )
            withContext(Dispatchers.Main) {
                onDone(restored)
            }
        }
    }

    /**
     * Automatically checks if the local database is empty and restores documents
     * from the vault without relying on asynchronous or filtered UI state.
     */
    fun autoRestoreIfDatabaseEmpty(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = com.krinry.scancraft.data.repository.DocumentRecoveryManager.autoRestoreIfDatabaseEmpty(
                context = getApplication(),
                database = db
            )
            com.krinry.scancraft.data.repository.DocumentRecoveryManager.syncThumbnailsToPrivateCache(
                context = getApplication(),
                database = db
            )
            if (restored > 0) {
                withContext(Dispatchers.Main) {
                    onDone(restored)
                }
            }
        }
    }
}
