package com.pulsemessenger.android.core.network

data class LoginRequest(
    val username: String,
    val password: String,
)

data class MeUserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val isAdmin: Boolean = false,
    val createdAt: String = "",
)

data class LoginResponse(
    val token: String,
    val user: MeUserDto,
)

data class MeResponse(
    val user: MeUserDto,
)

data class DialogUserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val isAdmin: Boolean = false,
    val createdAt: String = "",
    val lastSeenAt: String? = null,
    val online: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastFileName: String = "",
    val lastMessageType: String = "text",
    val lastMessageAt: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
)

data class UsersResponse(
    val users: List<DialogUserDto> = emptyList(),
)

data class MessageReactionDto(
    val emoji: String,
    val count: Int = 0,
    val reactedByMe: Boolean = false,
)

data class PollVoterDto(
    val id: Long,
    val username: String,
    val displayName: String,
)

data class PollOptionDto(
    val id: Long,
    val text: String,
    val votes: Int = 0,
    val votedByMe: Boolean = false,
    val voters: List<PollVoterDto> = emptyList(),
)

data class PollDto(
    val id: Long,
    val question: String,
    val creatorId: Long,
    val isClosed: Boolean = false,
    val allowMultiple: Boolean = false,
    val createdAt: String = "",
    val options: List<PollOptionDto> = emptyList(),
)

data class DmMessageDto(
    val id: Long,
    val senderId: Long,
    val receiverId: Long,
    val content: String = "",
    val type: String = "text",
    val imageUrl: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val fileSize: Long? = null,
    val mediaGroupId: String = "",
    val forwardedFromName: String = "",
    val replyToMessageId: Long? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val poll: PollDto? = null,
    val reactions: List<MessageReactionDto> = emptyList(),
    val createdAt: String = "",
)

data class MessagesResponse(
    val messages: List<DmMessageDto> = emptyList(),
    val hasMore: Boolean = false,
    val peerLastReadAt: String? = null,
)

data class SharedAttachmentDto(
    val id: Long,
    val kind: String = "",
    val url: String = "",
    val fileName: String = "",
    val fileSize: Long? = null,
    val createdAt: String = "",
)

data class AttachmentsResponse(
    val attachments: List<SharedAttachmentDto> = emptyList(),
)

data class PollCreationRequest(
    val question: String,
    val options: List<String>,
    val allowMultiple: Boolean = false,
)

data class SendMessageRequest(
    val content: String,
    val replyToMessageId: Long? = null,
    val imageUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val mediaGroupId: String? = null,
    val poll: PollCreationRequest? = null,
    val forwardedFromName: String? = null,
)

data class EditMessageRequest(
    val content: String,
)

data class SendMessageResponse(
    val message: DmMessageDto,
)

data class RoomDto(
    val id: Long,
    val name: String,
    val accessType: String = "public",
    val createdBy: Long? = null,
    val createdAt: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val slug: String = "",
    val joined: Boolean = false,
    val hasInvitation: Boolean = false,
    val hasJoinRequest: Boolean = false,
    val membersCount: Int = 0,
    val canManage: Boolean = false,
    val canOwn: Boolean = false,
    val canPost: Boolean = true,
    val canInvite: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastMessageAt: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
)

data class RoomsResponse(
    val rooms: List<RoomDto> = emptyList(),
)

data class RoomMessageSenderDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val isAdmin: Boolean = false,
)

data class RoomMessageDto(
    val id: Long,
    val roomId: Long,
    val senderId: Long,
    val content: String = "",
    val type: String = "text",
    val imageUrl: String = "",
    val fileUrl: String = "",
    val fileName: String = "",
    val fileSize: Long? = null,
    val mediaGroupId: String = "",
    val forwardedFromName: String = "",
    val replyToMessageId: Long? = null,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val poll: PollDto? = null,
    val reactions: List<MessageReactionDto> = emptyList(),
    val createdAt: String = "",
    val sender: RoomMessageSenderDto,
)

data class ToggleReactionRequest(
    val emoji: String,
)

data class VotePollRequest(
    val optionId: Long? = null,
    val optionIds: List<Long>? = null,
)

data class PollResponse(
    val poll: PollDto,
)

data class InvitationUserDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
)

data class InvitationDto(
    val id: Long,
    val roomId: Long,
    val roomName: String,
    val roomAccessType: String = "public",
    val createdAt: String = "",
    val inviter: InvitationUserDto? = null,
)

data class InvitationsResponse(
    val invitations: List<InvitationDto> = emptyList(),
)

data class InvitationActionResponse(
    val ok: Boolean = false,
    val roomId: Long? = null,
    val roomName: String? = null,
)

data class SessionDto(
    val id: String,
    val userAgent: String = "",
    val ip: String = "",
    val createdAt: String = "",
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val isCurrent: Boolean = false,
)

data class SessionsResponse(
    val sessions: List<SessionDto> = emptyList(),
)

data class DeviceDto(
    val id: Long,
    val displayName: String = "Устройство",
    val platform: String = "",
    val browser: String = "",
    val ip: String = "",
    val firstSeenAt: String = "",
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val activeSessions: Int = 0,
    val isCurrent: Boolean = false,
)

