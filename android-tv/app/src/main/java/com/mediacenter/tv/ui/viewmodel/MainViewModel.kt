package com.mediacenter.tv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.Author
import com.mediacenter.tv.data.model.CollectionItem
import com.mediacenter.tv.data.model.CreateCollectionRequest
import com.mediacenter.tv.data.model.MediaItem
import com.mediacenter.tv.data.model.Tag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MediaUiState {
    object Loading : MediaUiState()
    data class Success(val items: List<MediaItem>, val totalPages: Int, val currentPage: Int) : MediaUiState()
    data class Error(val message: String) : MediaUiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<MediaUiState>(MediaUiState.Loading)
    val uiState: StateFlow<MediaUiState> = _uiState

    private val _selectedType = MutableStateFlow<String?>(null) // null = ALL, "video", "audio", "image"
    val selectedType: StateFlow<String?> = _selectedType

    private val _serverUrl = MutableStateFlow(ApiClient.getServerUrl(getApplication()))
    val serverUrl: StateFlow<String> = _serverUrl

    private val _isLoggedIn = MutableStateFlow(!ApiClient.getToken(getApplication()).isNullOrEmpty())
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    // 筛选与排序
    val authors = MutableStateFlow<List<Author>>(emptyList())
    val tags = MutableStateFlow<List<Tag>>(emptyList())
    val collections = MutableStateFlow<List<CollectionItem>>(emptyList())

    val selectedAuthorId = MutableStateFlow<String?>(null)
    /** 多选标签（后端支持标签表达式，多个标签用 & 联合筛选） */
    val selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedCollectionId = MutableStateFlow<String?>(null)
    val sortBy = MutableStateFlow("createdAt")
    val sortOrder = MutableStateFlow("desc")

    /** 媒体 → 收藏状态缓存：mediaId → 该媒体所在的收藏夹 id 集合 */
    val mediaCollectionIds = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    private var currentPage = 1

    init {
        loadMedia()
        loadFilterOptions()
    }

    fun loadFilterOptions() {
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val authorRes = api.getAuthors()
                if (authorRes.isSuccessful) authors.value = authorRes.body()?.authors ?: emptyList()

                val tagRes = api.getTags()
                if (tagRes.isSuccessful) tags.value = tagRes.body()?.tags ?: emptyList()

                if (_isLoggedIn.value) {
                    reloadCollections()
                } else {
                    collections.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 重新拉取收藏夹列表（登录后 / 收藏变更后调用） */
    fun reloadCollections() {
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val res = api.getCollections()
                if (res.isSuccessful) {
                    collections.value = res.body()?.collections ?: emptyList()
                } else if (res.code() == 401) {
                    // 会话失效
                    ApiClient.saveToken(getApplication(), null)
                    _isLoggedIn.value = false
                    collections.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setFilterType(type: String?) {
        if (_selectedType.value != type) {
            _selectedType.value = type
            selectedCollectionId.value = null
            currentPage = 1
            loadMedia()
        }
    }

    fun setFilterAuthor(authorId: String?) {
        if (selectedAuthorId.value != authorId) {
            selectedAuthorId.value = authorId
            selectedCollectionId.value = null
            currentPage = 1
            loadMedia()
        }
    }

    /** 切换一个标签的选中状态（多选） */
    fun toggleTag(tagName: String) {
        selectedCollectionId.value = null
        val next = selectedTags.value.toMutableSet()
        if (!next.add(tagName)) next.remove(tagName)
        selectedTags.value = next
        currentPage = 1
        loadMedia()
    }

    /** 一次性替换多选标签集合（批量选择后统一加载） */
    fun setSelectedTags(tagNames: Set<String>) {
        if (selectedTags.value != tagNames) {
            selectedTags.value = tagNames
            selectedCollectionId.value = null
            currentPage = 1
            loadMedia()
        }
    }

    fun clearTags() {
        if (selectedTags.value.isNotEmpty()) {
            selectedTags.value = emptySet()
            currentPage = 1
            loadMedia()
        }
    }

    fun setFilterCollection(collectionId: String?) {
        if (selectedCollectionId.value != collectionId) {
            selectedCollectionId.value = collectionId
            currentPage = 1
            loadMedia()
        }
    }

    fun setSort(sort: String) {
        if (sortBy.value == sort) {
            sortOrder.value = if (sortOrder.value == "desc") "asc" else "desc"
        } else {
            sortBy.value = sort
            sortOrder.value = "desc"
        }
        currentPage = 1
        loadMedia()
    }

    fun updateServerUrl(url: String) {
        ApiClient.saveServerUrl(getApplication(), url)
        _serverUrl.value = ApiClient.getServerUrl(getApplication())
        currentPage = 1
        loadMedia()
        loadFilterOptions()
    }

    fun refresh() {
        currentPage = 1
        loadMedia()
        loadFilterOptions()
    }

    fun login(username: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val response = api.login(com.mediacenter.tv.data.model.LoginRequest(username, password))
                if (response.isSuccessful && response.body() != null) {
                    val token = response.body()!!.token
                    ApiClient.saveToken(getApplication(), token)
                    _isLoggedIn.value = true
                    loadMedia()
                    loadFilterOptions()
                    onResult(true, null)
                } else {
                    onResult(false, "登录失败: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                onResult(false, "网络错误: ${e.localizedMessage}")
            }
        }
    }

    fun logout() {
        ApiClient.saveToken(getApplication(), null)
        _isLoggedIn.value = false
        collections.value = emptyList()
        mediaCollectionIds.value = emptyMap()
        selectedCollectionId.value = null
        loadMedia()
    }

    // ========== 收藏夹操作 ==========

    /** 拉取某媒体所在的收藏夹 id 集合（用于 UI 展示收藏状态） */
    fun loadMediaCollections(mediaId: String, onLoaded: (Set<String>) -> Unit = {}) {
        if (!_isLoggedIn.value) {
            onLoaded(emptySet())
            return
        }
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val res = api.getMediaCollections(mediaId)
                if (res.isSuccessful) {
                    val ids = res.body()?.collectionIds?.toSet() ?: emptySet()
                    mediaCollectionIds.value = mediaCollectionIds.value + (mediaId to ids)
                    onLoaded(ids)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /** 把媒体加入收藏夹 */
    fun addToCollection(collectionId: String, mediaId: String, onResult: (Boolean, String?) -> Unit) {
        if (!_isLoggedIn.value) {
            onResult(false, "请先登录")
            return
        }
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val res = api.addMediaToCollection(
                    collectionId,
                    com.mediacenter.tv.data.model.AddToCollectionRequest(listOf(mediaId))
                )
                if (res.isSuccessful) {
                    // 更新本地缓存与收藏夹计数
                    val current = mediaCollectionIds.value[mediaId] ?: emptySet()
                    mediaCollectionIds.value = mediaCollectionIds.value + (mediaId to (current + collectionId))
                    collections.value = collections.value.map {
                        if (it.id == collectionId) it.copy(mediaCount = (it.mediaCount ?: 0) + 1) else it
                    }
                    // 若当前正处于该收藏夹视图，刷新列表
                    if (selectedCollectionId.value == collectionId) loadMedia()
                    onResult(true, null)
                } else {
                    onResult(false, "添加失败: ${res.code()}")
                }
            } catch (e: Exception) {
                onResult(false, "网络错误: ${e.localizedMessage}")
            }
        }
    }

    /** 从收藏夹移除媒体 */
    fun removeFromCollection(collectionId: String, mediaId: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val res = api.removeMediaFromCollection(collectionId, mediaId)
                if (res.isSuccessful) {
                    val current = mediaCollectionIds.value[mediaId] ?: emptySet()
                    mediaCollectionIds.value = mediaCollectionIds.value + (mediaId to (current - collectionId))
                    collections.value = collections.value.map {
                        if (it.id == collectionId) it.copy(mediaCount = maxOf(0, (it.mediaCount ?: 0) - 1)) else it
                    }
                    // 若当前正处于该收藏夹视图，刷新列表
                    if (selectedCollectionId.value == collectionId) loadMedia()
                    onResult(true, null)
                } else {
                    onResult(false, "移除失败: ${res.code()}")
                }
            } catch (e: Exception) {
                onResult(false, "网络错误: ${e.localizedMessage}")
            }
        }
    }

    /** 新建收藏夹 */
    fun createCollection(name: String, onResult: (Boolean, String?) -> Unit) {
        if (!_isLoggedIn.value) {
            onResult(false, "请先登录")
            return
        }
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            onResult(false, "收藏夹名称不能为空")
            return
        }
        viewModelScope.launch {
            try {
                val api = ApiClient.getApi(getApplication())
                val res = api.createCollection(CreateCollectionRequest(name = trimmed))
                if (res.isSuccessful) {
                    reloadCollections()
                    onResult(true, null)
                } else {
                    onResult(false, "创建失败: ${res.code()}")
                }
            } catch (e: Exception) {
                onResult(false, "网络错误: ${e.localizedMessage}")
            }
        }
    }

    // ========== 媒体加载 ==========

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = MediaUiState.Loading
            try {
                val api = ApiClient.getApi(getApplication())

                if (selectedCollectionId.value != null) {
                    val response = api.getCollectionMedia(selectedCollectionId.value!!)
                    if (response.isSuccessful && response.body() != null) {
                        val data = response.body()!!
                        _uiState.value = MediaUiState.Success(
                            items = data.items,
                            totalPages = data.pagination?.totalPages ?: 1,
                            currentPage = data.pagination?.page ?: 1
                        )
                    } else {
                        _uiState.value = MediaUiState.Error(
                            when (response.code()) {
                                401 -> "请先登录后查看收藏夹"
                                403 -> "无权访问该收藏夹"
                                else -> "加载收藏库失败: ${response.code()}"
                            }
                        )
                    }
                    return@launch
                }

                // 多选标签 → 标签表达式（AND 语义），如 "A&B"
                val tagsExpr = selectedTags.value.takeIf { it.isNotEmpty() }?.joinToString("&")

                val response = api.getMediaList(
                    page = currentPage,
                    limit = 40,
                    type = _selectedType.value,
                    authorId = selectedAuthorId.value,
                    tags = tagsExpr,
                    sortBy = sortBy.value,
                    sortOrder = sortOrder.value
                )

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = MediaUiState.Success(
                        items = data.items,
                        totalPages = data.pagination?.totalPages ?: 1,
                        currentPage = data.pagination?.page ?: 1
                    )
                } else {
                    _uiState.value = MediaUiState.Error("加载失败: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                _uiState.value = MediaUiState.Error("网络连接失败: ${e.localizedMessage}")
            }
        }
    }
}
