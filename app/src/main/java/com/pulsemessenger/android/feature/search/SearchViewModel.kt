package com.pulsemessenger.android.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.SearchResultDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(
    private val repository: SearchRepository,
) : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SearchResultDto>>(emptyList())
    var isSearching by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    private var searchJob: Job? = null

    fun onQueryChanged(value: String) {
        query = value
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            search()
        }
    }

    fun search() {
        val q = query.trim()
        if (q.length < 2) {
            results = emptyList()
            return
        }
        isSearching = true
        error = null
        viewModelScope.launch {
            repository.searchMessages(q)
                .onSuccess { results = it }
                .onFailure { error = it.message ?: "Search failed" }
            isSearching = false
        }
    }
}
