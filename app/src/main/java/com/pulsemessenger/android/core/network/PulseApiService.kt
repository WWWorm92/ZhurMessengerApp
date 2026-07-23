package com.pulsemessenger.android.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface PulseApiService {
    @POST("/api/auth/register")
    suspend fun register(@Body body: RegisterRequest): Response<LoginResponse>

    @POST("/api/auth/login")
    suspend fun login(@Body body: LoginRequest): Response<LoginResponse>

    @POST("/api/auth/refresh")
    suspend fun refresh(): Response<LoginResponse>

    @GET("/api/auth/me")
    suspend fun me(@Header("Authorization") authorization: String): Response<MeResponse>

    @GET("/api/auth/sessions")
    suspend fun sessions(@Header("Authorization") authorization: String): Response<SessionsResponse>

    @GET("/api/auth/devices")
    suspend fun devices(@Header("Authorization") authorization: String): Response<DevicesResponse>

    @DELETE("/api/auth/sessions/{sessionId}")
    suspend fun revokeSession(
        @Header("Authorization") authorization: String,
        @Path("sessionId") sessionId: String,
    ): Response<RevokeSessionResponse>

    @DELETE("/api/auth/devices/{deviceId}")
    suspend fun revokeDevice(
        @Header("Authorization") authorization: String,
        @Path("deviceId") deviceId: Long,
    ): Response<RevokeDeviceResponse>

    @GET("/api/users")
    suspend fun users(@Header("Authorization") authorization: String): Response<UsersResponse>

    @GET("/api/messages/{userId}")
    suspend fun dmMessages(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 60,
        @Query("beforeId") beforeId: Long? = null,
    ): Response<MessagesResponse>

    @GET("/api/messages/{userId}/attachments")
    suspend fun dmAttachments(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<AttachmentsResponse>

    @POST("/api/messages/{userId}/read")
    suspend fun markDmRead(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<MarkReadResponse>

    @POST("/api/messages/{userId}")
    suspend fun sendDmMessage(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Body body: SendMessageRequest,
    ): Response<SendMessageResponse>

    @PATCH("/api/messages/item/{messageId}")
    suspend fun editDmMessage(
        @Header("Authorization") authorization: String,
        @Path("messageId") messageId: Long,
        @Body body: EditMessageRequest,
    ): Response<SendMessageResponse>

    @DELETE("/api/messages/item/{messageId}")
    suspend fun deleteDmMessage(
        @Header("Authorization") authorization: String,
        @Path("messageId") messageId: Long,
    ): Response<SendMessageResponse>

    @POST("/api/messages/item/{messageId}/reactions")
    suspend fun toggleDmReaction(
        @Header("Authorization") authorization: String,
        @Path("messageId") messageId: Long,
        @Body body: ToggleReactionRequest,
    ): Response<SendMessageResponse>

    @GET("/api/rooms")
    suspend fun rooms(@Header("Authorization") authorization: String): Response<RoomsResponse>

    @POST("/api/rooms")
    suspend fun createRoom(
        @Header("Authorization") authorization: String,
        @Body body: CreateRoomRequest,
    ): Response<CreateRoomResponse>

    @GET("/api/invitations")
    suspend fun invitations(@Header("Authorization") authorization: String): Response<InvitationsResponse>

    @POST("/api/invitations/{invitationId}/accept")
    suspend fun acceptInvitation(
        @Header("Authorization") authorization: String,
        @Path("invitationId") invitationId: Long,
    ): Response<InvitationActionResponse>

    @POST("/api/invitations/{invitationId}/decline")
    suspend fun declineInvitation(
        @Header("Authorization") authorization: String,
        @Path("invitationId") invitationId: Long,
    ): Response<InvitationActionResponse>

    @GET("/api/rooms/{roomId}/messages")
    suspend fun roomMessages(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Query("limit") limit: Int = 60,
        @Query("beforeId") beforeId: Long? = null,
    ): Response<RoomMessagesResponse>

    @GET("/api/rooms/{roomId}/attachments")
    suspend fun roomAttachments(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<AttachmentsResponse>

    @POST("/api/rooms/{roomId}/read")
    suspend fun markRoomRead(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<MarkReadResponse>

    @POST("/api/rooms/{roomId}/messages")
    suspend fun sendRoomMessage(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Body body: SendMessageRequest,
    ): Response<SendRoomMessageResponse>

    @PATCH("/api/rooms/{roomId}/messages/{messageId}")
    suspend fun editRoomMessage(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("messageId") messageId: Long,
        @Body body: EditMessageRequest,
    ): Response<SendRoomMessageResponse>

    @DELETE("/api/rooms/{roomId}/messages/{messageId}")
    suspend fun deleteRoomMessage(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("messageId") messageId: Long,
    ): Response<SendRoomMessageResponse>

    @POST("/api/rooms/{roomId}/messages/{messageId}/reactions")
    suspend fun toggleRoomReaction(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("messageId") messageId: Long,
        @Body body: ToggleReactionRequest,
    ): Response<SendRoomMessageResponse>

    @POST("/api/polls/{pollId}/vote")
    suspend fun votePoll(
        @Header("Authorization") authorization: String,
        @Path("pollId") pollId: Long,
        @Body body: VotePollRequest,
    ): Response<PollResponse>

    @POST("/api/polls/{pollId}/close")
    suspend fun closePoll(
        @Header("Authorization") authorization: String,
        @Path("pollId") pollId: Long,
    ): Response<PollResponse>

    @Multipart
    @POST("/api/uploads/room-avatar")
    suspend fun uploadRoomAvatar(
        @Header("Authorization") authorization: String,
        @Part avatar: MultipartBody.Part,
    ): Response<UploadRoomAvatarResponse>

    @Multipart
    @POST("/api/uploads/message-image")
    suspend fun uploadMessageImage(
        @Header("Authorization") authorization: String,
        @Part image: MultipartBody.Part,
    ): Response<UploadImageResponse>

    @Multipart
    @POST("/api/uploads/message-file")
    suspend fun uploadMessageFile(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
    ): Response<UploadFileResponse>

    @Multipart
    @POST("/api/uploads/encrypted-message-file")
    suspend fun uploadEncryptedMessageFile(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
    ): Response<UploadFileResponse>


    @Streaming
    @GET
    suspend fun downloadMedia(
        @Header("Authorization") authorization: String,
        @Url url: String,
    ): Response<ResponseBody>

    @PATCH("/api/profile")
    suspend fun updateProfile(
        @Header("Authorization") authorization: String,
        @Body body: UpdateProfileRequest,
    ): Response<UpdateProfileResponse>

    @Multipart
    @POST("/api/uploads/avatar")
    suspend fun uploadAvatar(
        @Header("Authorization") authorization: String,
        @Part avatar: MultipartBody.Part,
    ): Response<UpdateProfileResponse>

    @PATCH("/api/profile/password")
    suspend fun changePassword(
        @Header("Authorization") authorization: String,
        @Body body: ChangePasswordRequest,
    ): Response<GenericOkResponse>

    @GET("/api/rooms/{roomId}")
    suspend fun roomDetail(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<RoomDetailResponse>

    @PATCH("/api/rooms/{roomId}")
    suspend fun updateRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Body body: UpdateRoomRequest,
    ): Response<UpdateRoomResponse>

    @GET("/api/rooms/{roomId}/invite-candidates")
    suspend fun inviteCandidates(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<InviteCandidatesResponse>

    @POST("/api/rooms/{roomId}/invite-user")
    suspend fun inviteRoomUser(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Body body: InviteUserRequest,
    ): Response<GenericOkResponse>

    @GET("/api/rooms/{roomId}/requests")
    suspend fun joinRequests(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<JoinRequestsResponse>

    @POST("/api/rooms/{roomId}/requests/{userId}/approve")
    suspend fun approveJoinRequest(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("userId") userId: Long,
    ): Response<GenericOkResponse>

    @DELETE("/api/rooms/{roomId}/requests/{userId}")
    suspend fun declineJoinRequest(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("userId") userId: Long,
    ): Response<GenericOkResponse>

    @DELETE("/api/rooms/{roomId}/members/{userId}")
    suspend fun removeRoomMember(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("userId") userId: Long,
    ): Response<GenericOkResponse>

    @PATCH("/api/rooms/{roomId}/members/{userId}")
    suspend fun updateRoomMember(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
        @Path("userId") userId: Long,
        @Body body: MemberActionRequest,
    ): Response<GenericOkResponse>

    @POST("/api/rooms/{roomId}/leave")
    suspend fun leaveRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<GenericOkResponse>

    @DELETE("/api/rooms/{roomId}")
    suspend fun deleteRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<GenericOkResponse>

    @GET("/api/search/messages")
    suspend fun searchMessages(
        @Header("Authorization") authorization: String,
        @retrofit2.http.Query("q") query: String,
        @retrofit2.http.Query("scope") scope: String = "all",
        @retrofit2.http.Query("targetId") targetId: Long? = null,
        @retrofit2.http.Query("limit") limit: Int = 40,
    ): Response<SearchResponse>

    @POST("/api/pins")
    suspend fun pinMessage(
        @Header("Authorization") authorization: String,
        @Body body: PinMessageRequest,
    ): Response<GenericOkResponse>

    @DELETE("/api/pins")
    suspend fun unpinMessage(
        @Header("Authorization") authorization: String,
        @Body body: UnpinRequest,
    ): Response<GenericOkResponse>

    @DELETE("/api/dialogs/{userId}")
    suspend fun deleteDialog(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<GenericOkResponse>

    @PATCH("/api/chat-prefs")
    suspend fun updateChatPrefs(
        @Header("Authorization") authorization: String,
        @Body body: ChatPrefsRequest,
    ): Response<GenericOkResponse>

    @POST("/api/rooms/{roomId}/join")
    suspend fun joinRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<GenericOkResponse>

    @POST("/api/rooms/{roomId}/request-join")
    suspend fun requestJoinRoom(
        @Header("Authorization") authorization: String,
        @Path("roomId") roomId: Long,
    ): Response<GenericOkResponse>

    @POST("/api/auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
    ): Response<GenericOkResponse>

    @GET("/api/notifications/status")
    suspend fun notificationStatus(
        @Header("Authorization") authorization: String,
    ): Response<NotificationStatusResponse>

    @POST("/api/notifications/fcm")
    suspend fun registerFcmToken(
        @Header("Authorization") authorization: String,
        @Body body: RegisterFcmRequest,
    ): Response<GenericOkResponse>

    @DELETE("/api/notifications/subscriptions")
    suspend fun unsubscribeAll(
        @Header("Authorization") authorization: String,
    ): Response<GenericOkResponse>

    @POST("/api/notifications/test")
    suspend fun testNotification(
        @Header("Authorization") authorization: String,
    ): Response<GenericOkResponse>

    @GET("/api/admin/overview")
    suspend fun adminOverview(
        @Header("Authorization") authorization: String,
    ): Response<AdminOverviewResponse>

    @GET("/api/admin/users")
    suspend fun adminUsers(
        @Header("Authorization") authorization: String,
    ): Response<AdminUsersResponse>

    @POST("/api/admin/users")
    suspend fun createAdminUser(
        @Header("Authorization") authorization: String,
        @Body body: AdminCreateUserRequest,
    ): Response<AdminCreateUserResponse>

    @PATCH("/api/admin/users/{userId}")
    suspend fun updateAdminUserRole(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Body body: AdminToggleRoleRequest,
    ): Response<GenericOkResponse>

    @PATCH("/api/admin/users/{userId}/password")
    suspend fun updateAdminUserPassword(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
        @Body body: AdminPasswordRequest,
    ): Response<GenericOkResponse>

    @DELETE("/api/admin/users/{userId}")
    suspend fun deleteAdminUser(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<GenericOkResponse>

    @GET("/api/e2ee/status")
    suspend fun e2eeStatus(
        @Header("Authorization") authorization: String,
    ): Response<E2EEStatusResponse>

    @POST("/api/e2ee/device-keys")
    suspend fun registerE2EEDeviceKeys(
        @Header("Authorization") authorization: String,
        @Body body: E2EERegisterDeviceKeysRequest,
    ): Response<E2EEDeviceKeysResponse>

    @POST("/api/e2ee/one-time-prekeys")
    suspend fun uploadE2EEOneTimePreKeys(
        @Header("Authorization") authorization: String,
        @Body body: E2EEUploadOneTimePreKeysRequest,
    ): Response<E2EEDeviceKeysResponse>

    @GET("/api/e2ee/users/{userId}/status")
    suspend fun e2eePeerStatus(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<E2EEPeerStatusResponse>

    @GET("/api/e2ee/users/{userId}/bundle")
    suspend fun e2eePreKeyBundle(
        @Header("Authorization") authorization: String,
        @Path("userId") userId: Long,
    ): Response<E2EEPreKeyBundleResponse>

}
