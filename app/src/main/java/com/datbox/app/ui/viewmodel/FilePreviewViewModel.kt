package com.datbox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.data.api.OpenListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 预览页只做一件事：把远端路径换成带签名的下载地址。 */
@HiltViewModel
class FilePreviewViewModel @Inject constructor(
    private val repo: OpenListRepository,
) : ViewModel() {

    private val _url = MutableStateFlow<Result<String>?>(null)
    val url = _url.asStateFlow()

    private var job: Job? = null

    fun load(remotePath: String) {
        job?.cancel()
        _url.value = null
        job = viewModelScope.launch { _url.value = repo.previewUrl(remotePath) }
    }
}
