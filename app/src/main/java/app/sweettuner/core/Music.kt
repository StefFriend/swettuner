package app.sweettuner.core

import kotlin.math.ln
import kotlin.math.pow

/** Basic pitch helpers. MIDI note numbers: A4 = 69, E2 (low E) = 40. */
object Music {
    private val SHARPS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val FLATS = arrayOf("C", "D♭", "D", "E♭", "E", "F", "G♭", "G", "A♭", "A", "B♭", "B")

    fun pitchClass(midi: Int): Int = Math.floorMod(midi, 12)

    fun noteName(midi: Int, flats: Boolean): String =
        (if (flats) FLATS else SHARPS)[pitchClass(midi)]

    fun octave(midi: Int): Int = Math.floorDiv(midi, 12) - 1

    fun fullName(midi: Int, flats: Boolean): String = noteName(midi, flats) + octave(midi)

    /** Equal-tempered frequency of a (possibly fractional) MIDI note for the given A4. */
    fun frequency(midi: Double, a4: Double): Double = a4 * 2.0.pow((midi - 69.0) / 12.0)

    /** Interval in cents from [reference] to [freq]. */
    fun cents(freq: Double, reference: Double): Double = 1200.0 * ln(freq / reference) / ln(2.0)

    /** Applies a cents offset to a frequency. */
    fun shift(freq: Double, cents: Double): Double = freq * 2.0.pow(cents / 1200.0)

    fun formatCents(c: Double, decimals: Int = 1): String {
        val v = String.format(java.util.Locale.US, "%.${decimals}f", kotlin.math.abs(c))
        return when {
            v.toDouble() == 0.0 -> "0" + (if (decimals > 0) "." + "0".repeat(decimals) else "")
            c > 0 -> "+$v"
            else -> "−$v"
        }
    }
}