data class DevicesResponse(
    val devices: List<DeviceDto> = emptyList(),
)

data class RevokeSessionResponse(
    val ok: Boolean = false,
    val revokedCurrent: Boolean = false,
)

data class RevokeDeviceResponse(
    val ok: Boolean = false,
    val revokedCurrent: Boolean = false,
)

data class RoomMessagesResponse(
    val messages: List<RoomMessageDto> = emptyList(),
    val hasMore: Boolean = false,
)

data class SendRoomMessageResponse(
    val message: RoomMessageDto,
)

data class UploadImageResponse(
    val imageUrl: String,
)

data class UploadRoomAvatarResponse(
    val avatarUrl: String,
)

data class SearchResultDto(
    val id: Long,
    val senderId: Long,
    val content: String = "",
    val createdAt: String = "",
    val scope: String = "dm",
    val targetId: Long? = null,
    val targetName: String? = null,
    val roomId: Long? = null,
    val roomName: String? = null,
)

data class SearchResponse(
    val results: List<SearchResultDto> = emptyList(),
)

data class UploadFileResponse(
    val fileUrl: String,
    val fileName: String,
    val fileSize: Long? = null,
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String,
)

data class PinMessageRequest(
    val scope: String,
    val targetId: Long,
    val messageId: Long,
)

data class UnpinRequest(
    val scope: String,
    val targetId: Long,
)

data class JoinRequestDto(
    val userId: Long,
    val username: String = "",
    val displayName: String = "",
    val avatarUrl: String = "",
    val createdAt: String = "",
)

data class JoinRequestsResponse(
    val requests: List<JoinRequestDto> = emptyList(),
)

data class ChatPrefsRequest(
    val scope: String,
    val targetId: Long,
    val pinned: Boolean? = null,
    val muted: Boolean? = null,
    val archived: Boolean? = null,
)

data class ErrorResponse(
    val error: String? = null,
)

data class CreateRoomRequest(
    val name: String,
    val accessType: String = "public",
    val description: String = "",
    val slug: String = "",
)

data class CreateRoomResponse(
    val room: RoomDto,
)

data class UpdateProfileRequest(
    val displayName: String,
)

data class UpdateProfileResponse(
    val user: MeUserDto,
    val avatarUrl: String? = null,
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

data class AdminStats(
    val users: Int = 0,
    val rooms: Int = 0,
    val dmMessages: Int = 0,
    val roomMessages: Int = 0,
    val polls: Int = 0,
)

data class AdminOverviewResponse(
    val stats: AdminStats = AdminStats(),
)

data class AdminUsersResponse(
    val users: List<DialogUserDto> = emptyList(),
)

data class AdminCreateUserRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val isAdmin: Boolean = false,
)

data class AdminCreateUserResponse(
    val user: DialogUserDto,
)

data class AdminToggleRoleRequest(
    val isAdmin: Boolean,
)

data class AdminPasswordRequest(
    val newPassword: String,
)

data class NotificationStatusResponse(
    val pushEnabled: Boolean = false,
    val subscriptions: Int = 0,
)

data class GenericOkResponse(
    val ok: Boolean = false,
)

data class MarkReadResponse(
    val ok: Boolean = false,
    val readAt: String? = null,
)

data class RegisterFcmRequest(
    val token: String,
)

data class RoomMemberDto(
    val id: Long,
    val username: String,
    val displayName: String,
    val avatarUrl: String = "",
    val isAdmin: Boolean = false,
    val role: String = "member",
    val isMuted: Boolean = false,
    val canPostMedia: Boolean = true,
    val online: Boolean = false,
)

data class RoomDetailResponse(
    val id: Long,
    val name: String,
    val accessType: String = "public",
    val createdBy: Long? = null,
    val createdAt: String = "",
    val avatarUrl: String = "",
    val description: String = "",
    val slug: String = "",
    val joined: Boolean = false,
    val hasInvitation: Boolean = false,
    val hasJoinRequest: Boolean = false,
    val membersCount: Int = 0,
    val canManage: Boolean = false,
    val canOwn: Boolean = false,
    val canPost: Boolean = true,
    val canInvite: Boolean = false,
    val unreadCount: Int = 0,
    val lastMessage: String = "",
    val lastMessageType: String = "text",
    val lastMessageAt: String? = null,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val members: List<RoomMemberDto> = emptyList(),
)

data class UpdateRoomRequest(
    val name: String? = null,
    val description: String? = null,
    val avatarUrl: String? = null,
    val accessType: String? = null,
    val slug: String? = null,
    val whoCanPost: String? = null,
    val whoCanInvite: String? = null,
)

data class UpdateRoomResponse(
    val room: RoomDto,
)

data class InviteCandidatesResponse(
    val users: List<DialogUserDto> = emptyList(),
)

data class InviteUserRequest(
    val userId: Long,
)

data class MemberActionRequest(
    val role: String? = null,
    val isMuted: Boolean? = null,
    val canPostMedia: Boolean? = null,
)
