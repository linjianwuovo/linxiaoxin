package com.linxin.feature.login.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.linxin.core.auth.SavedCredentialStore
import com.linxin.feature.login.data.LoginRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val userCode: String = "",
    val password: String = "",
    val rememberPassword: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loginSuccess: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val credentials: SavedCredentialStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState

    init {
        restore()
    }

    private fun restore() {
        viewModelScope.launch {
            val saved = credentials.load()
            _uiState.update {
                it.copy(
                    userCode = saved?.userCode?.takeIf { code -> code.isNotBlank() }
                        ?: credentials.savedUserCode(),
                    password = saved?.password.orEmpty(),
                    rememberPassword = saved != null,
                )
            }
        }
    }

    fun onUserCodeChange(value: String) {
        _uiState.update { it.copy(userCode = value, error = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, error = null) }
    }

    fun onRememberPasswordChange(checked: Boolean) {
        _uiState.update { it.copy(rememberPassword = checked) }
        // 取消勾选就当场删掉，不等下一次登录
        if (!checked) viewModelScope.launch { credentials.clearPassword() }
    }

    fun login() {
        val state = _uiState.value
        if (state.userCode.isBlank() || state.password.isBlank()) {
            _uiState.update { it.copy(error = "请输入学号和密码") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = loginRepository.login(state.userCode, state.password)

            result.fold(
                onSuccess = {
                    credentials.save(
                        userCode = state.userCode,
                        password = state.password.takeIf { state.rememberPassword },
                    )
                    _uiState.update { it.copy(isLoading = false, loginSuccess = true) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoading = false, error = e.message ?: "登录失败")
                    }
                },
            )
        }
    }
}
