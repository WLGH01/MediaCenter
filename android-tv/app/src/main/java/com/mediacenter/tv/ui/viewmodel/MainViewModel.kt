package com.mediacenter.tv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.Author
import com.mediacenter.tv.data.model.CollectionItem
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
    val selectedTag = MutableStateFlow<String?>(null)
    val selectedCollectionId = MutableStateFlow<String?>(null)
    val sortBy = MutableStateFlow("createdAt")
    val sortOrder = MutableStateFlow("desc")

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
                    val collRes = api.getCollections()
                    if (collRes.isSuccessful) collections.value = collRes.body() ?: emptyList()
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

    fun setFilterTag(tagName: String?) {
        if (selectedTag.value != tagName) {
            selectedTag.value = tagName
            selectedCollectionId.value = null
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
        selectedCollectionId.value = null
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = MediaUiState.Loading
            try {
                val api = ApiClient.getApi(getApplication())

                if (selectedCollectionId.value != null) {
                    val response = api.getCollectionMedia(selectedCollectionId.value!!)
                    if (response.isSuccessful && response.body() != null) {
                        _uiState.value = MediaUiState.Success(
                            items = response.body()!!,
                            totalPages = 1,
                            currentPage = 1
                        )
                    } else {
                        _uiState.value = MediaUiState.Error("加载收藏库失败: ${response.code()}")
                    }
                    return@launch
                }

                val response = api.getMediaList(
                    page = currentPage,
                    limit = 40,
                    type = _selectedType.value,
                    authorId = selectedAuthorId.value,
                    tags = selectedTag.value,
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
