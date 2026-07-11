package com.pulsemessenger.android.feature.invitations

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsemessenger.android.core.network.InvitationDto
import kotlinx.coroutines.launch

class InvitationsViewModel(
    private val repository: InvitationsRepository,
) : ViewModel() {
    var invitations by mutableStateOf<List<InvitationDto>>(emptyList())
    var isLoading by mutableStateOf(false)
    var actionInvitationId by mutableStateOf<Long?>(null)
    var error by mutableStateOf<String?>(null)

    fun load() {
        if (isLoading) return
        isLoading = true
        error = null
        viewModelScope.launch {
            repository.loadInvitations()
                .onSuccess { invitations = it }
                .onFailure { error = it.message ?: "Failed to load invitations" }
            isLoading = false
        }
    }

    fun accept(invitationId: Long) {
        actionInvitationId = invitationId
        error = null
        viewModelScope.launch {
            repository.acceptInvitation(invitationId)
                .onSuccess {
                    invitations = invitations.filterNot { it.id == invitationId }
                }
                .onFailure {
                    error = it.message ?: "Failed to accept invitation"
                }
            actionInvitationId = null
        }
    }

    fun decline(invitationId: Long) {
        actionInvitationId = invitationId
        error = null
        viewModelScope.launch {
            repository.declineInvitation(invitationId)
                .onSuccess {
                    invitations = invitations.filterNot { it.id == invitationId }
                }
                .onFailure {
                    error = it.message ?: "Failed to decline invitation"
                }
            actionInvitationId = null
        }
    }

    fun replaceInvitations(items: List<InvitationDto>) {
        invitations = items
    }
}
