package com.example.presentation.maillist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Mail
import com.example.domain.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MailListUiState(
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

class MailListViewModel(
    private val mailRepository: MailRepository
) : ViewModel() {

    val mails: StateFlow<List<Mail>> = mailRepository.getAllMails()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(MailListUiState())
    val uiState: StateFlow<MailListUiState> = _uiState.asStateFlow()

    fun refreshMails() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, errorMessage = null)
            val result = mailRepository.fetchAndSaveNewMails()
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            } else {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Failed to refresh mails."
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    companion object {
        fun provideFactory(mailRepository: MailRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MailListViewModel(mailRepository) as T
                }
            }
    }
}
