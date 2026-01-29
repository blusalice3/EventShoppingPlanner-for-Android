package com.example.eventshoppingplanner.presentation.screens.importscreen

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.eventshoppingplanner.domain.model.Event
import com.example.eventshoppingplanner.domain.model.ShoppingItem
import com.example.eventshoppingplanner.domain.repository.EventRepository
import com.example.eventshoppingplanner.domain.repository.ShoppingItemRepository
import com.example.eventshoppingplanner.util.CsvParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class ImportUiState(
    val eventName: String = "",
    val selectedFileUri: Uri? = null,
    val selectedFileName: String? = null,
    val parsedItems: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = false,
    val isParsed: Boolean = false,
    val errorMessage: String? = null,
    val isImportComplete: Boolean = false,
    val createdEventId: String? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val itemRepository: ShoppingItemRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var tempEventId: String = UUID.randomUUID().toString()

    fun updateEventName(name: String) {
        _uiState.update { it.copy(eventName = name, errorMessage = null) }
    }

    fun selectFile(uri: Uri, fileName: String?) {
        _uiState.update {
            it.copy(
                selectedFileUri = uri,
                selectedFileName = fileName,
                isParsed = false,
                parsedItems = emptyList(),
                errorMessage = null
            )
        }
    }

    fun parseFile(inputStream: InputStream) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = CsvParser.parseFromInputStream(inputStream, tempEventId)

            result.fold(
                onSuccess = { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isParsed = true,
                            parsedItems = items,
                            errorMessage = if (items.isEmpty()) "有効なデータが見つかりませんでした" else null
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isParsed = false,
                            errorMessage = "ファイルの読み込みに失敗しました: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun importData() {
        val state = _uiState.value
        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "イベント名を入力してください") }
            return
        }
        if (state.parsedItems.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "インポートするデータがありません") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                // イベント作成
                val event = Event(
                    id = tempEventId,
                    name = state.eventName,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                eventRepository.insertEvent(event)

                // アイテム追加
                itemRepository.insertItems(state.parsedItems)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isImportComplete = true,
                        createdEventId = tempEventId
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "インポートに失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    fun createEmptyEvent() {
        val state = _uiState.value
        if (state.eventName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "イベント名を入力してください") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val event = Event(
                    id = tempEventId,
                    name = state.eventName,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now()
                )
                eventRepository.insertEvent(event)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isImportComplete = true,
                        createdEventId = tempEventId
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "作成に失敗しました: ${e.message}"
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}