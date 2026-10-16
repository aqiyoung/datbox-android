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

    val user: StateFlow<UserInfo?> = _user

    init {
        viewModelScope.launch {
            _user.value = repo.currentUser().getOrNull()
        }
    }
}
