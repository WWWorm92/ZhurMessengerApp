package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSink
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

data class CallIcePayload(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val candidate: String,
)

class WebRtcCallManager(
    private val context: Context,
) {
    companion object {
        private const val TAG = "WEBRTC_CALL"
        private const val VERSION = "webrtc-v9-default-network-fix-2026-09-16"
        private const val NETWORK_SETTLE_DELAY_MS = 400L
        private const val RECOVERY_RETRY_DELAY_MS = 2_000L
        private const val MAX_RECOVERY_ATTEMPTS = 3
    }

    private val initialized = AtomicBoolean(false)

    private val eglBase: EglBase = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var audioManager: AudioManager? = null
    private var videoCallEnabled = false
    private var cameraEnabled = true
    private val localVideoSinks = CopyOnWriteArraySet<VideoSink>()
    private val remoteVideoSinks = CopyOnWriteArraySet<VideoSink>()

    private val pendingRemoteIce = mutableListOf<CallIcePayload>()
    private var remoteDescriptionSet = false
    private var speakerEnabled = false
    private val connectionHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var lastIceConnectionState: PeerConnection.IceConnectionState? = null
    private var disconnectWarningTask: Runnable? = null
    private var recoveryDispatchTask: Runnable? = null
    private var recoveryRetryTask: Runnable? = null
    private var restartOfferInFlight = false
    private var lastRecoveryRequestAt = 0L
    private var recoveryPending = false
    private var recoveryAttempt = 0
    private var lastNetworkSignature = ""
    private var lastNetworkChangeAt = 0L
    private var networkCallbackRegistered = false
    @Volatile private var trackedDefaultNetwork: Network? = null
    @Volatile private var trackedNetworkState = NetworkState("unknown", false)
    private var lastRecoveryWaitLog = ""
    private var lastRecoveryWaitLogAt = 0L
    private val connectivityManager: ConnectivityManager? by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = connectivityManager?.getNetworkCapabilities(network)
            updateTrackedDefaultNetwork("available", network, caps)
        }

        override fun onLost(network: Network) {
            // onLost belongs to the Network object that actually disappeared.
            // Never re-read activeNetwork here: during Wi-Fi -> LTE Android can
            // temporarily expose stale/default information and make LTE look like Wi-Fi.
            if (trackedDefaultNetwork == network) {
                trackedDefaultNetwork = null
                updateTrackedNetworkState("lost", NetworkState("none", false))
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            // Ignore late capability callbacks from the previous default network.
            if (trackedDefaultNetwork == null || trackedDefaultNetwork == network) {
                trackedDefaultNetwork = network
                updateTrackedNetworkState("capabilities", networkStateFromCapabilities(capabilities))
            }
        }
    }
    private var lastRemoteOfferSdp = ""
    private var lastLocalAnswerSdp = ""
    private var lastRemoteAnswerSdp = ""
    private var remoteAnswerInFlight = false
    private var configuredIceServers: List<PeerConnection.IceServer> = listOf(
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
    )

    var onIceCandidate: ((CallIcePayload) -> Unit)? = null
    var onDiagnostic: ((String, String) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onRecoveryNeeded: (() -> Unit)? = null

    fun startAsCaller(
        videoEnabled: Boolean = false,
        onLocalOffer: (String) -> Unit,
    ) {
        android.util.Log.d(TAG, "manager version=$VERSION startAsCaller video=$videoEnabled")
        resetPeerConnectionOnly()
        videoCallEnabled = videoEnabled
        cameraEnabled = true
        preparePeerConnection(addLocalAudio = true, addLocalVideo = videoEnabled)
        onStatusChanged?.invoke("Соединяем...")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoEnabled.toString()))
        }

        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                val safeSdp = normalizeLocalSdp(description.description)
                val safeDescription = SessionDescription(SessionDescription.Type.OFFER, safeSdp)

                android.util.Log.d(
                    "WEBRTC_CALL",
                    "local offer length=${safeSdp.length} hasAudio=${safeSdp.contains("m=audio")} hasExtmapMixed=${safeSdp.contains("a=extmap-allow-mixed")} start=${safeSdp.take(120)}"
                )

                peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        onLocalOffer(safeSdp)
                    }

                    override fun onSetFailure(error: String?) {
                        android.util.Log.e("WEBRTC_CALL", "setLocalDescription offer failed: $error")
                        onStatusChanged?.invoke("Ошибка локального SDP: ${error ?: "unknown"}")
                    }
                }, safeDescription)
            }

            override fun onCreateFailure(error: String?) {
                android.util.Log.e("WEBRTC_CALL", "createOffer failed: $error")
                onStatusChanged?.invoke("Ошибка offer: ${error ?: "unknown"}")
            }
        }, constraints)
    }

    fun startAsCallee(
        remoteOffer: String,
        videoEnabled: Boolean = false,
        onLocalAnswer: (String) -> Unit,
    ) {
        android.util.Log.d(TAG, "manager version=$VERSION startAsCallee video=$videoEnabled")
        resetPeerConnectionOnly()
        videoCallEnabled = videoEnabled
        cameraEnabled = true
        preparePeerConnection(addLocalAudio = false, addLocalVideo = false)
        onStatusChanged?.invoke("Принимаем звонок...")

        handleRemoteOffer(remoteOffer, onLocalAnswer)
    }

    fun handleRemoteOffer(
        remoteOffer: String,
        onLocalAnswer: (String) -> Unit,
    ) {
        val pc = peerConnection
        if (pc == null) {
            onStatusChanged?.invoke("Нет активного PeerConnection")
            return
        }

        val safeOffer = normalizeRemoteSdp(remoteOffer)

        android.util.Log.d(
            TAG,
            "remote offer length=${safeOffer.length} hasAudio=${safeOffer.contains("m=audio")} hasExtmapMixed=${safeOffer.contains("a=extmap-allow-mixed")} start=${safeOffer.take(160)}"
        )

        if (!safeOffer.trimStart().startsWith("v=0")) {
            android.util.Log.e(TAG, "invalid remote offer: ${safeOffer.take(240)}")
            onStatusChanged?.invoke("Ошибка remote offer: invalid SDP")
            return
        }

        if (!safeOffer.contains("m=audio")) {
            android.util.Log.e(TAG, "remote offer has no audio m-line: ${safeOffer.take(500)}")
            onStatusChanged?.invoke("Ошибка remote offer: no audio")
            return
        }

        // call:resume can replay an offer whose answer was lost in transit.
        // If this exact offer was already answered and the PC is stable,
        // resend the cached answer instead of rebuilding the whole connection.
        if (
            safeOffer == lastRemoteOfferSdp &&
            lastLocalAnswerSdp.isNotBlank() &&
            pc.signalingState() == PeerConnection.SignalingState.STABLE
        ) {
            android.util.Log.d(TAG, "duplicate remote offer -> replay cached answer")
            onLocalAnswer(lastLocalAnswerSdp)
            return
        }

        onStatusChanged?.invoke("Восстанавливаем соединение...")
        // New ICE candidates belong to this offer. Queue any candidates that
        // arrive before setRemoteDescription completes instead of applying
        // them against the previous ICE generation.
        remoteDescriptionSet = false

        setRemoteOfferInternal(
            sdp = safeOffer,
            onLocalAnswer = onLocalAnswer,
            retried = false,
        )
    }

    fun hasPeerConnection(): Boolean = peerConnection != null

    fun isMediaConnected(): Boolean =
        lastIceConnectionState == PeerConnection.IceConnectionState.CONNECTED ||
            lastIceConnectionState == PeerConnection.IceConnectionState.COMPLETED

    fun restartAsCaller(
        onLocalOffer: (String) -> Unit,
    ) {
        val pc = peerConnection
        if (pc == null || restartOfferInFlight) {
            return
        }

        if (pc.signalingState() != PeerConnection.SignalingState.STABLE) {
            android.util.Log.d(TAG, "ICE restart deferred signaling=${pc.signalingState()}")
            connectionHandler.postDelayed(
                {
                    if (
                        peerConnection === pc &&
                        lastIceConnectionState != PeerConnection.IceConnectionState.CLOSED
                    ) {
                        restartAsCaller(onLocalOffer)
                    }
                },
                750L,
            )
            return
        }

        restartOfferInFlight = true
        onStatusChanged?.invoke("Восстанавливаем соединение...")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoCallEnabled.toString()))
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription) {
                val safeSdp = normalizeLocalSdp(description.description)
                val safeDescription = SessionDescription(SessionDescription.Type.OFFER, safeSdp)

                pc.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        restartOfferInFlight = false
                        remoteDescriptionSet = false
                        pendingRemoteIce.clear()
                        android.util.Log.d(TAG, "ICE restart offer created length=${safeSdp.length}")
                        onLocalOffer(safeSdp)
                    }

                    override fun onSetFailure(error: String?) {
                        restartOfferInFlight = false
                        android.util.Log.e(TAG, "setLocalDescription ICE restart failed: $error")
                        onStatusChanged?.invoke("Не удалось восстановить соединение")
                    }
                }, safeDescription)
            }

            override fun onCreateFailure(error: String?) {
                restartOfferInFlight = false
                android.util.Log.e(TAG, "create ICE restart offer failed: $error")
                onStatusChanged?.invoke("Не удалось восстановить соединение")
            }
        }, constraints)
    }

    private fun setRemoteOfferInternal(
        sdp: String,
        onLocalAnswer: (String) -> Unit,
        retried: Boolean,
    ) {
        val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, sdp)

        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                lastRemoteOfferSdp = sdp
                android.util.Log.d(TAG, "remote offer set successfully")

                ensureLocalAudioTrack()
                if (videoCallEnabled) {
                    ensureLocalVideoTrack()
                }
                flushPendingRemoteIce()

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", videoCallEnabled.toString()))
                }

                peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(description: SessionDescription) {
                        val safeSdp = normalizeLocalSdp(description.description)
                        val safeDescription = SessionDescription(SessionDescription.Type.ANSWER, safeSdp)

                        android.util.Log.d(
                            "WEBRTC_CALL",
                            "local answer length=${safeSdp.length} hasAudio=${safeSdp.contains("m=audio")} hasExtmapMixed=${safeSdp.contains("a=extmap-allow-mixed")} start=${safeSdp.take(120)}"
                        )

                        peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                lastLocalAnswerSdp = safeSdp
                                onLocalAnswer(safeSdp)
                            }

                            override fun onSetFailure(error: String?) {
                                android.util.Log.e("WEBRTC_CALL", "setLocalDescription answer failed: $error")
                                onStatusChanged?.invoke("Ошибка локального SDP: ${error ?: "unknown"}")
                            }
                        }, safeDescription)
                    }

                    override fun onCreateFailure(error: String?) {
                        android.util.Log.e("WEBRTC_CALL", "createAnswer failed: $error")
                        onStatusChanged?.invoke("Ошибка answer: ${error ?: "unknown"}")
                    }
                }, constraints)
            }

            override fun onSetFailure(error: String?) {
                android.util.Log.e("WEBRTC_CALL", "setRemoteDescription offer failed: $error")

                // Часть Android WebRTC сборок падает на session-level a=extmap-allow-mixed
                // и отдаёт бесполезное "SessionDescription is NULL". Пробуем один раз без этой строки.
                if (!retried && sdp.contains("a=extmap-allow-mixed")) {
                    val fallbackSdp = sdp
                        .lineSequence()
                        .filterNot { it.trim() == "a=extmap-allow-mixed" }
                        .joinToString("\r\n")
                        .trimEnd() + "\r\n"

                    android.util.Log.w(
                        "WEBRTC_CALL",
                        "retry remote offer without extmap-allow-mixed length=${fallbackSdp.length}"
                    )

                    setRemoteOfferInternal(
                        sdp = fallbackSdp,
                        onLocalAnswer = onLocalAnswer,
                        retried = true,
                    )
                    return
                }

                onStatusChanged?.invoke("Ошибка remote offer: ${error ?: "unknown"}")
            }
        }, remoteDescription)
    }

    fun handleRemoteAnswer(remoteAnswer: String) {
        val safeAnswer = normalizeRemoteSdp(remoteAnswer)

        android.util.Log.d(
            TAG,
            "remote answer length=${safeAnswer.length} hasAudio=${safeAnswer.contains("m=audio")} signaling=${peerConnection?.signalingState()}"
        )

        if (!safeAnswer.trimStart().startsWith("v=0")) {
            android.util.Log.e(TAG, "invalid remote answer")
            return
        }

        val pc = peerConnection ?: return
        val signalingState = pc.signalingState()

        // ANSWER is legal only for our outstanding local OFFER. Replayed or
        // delayed answers after an already completed negotiation are harmless.
        if (signalingState == PeerConnection.SignalingState.STABLE) {
            android.util.Log.d(TAG, "stale/duplicate remote answer ignored; signaling=STABLE")
            restartOfferInFlight = false
            return
        }

        if (signalingState != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            android.util.Log.w(TAG, "remote answer ignored in signaling=$signalingState")
            return
        }

        // setRemoteDescription is async. Without this guard two answers can
        // both observe HAVE_LOCAL_OFFER before the first one moves us to STABLE.
        if (remoteAnswerInFlight) {
            android.util.Log.d(TAG, "remote answer ignored: another answer is already in flight")
            return
        }

        remoteAnswerInFlight = true
        val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, safeAnswer)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteAnswerInFlight = false
                remoteDescriptionSet = true
                lastRemoteAnswerSdp = safeAnswer
                restartOfferInFlight = false
                flushPendingRemoteIce()
                // The ICE callback is the source of truth for media health.
                if (isMediaConnected()) {
                    onStatusChanged?.invoke("Звонок активен")
                } else {
                    onStatusChanged?.invoke("Соединяем...")
                }
            }

            override fun onSetFailure(error: String?) {
                remoteAnswerInFlight = false

                // Another valid answer may have completed while this async
                // operation was queued. Never turn that into a user-visible SDP error.
                if (pc.signalingState() == PeerConnection.SignalingState.STABLE) {
                    android.util.Log.d(TAG, "remote answer became stale while applying; ignored")
                    return
                }

                android.util.Log.e(TAG, "setRemoteDescription answer failed: $error")
                onStatusChanged?.invoke("Восстанавливаем соединение...")
                requestRecovery()
            }
        }, remoteDescription)
    }

    fun addRemoteIce(payload: CallIcePayload) {
        if (payload.candidate.isBlank()) return

        if (!remoteDescriptionSet) {
            pendingRemoteIce += payload
            android.util.Log.d(
                "WEBRTC_CALL",
                "remote ICE queued mid=${payload.sdpMid} index=${payload.sdpMLineIndex}"
            )
            return
        }

        addRemoteIceNow(payload)
    }

    fun setMuted(muted: Boolean) {
        audioTrack?.setEnabled(!muted)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        speakerEnabled = enabled
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = enabled
    }

    fun isSpeakerEnabled(): Boolean = speakerEnabled

    fun isVideoCall(): Boolean = videoCallEnabled

    fun setCameraEnabled(enabled: Boolean) {
        cameraEnabled = enabled
        localVideoTrack?.setEnabled(enabled)
    }

    fun isCameraEnabled(): Boolean = cameraEnabled

    fun switchCamera() {
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontCamera: Boolean) {
                android.util.Log.d(TAG, "camera switched front=$isFrontCamera")
            }

            override fun onCameraSwitchError(errorDescription: String?) {
                android.util.Log.w(TAG, "camera switch failed: $errorDescription")
            }
        })
    }

    fun bindVideoRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setEnableHardwareScaler(true)
        renderer.setMirror(local)
        if (local) {
            renderer.setZOrderMediaOverlay(true)
            localVideoSinks += renderer
            localVideoTrack?.addSink(renderer)
        } else {
            remoteVideoSinks += renderer
            remoteVideoTrack?.addSink(renderer)
        }
    }

    fun unbindVideoRenderer(renderer: SurfaceViewRenderer, local: Boolean) {
        if (local) {
            localVideoTrack?.removeSink(renderer)
            localVideoSinks -= renderer
        } else {
            remoteVideoTrack?.removeSink(renderer)
            remoteVideoSinks -= renderer
        }
    }

    private fun cancelDisconnectWarning() {
        disconnectWarningTask?.let(connectionHandler::removeCallbacks)
        disconnectWarningTask = null
    }

    private fun scheduleDisconnectWarning() {
        cancelDisconnectWarning()
        val task = Runnable {
            disconnectWarningTask = null
            if (lastIceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                requestRecovery()
            }
        }
        disconnectWarningTask = task
        connectionHandler.postDelayed(task, 2_500L)
    }

    private fun requestRecovery() {
        if (isMediaConnected()) return

        recoveryPending = true
        onStatusChanged?.invoke("Соединение потеряно, ожидаем новую сеть...")
        scheduleRecoveryWhenNetworkStable()
    }

    private fun scheduleRecoveryWhenNetworkStable() {
        recoveryDispatchTask?.let(connectionHandler::removeCallbacks)

        val now = System.currentTimeMillis()
        val snapshot = currentNetworkState()
        if (!snapshot.validated) {
            logRecoveryWaitOnce("reason=not_validated type=${snapshot.type}")
            val task = Runnable {
                recoveryDispatchTask = null
                if (recoveryPending && !isMediaConnected()) {
                    scheduleRecoveryWhenNetworkStable()
                }
            }
            recoveryDispatchTask = task
            connectionHandler.postDelayed(task, 1_000L)
            return
        }

        // Android may already report CELLULAR while libwebrtc's internal network
        // monitor still has the old Wi-Fi interface. Give it time to observe the
        // new default network before creating an ICE-restart offer.
        val sinceNetworkChange = now - lastNetworkChangeAt
        val delay = (NETWORK_SETTLE_DELAY_MS - sinceNetworkChange).coerceAtLeast(0L)
        val targetSignature = "${snapshot.type}:${snapshot.validated}"

        diagnostic(
            "RECOVERY_SCHEDULED",
            "delayMs=$delay type=${snapshot.type} attempt=${recoveryAttempt + 1}",
        )

        val task = Runnable {
            recoveryDispatchTask = null
            if (!recoveryPending || isMediaConnected()) return@Runnable

            val ready = currentNetworkState()
            val readySignature = "${ready.type}:${ready.validated}"
            if (!ready.validated || readySignature != targetSignature) {
                scheduleRecoveryWhenNetworkStable()
                return@Runnable
            }

            val nowRun = System.currentTimeMillis()
            if (nowRun - lastRecoveryRequestAt < 1_500L) {
                scheduleRecoveryWhenNetworkStable()
                return@Runnable
            }

            lastRecoveryRequestAt = nowRun
            recoveryPending = false
            recoveryAttempt += 1
            diagnostic("RECOVERY_TRIGGERED", "type=${ready.type} attempt=$recoveryAttempt")
            onStatusChanged?.invoke("Восстанавливаем соединение...")
            onRecoveryNeeded?.invoke()
            scheduleRecoveryRetry()
        }
        recoveryDispatchTask = task
        connectionHandler.postDelayed(task, delay)
    }

    private fun scheduleRecoveryRetry() {
        recoveryRetryTask?.let(connectionHandler::removeCallbacks)
        if (recoveryAttempt >= MAX_RECOVERY_ATTEMPTS) return

        val task = Runnable {
            recoveryRetryTask = null
            if (isMediaConnected() || peerConnection == null) return@Runnable
            diagnostic("RECOVERY_RETRY", "nextAttempt=${recoveryAttempt + 1}")
            recoveryPending = true
            scheduleRecoveryWhenNetworkStable()
        }
        recoveryRetryTask = task
        connectionHandler.postDelayed(task, RECOVERY_RETRY_DELAY_MS)
    }

    private data class NetworkState(val type: String, val validated: Boolean)

    private fun networkStateFromCapabilities(caps: NetworkCapabilities?): NetworkState {
        if (caps == null) return NetworkState("unknown", false)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            else -> "OTHER"
        }
        return NetworkState(type, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
    }

    private fun currentNetworkState(): NetworkState {
        return try {
            val cm = connectivityManager ?: return NetworkState("unknown", false)
            val tracked = trackedDefaultNetwork
            if (networkCallbackRegistered && tracked != null) {
                val fresh = networkStateFromCapabilities(cm.getNetworkCapabilities(tracked))
                if (fresh.type != "unknown") {
                    trackedNetworkState = fresh
                    return fresh
                }
                return trackedNetworkState
            }
            if (networkCallbackRegistered) return trackedNetworkState
            val network = cm.activeNetwork ?: return NetworkState("none", false)
            networkStateFromCapabilities(cm.getNetworkCapabilities(network))
        } catch (_: Exception) {
            trackedNetworkState
        }
    }

    private fun updateTrackedDefaultNetwork(source: String, network: Network, caps: NetworkCapabilities?) {
        trackedDefaultNetwork = network
        updateTrackedNetworkState(source, networkStateFromCapabilities(caps))
    }

    private fun updateTrackedNetworkState(source: String, state: NetworkState) {
        trackedNetworkState = state
        connectionHandler.post {
            val signature = "${state.type}:${state.validated}"
            if (signature == lastNetworkSignature) return@post
            val previous = lastNetworkSignature.ifBlank { "unknown" }
            lastNetworkSignature = signature
            lastNetworkChangeAt = System.currentTimeMillis()
            diagnostic("NETWORK_CHANGE", "source=$source from=$previous to=$signature")
            if (recoveryPending && state.validated) scheduleRecoveryWhenNetworkStable()
        }
    }

    private fun logRecoveryWaitOnce(details: String) {
        val now = System.currentTimeMillis()
        if (details == lastRecoveryWaitLog && now - lastRecoveryWaitLogAt < 1_000L) return
        lastRecoveryWaitLog = details
        lastRecoveryWaitLogAt = now
        diagnostic("RECOVERY_WAIT_NETWORK", details)
    }

    private fun startNetworkMonitoring() {
        if (networkCallbackRegistered) return
        val cm = connectivityManager ?: return
        runCatching {
            val initial = cm.activeNetwork
            trackedDefaultNetwork = initial
            trackedNetworkState = networkStateFromCapabilities(initial?.let(cm::getNetworkCapabilities))
            lastNetworkSignature = "${trackedNetworkState.type}:${trackedNetworkState.validated}"
            lastNetworkChangeAt = System.currentTimeMillis()
            cm.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure {
            android.util.Log.w(TAG, "default network callback registration failed", it)
        }
    }

    private fun stopNetworkMonitoring() {
        if (!networkCallbackRegistered) return
        runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
        trackedDefaultNetwork = null
        trackedNetworkState = NetworkState("unknown", false)
        lastRecoveryWaitLog = ""
        lastRecoveryWaitLogAt = 0L
    }

    fun end() {
        cancelDisconnectWarning()
        recoveryDispatchTask?.let(connectionHandler::removeCallbacks)
        recoveryDispatchTask = null
        recoveryRetryTask?.let(connectionHandler::removeCallbacks)
        recoveryRetryTask = null
        recoveryPending = false
        recoveryAttempt = 0
        stopNetworkMonitoring()
        lastIceConnectionState = null
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            audioTrack?.dispose()
            audioTrack = null

            audioSource?.dispose()
            audioSource = null

            releaseVideoCapture()
            videoCallEnabled = false
            cameraEnabled = true

            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            audioManager = null
            speakerEnabled = false
        }

        pendingRemoteIce.clear()
        remoteDescriptionSet = false
        restartOfferInFlight = false
        remoteAnswerInFlight = false
        lastRecoveryRequestAt = 0L
        lastRemoteOfferSdp = ""
        lastLocalAnswerSdp = ""
        lastRemoteAnswerSdp = ""
        onStatusChanged?.invoke("Звонок завершён")
    }

    private fun networkSnapshot(): String {
        val state = currentNetworkState()
        return "network=${state.type} validated=${state.validated}"
    }

    private fun diagnostic(event: String, details: String = "") {
        val text = listOf(details, networkSnapshot()).filter { it.isNotBlank() }.joinToString(" ")
        android.util.Log.d("WEBRTC_DIAG", "$event $text")
        onDiagnostic?.invoke(event, text)
    }

    fun updateIceServers(servers: List<PeerConnection.IceServer>) {
        if (servers.isEmpty()) return
        configuredIceServers = servers

        val pc = peerConnection ?: return
        val config = PeerConnection.RTCConfiguration(configuredIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        val updated = runCatching { pc.setConfiguration(config) }.getOrDefault(false)
        android.util.Log.d(TAG, "ICE configuration refreshed servers=${servers.size} applied=$updated")
    }

    private fun preparePeerConnection(addLocalAudio: Boolean, addLocalVideo: Boolean) {
        ensureFactory()
        startNetworkMonitoring()

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = speakerEnabled

        val rtcConfig = PeerConnection.RTCConfiguration(configuredIceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    android.util.Log.d("WEBRTC_CALL", "signaling=$state")
                    diagnostic("SIGNALING_STATE", "state=$state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    android.util.Log.d("WEBRTC_CALL", "iceGathering=$state")
                    diagnostic("ICE_GATHERING", "state=$state")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {
                    attachRemoteVideoTrack(receiver?.track())
                }
                override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                    attachRemoteVideoTrack(transceiver?.receiver?.track())
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    android.util.Log.d("WEBRTC_CALL", "iceConnection=$state")
                    lastIceConnectionState = state
                    diagnostic("ICE_CONNECTION", "state=$state")

                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> {
                            if (disconnectWarningTask == null) {
                                onStatusChanged?.invoke("Соединяем...")
                            }
                        }
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            cancelDisconnectWarning()
                            recoveryDispatchTask?.let(connectionHandler::removeCallbacks)
                            recoveryDispatchTask = null
                            recoveryRetryTask?.let(connectionHandler::removeCallbacks)
                            recoveryRetryTask = null
                            recoveryPending = false
                            recoveryAttempt = 0
                            restartOfferInFlight = false
                            onStatusChanged?.invoke("Звонок активен")
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            // DISCONNECTED is often transient while Android
                            // switches network paths. Do not flash a reconnect
                            // warning unless it persists for a few seconds.
                            scheduleDisconnectWarning()
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            cancelDisconnectWarning()
                            requestRecovery()
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            cancelDisconnectWarning()
                            onStatusChanged?.invoke("Звонок завершён")
                        }
                        else -> Unit
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return

                    android.util.Log.d(
                        "WEBRTC_CALL",
                        "local ICE mid=${candidate.sdpMid} index=${candidate.sdpMLineIndex}"
                    )
                    val candidateText = candidate.sdp
                    val candidateType = Regex(" typ ([^ ]+)").find(candidateText)?.groupValues?.getOrNull(1) ?: "unknown"
                    val protocol = candidateText.split(" ").getOrNull(2) ?: "unknown"
                    diagnostic("LOCAL_CANDIDATE", "type=$candidateType protocol=$protocol")

                    onIceCandidate?.invoke(
                        CallIcePayload(
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            candidate = candidate.sdp,
                        )
                    )
                }
            }
        )

        if (peerConnection == null) {
            onStatusChanged?.invoke("Ошибка PeerConnection")
            return
        }

        if (addLocalAudio) {
            ensureLocalAudioTrack()
        }
        if (addLocalVideo) {
            ensureLocalVideoTrack()
        }

        setSpeakerEnabled(speakerEnabled)
    }

    private fun ensureLocalAudioTrack() {
        if (audioTrack != null) return

        val source = factory?.createAudioSource(MediaConstraints())
        val track = factory?.createAudioTrack("pulse_audio_track", source)

        audioSource = source
        audioTrack = track

        if (track != null) {
            track.setEnabled(true)
            val sender = peerConnection?.addTrack(track, listOf("pulse_audio_stream"))
            android.util.Log.d("WEBRTC_CALL", "local audio track added=${sender != null}")
        } else {
            android.util.Log.e("WEBRTC_CALL", "local audio track create failed")
        }
    }

    private fun ensureLocalVideoTrack() {
        if (!videoCallEnabled || localVideoTrack != null) return

        val peerFactory = factory ?: return
        val capturer = createCameraCapturer()
        if (capturer == null) {
            android.util.Log.e(TAG, "no camera capturer available")
            return
        }

        val helper = SurfaceTextureHelper.create("PulseVideoCapture", eglBase.eglBaseContext)
        val source = peerFactory.createVideoSource(false)

        try {
            capturer.initialize(helper, context, source.capturerObserver)
            capturer.startCapture(1280, 720, 24)
        } catch (error: Throwable) {
            android.util.Log.e(TAG, "camera start failed", error)
            runCatching { capturer.dispose() }
            runCatching { source.dispose() }
            runCatching { helper.dispose() }
            return
        }

        val track = peerFactory.createVideoTrack("pulse_video_track", source)
        track.setEnabled(cameraEnabled)
        localVideoSinks.forEach { sink -> track.addSink(sink) }

        videoCapturer = capturer
        surfaceTextureHelper = helper
        videoSource = source
        localVideoTrack = track

        val sender = peerConnection?.addTrack(track, listOf("pulse_video_stream"))
        android.util.Log.d(TAG, "local video track added=${sender != null}")
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = if (Camera2Enumerator.isSupported(context)) {
            Camera2Enumerator(context)
        } else {
            Camera1Enumerator(false)
        }

        val names = enumerator.deviceNames.toList()
        val preferred = names.firstOrNull { enumerator.isFrontFacing(it) }
            ?: names.firstOrNull { enumerator.isBackFacing(it) }
            ?: names.firstOrNull()
            ?: return null

        return enumerator.createCapturer(preferred, null) as? CameraVideoCapturer
    }

    private fun attachRemoteVideoTrack(track: org.webrtc.MediaStreamTrack?) {
        val videoTrack = track as? VideoTrack ?: return
        if (remoteVideoTrack === videoTrack) return

        remoteVideoTrack?.let { oldTrack ->
            remoteVideoSinks.forEach { sink -> oldTrack.removeSink(sink) }
        }
        remoteVideoTrack = videoTrack
        remoteVideoSinks.forEach { sink -> videoTrack.addSink(sink) }
        android.util.Log.d(TAG, "remote video track attached")
    }

    private fun releaseVideoCapture() {
        remoteVideoTrack?.let { track ->
            remoteVideoSinks.forEach { sink -> track.removeSink(sink) }
        }
        localVideoTrack?.let { track ->
            localVideoSinks.forEach { sink -> track.removeSink(sink) }
        }

        runCatching { videoCapturer?.stopCapture() }
        runCatching { videoCapturer?.dispose() }
        runCatching { localVideoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { surfaceTextureHelper?.dispose() }

        videoCapturer = null
        localVideoTrack = null
        remoteVideoTrack = null
        videoSource = null
        surfaceTextureHelper = null
    }

    private fun flushPendingRemoteIce() {
        if (pendingRemoteIce.isEmpty()) return

        val copy = pendingRemoteIce.toList()
        pendingRemoteIce.clear()

        android.util.Log.d("WEBRTC_CALL", "flushing remote ICE count=${copy.size}")
        copy.forEach { addRemoteIceNow(it) }
    }

    private fun addRemoteIceNow(payload: CallIcePayload) {
        val added = peerConnection?.addIceCandidate(
            IceCandidate(
                payload.sdpMid,
                payload.sdpMLineIndex,
                payload.candidate,
            )
        ) ?: false

        if (added) {
            android.util.Log.d(
                "WEBRTC_CALL",
                "remote ICE added=true mid=${payload.sdpMid} index=${payload.sdpMLineIndex} len=${payload.candidate.length}"
            )
        } else {
            android.util.Log.w(
                "WEBRTC_CALL",
                "remote ICE was not added mid=${payload.sdpMid} index=${payload.sdpMLineIndex} len=${payload.candidate.length} remoteDescriptionSet=$remoteDescriptionSet peerConnectionNull=${peerConnection == null}"
            )
        }
    }

    private fun resetPeerConnectionOnly() {
        cancelDisconnectWarning()
        recoveryDispatchTask?.let(connectionHandler::removeCallbacks)
        recoveryDispatchTask = null
        recoveryRetryTask?.let(connectionHandler::removeCallbacks)
        recoveryRetryTask = null
        recoveryPending = false
        recoveryAttempt = 0
        lastIceConnectionState = null
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }

        peerConnection = null
        remoteDescriptionSet = false
        pendingRemoteIce.clear()
        restartOfferInFlight = false
        remoteAnswerInFlight = false
        lastRecoveryRequestAt = 0L
        lastRemoteOfferSdp = ""
        lastLocalAnswerSdp = ""
        lastRemoteAnswerSdp = ""

        runCatching {
            audioTrack?.dispose()
            audioSource?.dispose()
        }
        releaseVideoCapture()

        audioTrack = null
        audioSource = null
        videoCallEnabled = false
        cameraEnabled = true
    }

    private fun normalizeLocalSdp(sdp: String): String {
        // Убираем строку, из-за которой часть Android WebRTC сборок падает на другой стороне
        // с ошибкой "SessionDescription is NULL".
        return removeProblematicSdpLines(normalizeSdpLineEndings(sdp))
    }

    private fun normalizeRemoteSdp(sdp: String): String {
        return removeProblematicSdpLines(normalizeSdpLineEndings(sdp))
    }

    private fun removeProblematicSdpLines(sdp: String): String {
        val lines = sdp
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filterNot { it.trim() == "a=extmap-allow-mixed" }

        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun normalizeSdpLineEndings(raw: String): String {
        var text = raw.trim()

        // На случай, если сигналинг когда-нибудь передаст literal \n вместо настоящих переводов строк.
        if (text.contains("\\n") && !text.contains("\n")) {
            text = text.replace("\\r\\n", "\n")
                .replace("\\n", "\n")
        }

        val lines = text
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .split("\n")
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }

        return lines.joinToString("\r\n") + "\r\n"
    }

    private fun ensureFactory() {
        if (factory != null) return

        if (initialized.compareAndSet(false, true)) {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(context)
                    .createInitializationOptions()
            )
        }

        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            )
            .setVideoDecoderFactory(
                DefaultVideoDecoderFactory(eglBase.eglBaseContext)
            )
            .createPeerConnectionFactory()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}