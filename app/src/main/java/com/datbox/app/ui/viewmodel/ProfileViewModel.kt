package com.datbox.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datbox.app.data.api.ManagementRepository
import com.datbox.app.data.model.UserInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repo: ManagementRepository,
) : ViewModel() {
    private val _user = MutableStateFlow<UserInfo?>(null)

    /** 当前登录用户 (/api/me); null = 加载中或加载失败 (UI 显示占位) */
    val user: StateFlow<UserInfo?> = _user

    init {
        viewModelScope.launch {
            _user.value = repo.currentUser().getOrNull()
        }
    }
}
