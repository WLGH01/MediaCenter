package com.mediacenter.tv.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediacenter.tv.data.api.ApiClient
import com.mediacenter.tv.data.model.MediaItem
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

    private var currentPage = 1

    init {
        loadMedia()
    }

    fun setFilterType(type: String?) {
        if (_selectedType.value != type) {
            _selectedType.value = type
            currentPage = 1
            loadMedia()
        }
    }

    fun updateServerUrl(url: String) {
        ApiClient.saveServerUrl(getApplication(), url)
        _serverUrl.value = ApiClient.getServerUrl(getApplication())
        currentPage = 1
        loadMedia()
    }

    fun refresh() {
        currentPage = 1
        loadMedia()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = MediaUiState.Loading
            try {
                val api = ApiClient.getApi(getApplication())
                val response = api.getMediaList(
                    page = currentPage,
                    limit = 40,
                    type = _selectedType.value
                )

                if (response.isSuccessful && response.body() != null) {
                    val data = response.body()!!
                    _uiState.value = MediaUiState.Success(
                        items = data.items,
                        totalPages = data.totalPages,
                        currentPage = data.page
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
