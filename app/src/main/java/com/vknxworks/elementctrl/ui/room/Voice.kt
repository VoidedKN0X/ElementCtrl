package com.vknxworks.elementctrl.ui.room

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import com.vknxworks.elementctrl.matrix.MatrixSession
import com.vknxworks.elementctrl.matrix.MediaFiles
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A finished recording ready to send. */
data class VoiceClip(val file: File, val durationMs: Long, val waveform: List<Float>)

/** Records voice messages as Ogg/Opus (what Element uses) and samples the loudness for the waveform. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private var sampler: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val samples = ArrayList<Float>()

    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    /** Recent loudness levels (0–1), for the live bars while recording. */
    val levels: StateFlow<List<Float>> = _levels.asStateFlow()

    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    fun start(): Boolean {
        val dir = File(context.cacheDir, "upload").apply { mkdirs() }
        val out = File(dir, "voice-${System.currentTimeMillis()}.ogg")
        return runCatching {
            val r = MediaRecorder(context).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioSamplingRate(48_000)
                setAudioEncodingBitRate(24_000)
                setAudioChannels(1)
                setOutputFile(out.absolutePath)
                prepare()
                start()
            }
            recorder = r
            file = out
            startedAt = SystemClock.elapsedRealtime()
            samples.clear()
            sampler = scope.launch {
                while (isActive) {
                    val amp = runCatching { r.maxAmplitude }.getOrDefault(0)
                    // Perceived loudness is roughly logarithmic; map 0..32767 onto 0..1.
                    val level = (kotlin.math.ln(1.0 + amp) / kotlin.math.ln(32768.0)).toFloat().coerceIn(0f, 1f)
                    samples += level
                    _levels.value = samples.takeLast(48)
                    _elapsed.value = SystemClock.elapsedRealtime() - startedAt
                    delay(100)
                }
            }
            true
        }.getOrElse {
            out.delete()
            false
        }
    }

    /** Stops and returns the clip, or null if it was too short to be useful. */
    fun stop(): VoiceClip? {
        sampler?.cancel()
        val r = recorder ?: return null
        val duration = SystemClock.elapsedRealtime() - startedAt
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        recorder = null
        val f = file ?: return null
        // Hand the file over: cancel() must not delete a clip that is being sent.
        file = null
        if (!ok || duration < 700) { f.delete(); return null }
        return VoiceClip(f, duration, downsample(samples, 100))
    }

    fun cancel() {
        sampler?.cancel()
        runCatching { recorder?.stop() }
        recorder?.release()
        recorder = null
        file?.delete()
        _levels.value = emptyList()
        _elapsed.value = 0
    }

    private fun downsample(src: List<Float>, n: Int): List<Float> {
        if (src.isEmpty()) return List(n) { 0f }
        if (src.size <= n) return src
        val step = src.size.toFloat() / n
        return List(n) { i -> src.subList((i * step).toInt(), ((i + 1) * step).toInt().coerceAtMost(src.size)).maxOrNull() ?: 0f }
    }
}

data class PlayerState(val key: String? = null, val playing: Boolean = false, val positionMs: Long = 0, val durationMs: Long = 0, val loading: Boolean = false)

/** Plays one voice message or audio clip at a time, inline in the chat. */
object AudioPlayer {
    private var player: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun toggle(context: Context, session: MatrixSession, key: String, sourceJson: String, filename: String, mime: String) {
        val s = _state.value
        val p = player
        if (s.key == key && p != null) {
            if (p.isPlaying) { p.pause(); _state.value = s.copy(playing = false) } else { p.start(); _state.value = s.copy(playing = true); tick() }
            return
        }
        stop()
        _state.value = PlayerState(key = key, loading = true)
        scope.launch {
            runCatching {
                val file = MediaFiles.download(context, session, sourceJson, filename.ifBlank { "voice.ogg" }, mime)
                MediaPlayer().apply {
                    setDataSource(file.absolutePath)
                    prepare()
                    setOnCompletionListener { _state.value = _state.value.copy(playing = false, positionMs = 0); ticker?.cancel() }
                    start()
                }
            }.onSuccess { mp ->
                if (_state.value.key != key) { mp.release(); return@onSuccess }
                player = mp
                _state.value = PlayerState(key = key, playing = true, durationMs = mp.duration.toLong())
                tick()
            }.onFailure { _state.value = PlayerState() }
        }
    }

    fun seek(fraction: Float) {
        val p = player ?: return
        p.seekTo((p.duration * fraction).toInt())
        _state.value = _state.value.copy(positionMs = p.currentPosition.toLong())
    }

    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player ?: break
                _state.value = _state.value.copy(positionMs = p.currentPosition.toLong())
                if (!p.isPlaying) break
                delay(100)
            }
        }
    }

    fun stop() {
        ticker?.cancel()
        runCatching { player?.stop() }
        player?.release()
        player = null
        _state.value = PlayerState()
    }
}
