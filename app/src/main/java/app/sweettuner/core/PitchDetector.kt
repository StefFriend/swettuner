package app.sweettuner.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/** One pitch estimate. [clarity] is 1 − YIN aperiodicity (1 = perfectly periodic). */
data class PitchEstimate(val frequency: Double, val clarity: Double, val rms: Double)

/**
 * YIN pitch detector (de Cheveigné & Kawahara, 2002) with parabolic interpolation.
 * Range defaults cover a guitar from C2 (65.4 Hz, Drop C) up to high-fret notes.
 */
class YinPitchDetector(
    val sampleRate: Int,
    minFrequency: Double = 58.0,
    maxFrequency: Double = 1200.0,
    private val threshold: Double = 0.12,
    /** Signals quieter than this RMS (full scale = 1.0) are treated as silence. */
    var rmsGate: Double = 0.0015,
) {
    private val tauMax: Int = ceil(sampleRate / minFrequency).toInt()
    private val tauMin: Int = max(2, floor(sampleRate / maxFrequency).toInt())
    private val window: Int = max(2048, 4 * tauMax)

    /** Number of samples [detect] needs. */
    val requiredSamples: Int = window + tauMax + 2

    private val diff = DoubleArray(tauMax + 2)
    private val cmnd = DoubleArray(tauMax + 2)
    private val frame = DoubleArray(requiredSamples)

    /** Analyses the last [requiredSamples] samples of [buf] ending at [end] (exclusive). */
    fun detect(buf: FloatArray, end: Int = buf.size): PitchEstimate? {
        val start = end - requiredSamples
        require(start >= 0) { "Need $requiredSamples samples" }

        // Copy, remove DC, measure level.
        var mean = 0.0
        for (i in 0 until requiredSamples) mean += buf[start + i]
        mean /= requiredSamples
        var energy = 0.0
        for (i in 0 until requiredSamples) {
            val v = buf[start + i] - mean
            frame[i] = v
            energy += v * v
        }
        val rms = sqrt(energy / requiredSamples)
        if (rms < rmsGate) return null

        // Step 2: difference function.
        val last = tauMax + 1
        for (tau in 1..last) {
            var sum = 0.0
            var j = 0
            while (j < window) {
                val d = frame[j] - frame[j + tau]
                sum += d * d
                j++
            }
            diff[tau] = sum
        }

        // Step 3: cumulative mean normalised difference.
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..last) {
            running += diff[tau]
            cmnd[tau] = if (running > 0) diff[tau] * tau / running else 1.0
        }

        // Step 4: absolute threshold — first dip below threshold, then walk to its minimum.
        var tau = -1
        var t = tauMin
        while (t < last) {
            if (cmnd[t] < threshold) {
                while (t + 1 < last && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // No clear dip: accept the global minimum only if it is reasonably periodic.
            var best = tauMin
            for (k in tauMin until last) if (cmnd[k] < cmnd[best]) best = k
            if (cmnd[best] > 0.25) return null
            tau = best
        }

        // Step 5: parabolic interpolation around the minimum.
        val refined = if (tau in 1 until last) {
            val s0 = diff[tau - 1]
            val s1 = diff[tau]
            val s2 = diff[tau + 1]
            val denom = s0 - 2 * s1 + s2
            if (abs(denom) > 1e-12) tau + 0.5 * (s0 - s2) / denom else tau.toDouble()
        } else tau.toDouble()

        if (refined <= 0) return null
        return PitchEstimate(sampleRate / refined, (1.0 - cmnd[tau]).coerceIn(0.0, 1.0), rms)
    }
}

/** Which string the player is tuning and how far off it is. */
data class TunerReading(
    val stringIndex: Int,
    val detectedHz: Double,
    val targetHz: Double,
    /** Deviation from the sweetened target. */
    val cents: Double,
    /** Offset of the sweetened target from equal temperament. */
    val sweetOffset: Double,
)

object StringMatcher {
    fun targets(t: Tuning, offsets: DoubleArray, a4: Double): DoubleArray =
        DoubleArray(t.stringCount) { i -> Music.shift(Music.frequency(t.midi[i].toDouble(), a4), offsets[i]) }

    /**
     * Picks the string whose sweetened target is nearest to [hz] (or [locked] if set).
     * Returns null when the note is more than [maxCents] away from every string.
     */
    fun match(
        hz: Double, t: Tuning, offsets: DoubleArray, a4: Double,
        locked: Int? = null, maxCents: Double = 600.0,
    ): TunerReading? {
        val targets = targets(t, offsets, a4)
        val idx = locked ?: targets.indices.minByOrNull { abs(Music.cents(hz, targets[it])) } ?: return null
        val c = Music.cents(hz, targets[idx])
        if (locked == null && abs(c) > maxCents) return null
        return TunerReading(idx, hz, targets[idx], c, offsets[idx])
    }
}

/**
 * Stabilises raw estimates: median of the last few frames (kills octave blips),
 * then light exponential smoothing in the cents domain. Resets on a real note change.
 */
class PitchSmoother(private val size: Int = 5, private val alpha: Double = 0.35) {
    private val history = ArrayDeque<Double>()
    private var smoothed: Double? = null

    fun reset() {
        history.clear(); smoothed = null
    }

    fun push(hz: Double): Double {
        history.addLast(hz)
        while (history.size > size) history.removeFirst()
        val median = history.sorted()[history.size / 2]
        val s = smoothed
        // A jump of the median means a new string: follow it immediately instead of gliding.
        val next = if (s == null || abs(Music.cents(median, s)) > 80.0) median
        else Music.shift(s, alpha * Music.cents(median, s))
        smoothed = next
        return next
    }
}
