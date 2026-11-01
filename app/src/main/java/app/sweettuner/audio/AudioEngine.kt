package app.sweettuner.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import app.sweettuner.core.PitchEstimate
import app.sweettuner.core.YinPitchDetector

/**
 * Captures mono audio from the built-in microphone and runs the pitch detector on a
 * background thread. Results (or null for silence) are delivered on the main thread.
 */
class AudioEngine(
    private val context: Context,
    private val onPitch: (PitchEstimate?) -> Unit,
) {
    @Volatile private var running = false
    private var thread: Thread? = null
    private val main = Handler(Looper.getMainLooper())

    val isRunning: Boolean get() = running

    /** Caller must hold RECORD_AUDIO. Returns false if the microphone could not be opened. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val (record, sampleRate) = openRecorder() ?: return false
        running = true
        thread = Thread({ loop(record, sampleRate) }, "SweetTuner-audio").also { it.start() }
        return true
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission")
    private fun openRecorder(): Pair<AudioRecord, Int>? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        // UNPROCESSED avoids automatic gain control and noise suppression, which smear pitch.
        // VOICE_RECOGNITION is the next best (AGC usually off); MIC is the last resort.
        val sources = buildList {
            if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED)
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            add(MediaRecorder.AudioSource.MIC)
        }
        for (rate in intArrayOf(48000, 44100)) {
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            if (min <= 0) continue
            val bytes = maxOf(min, 16384 * 4)
            for (src in sources) {
                try {
                    val r = AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, bytes)
                    if (r.state == AudioRecord.STATE_INITIALIZED) return r to rate
                    r.release()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioRecord src=$src rate=$rate failed", e)
                }
            }
        }
        return null
    }

    private fun loop(record: AudioRecord, sampleRate: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val detector = YinPitchDetector(sampleRate)
        val need = detector.requiredSamples
        val hop = if (sampleRate >= 48000) 2048 else 1920 // ~23 analyses per second
        val ring = FloatArray(need)
        val chunk = FloatArray(hop)
        var filled = 0

        try {
            record.startRecording()
            while (running) {
                var got = 0
                while (got < hop && running) {
                    val n = record.read(chunk, got, hop - got, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        Log.e(TAG, "read error $n"); running = false; break
                    }
                    got += n
                }
                if (!running) break
                // Slide the analysis window left and append the new hop.
                System.arraycopy(ring, hop, ring, 0, need - hop)
                System.arraycopy(chunk, 0, ring, need - hop, hop)
                filled = minOf(need, filled + hop)
                if (filled < need) continue

                val estimate = detector.detect(ring)
                main.post { onPitch(estimate) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "audio loop failed", e)
        } finally {
            try { record.stop() } catch (e: Exception) { Log.w(TAG, "stop failed", e) }
            record.release()
        }
    }

    private companion object { const val TAG = "AudioEngine" }
}
