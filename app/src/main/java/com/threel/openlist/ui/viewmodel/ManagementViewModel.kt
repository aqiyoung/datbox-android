package com.threel.openlist.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threel.openlist.data.api.ManagementRepository
import com.threel.openlist.data.model.Mount
import com.threel.openlist.data.model.Option
import com.threel.openlist.data.model.Overview
import com.threel.openlist.data.model.Share
import com.threel.openlist.data.model.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManagementState(
    val users: List<User> = emptyList(),
    val mounts: List<Mount> = emptyList(),
    val shares: List<Share> = emptyList(),
    val options: List<Option> = emptyList(),
    val overview: Overview? = null,
    val loading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ManagementViewModel @Inject constructor(
    private val repo: ManagementRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ManagementState())
    val state = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val users = repo.userList().getOrNull()
            val mounts = repo.mountList().getOrNull()
            val shares = repo.shareList().getOrNull()
            val options = repo.optionList().getOrNull()
            _state.value = _state.value.copy(
                users = users ?: emptyList(),
                mounts = mounts ?: emptyList(),
                shares = shares ?: emptyList(),
                options = options ?: emptyList(),
                loading = false,
                error = if (users == null && mounts == null && shares == null) "加载失败" else null
            )
        }
    }

    // ===== 用户管理 =====
    fun createUser(username: String, password: String, role: Int = 1, permission: Int = 0, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.userCreate(username, password, role, permission)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun updateUser(id: Int, username: String = "", password: String = "", role: Int = -1, permission: Int = -1, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.userUpdate(id, username, password, role, permission)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteUser(id: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.userDelete(id)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    // ===== 挂载管理 =====
    fun createMount(name: String, driver: String = "Local", path: String = "", status: Int = 1, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.mountCreate(name, driver, path, status)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun updateMount(id: Int, name: String = "", driver: String = "", path: String = "", status: Int = -1, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.mountUpdate(id, name, driver, path, status)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteMount(id: Int, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.mountDelete(id)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    // ===== 分享管理 =====
    fun createShare(files: List<String>, expires: String = "", password: String = "", onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.shareCreate(files, expires, password)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteShare(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.shareDelete(id)
                .onSuccess { loadAll(); onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    // ===== 文件操作 =====
    fun mkdir(path: String, name: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.mkdir(path, name)
                .onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun rename(path: String, newName: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.rename(path, newName)
                .onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun move(path: String, newPath: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.move(path, newPath)
                .onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun deleteFile(path: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repo.delete(path)
                .onSuccess { onSuccess() }
                .onFailure { _state.value = _state.value.copy(error = it.message) }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
