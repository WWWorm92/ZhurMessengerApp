package com.pulsemessenger.android.core.call

import android.content.Context
import android.media.AudioManager
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
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
        private const val VERSION = "webrtc-v4-sdp-ice-2026-07-22"
    }

    private val initialized = AtomicBoolean(false)

    private var factory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null

    private val pendingRemoteIce = mutableListOf<CallIcePayload>()
    private var remoteDescriptionSet = false
    private var speakerEnabled = false

    var onIceCandidate: ((CallIcePayload) -> Unit)? = null
    var onStatusChanged: ((String) -> Unit)? = null

    fun startAsCaller(
        onLocalOffer: (String) -> Unit,
    ) {
        android.util.Log.d(TAG, "manager version=$VERSION startAsCaller")
        resetPeerConnectionOnly()
        preparePeerConnection(addLocalAudio = true)
        onStatusChanged?.invoke("Соединяем...")

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
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
        onLocalAnswer: (String) -> Unit,
    ) {
        android.util.Log.d(TAG, "manager version=$VERSION startAsCallee")
        resetPeerConnectionOnly()
        preparePeerConnection(addLocalAudio = false)
        onStatusChanged?.invoke("Принимаем звонок...")

        val safeOffer = normalizeRemoteSdp(remoteOffer)

        android.util.Log.d(
            "WEBRTC_CALL",
            "remote offer length=${safeOffer.length} hasAudio=${safeOffer.contains("m=audio")} hasExtmapMixed=${safeOffer.contains("a=extmap-allow-mixed")} start=${safeOffer.take(160)}"
        )

        if (!safeOffer.trimStart().startsWith("v=0")) {
            android.util.Log.e("WEBRTC_CALL", "invalid remote offer: ${safeOffer.take(240)}")
            onStatusChanged?.invoke("Ошибка remote offer: invalid SDP")
            return
        }

        if (!safeOffer.contains("m=audio")) {
            android.util.Log.e("WEBRTC_CALL", "remote offer has no audio m-line: ${safeOffer.take(500)}")
            onStatusChanged?.invoke("Ошибка remote offer: no audio")
            return
        }

        setRemoteOfferInternal(
            sdp = safeOffer,
            onLocalAnswer = onLocalAnswer,
            retried = false,
        )
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
                android.util.Log.d("WEBRTC_CALL", "remote offer set successfully")

                ensureLocalAudioTrack()
                flushPendingRemoteIce()

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
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
            "WEBRTC_CALL",
            "remote answer length=${safeAnswer.length} hasAudio=${safeAnswer.contains("m=audio")} hasExtmapMixed=${safeAnswer.contains("a=extmap-allow-mixed")} start=${safeAnswer.take(160)}"
        )

        if (!safeAnswer.trimStart().startsWith("v=0")) {
            android.util.Log.e("WEBRTC_CALL", "invalid remote answer: ${safeAnswer.take(240)}")
            onStatusChanged?.invoke("Ошибка remote answer: invalid SDP")
            return
        }

        val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, safeAnswer)
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                flushPendingRemoteIce()
                onStatusChanged?.invoke("Звонок активен")
            }

            override fun onSetFailure(error: String?) {
                android.util.Log.e("WEBRTC_CALL", "setRemoteDescription answer failed: $error")
                onStatusChanged?.invoke("Ошибка remote answer: ${error ?: "unknown"}")
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

    fun end() {
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
            peerConnection = null

            audioTrack?.dispose()
            audioTrack = null

            audioSource?.dispose()
            audioSource = null

            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            audioManager = null
            speakerEnabled = false
        }

        pendingRemoteIce.clear()
        remoteDescriptionSet = false
        onStatusChanged?.invoke("Звонок завершён")
    }

    private fun preparePeerConnection(addLocalAudio: Boolean) {
        ensureFactory()

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = speakerEnabled

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
                .createIceServer(),

            PeerConnection.IceServer.builder("turn:turn.zhuravlynoe.ru:3478?transport=udp")
                .setUsername("pulse")
                .setPassword("Reich1934")
                .createIceServer(),

            PeerConnection.IceServer.builder("turn:turn.zhuravlynoe.ru:3478?transport=tcp")
                .setUsername("pulse")
                .setPassword("Reich1934")
                .createIceServer(),
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = factory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    android.util.Log.d("WEBRTC_CALL", "signaling=$state")
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    android.util.Log.d("WEBRTC_CALL", "iceGathering=$state")
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) = Unit
                override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) = Unit

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    android.util.Log.d("WEBRTC_CALL", "iceConnection=$state")

                    when (state) {
                        PeerConnection.IceConnectionState.CHECKING -> onStatusChanged?.invoke("Соединяем...")
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> onStatusChanged?.invoke("Звонок активен")
                        PeerConnection.IceConnectionState.DISCONNECTED -> onStatusChanged?.invoke("Соединение потеряно, восстанавливаем...")
                        PeerConnection.IceConnectionState.FAILED -> onStatusChanged?.invoke("Не удалось соединиться. Можно подождать или завершить звонок")
                        PeerConnection.IceConnectionState.CLOSED -> onStatusChanged?.invoke("Звонок завершён")
                        else -> Unit
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate?) {
                    candidate ?: return

                    android.util.Log.d(
                        "WEBRTC_CALL",
                        "local ICE mid=${candidate.sdpMid} index=${candidate.sdpMLineIndex}"
                    )

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
        runCatching {
            peerConnection?.close()
            peerConnection?.dispose()
        }

        peerConnection = null
        remoteDescriptionSet = false
        pendingRemoteIce.clear()

        runCatching {
            audioTrack?.dispose()
            audioSource?.dispose()
        }

        audioTrack = null
        audioSource = null
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
            .createPeerConnectionFactory()
    }

    private open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}