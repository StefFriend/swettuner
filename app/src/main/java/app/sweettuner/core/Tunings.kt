package app.sweettuner.core

enum class TuningFamily(val title: String) {
    STANDARD("Standard & Drop"),
    OPEN("Open (chord) tunings"),
    MODAL("Modal & suspended"),
}

/**
 * A guitar tuning. [midi] is ordered from the lowest string (string 6) to the highest (string 1).
 * [chordRoot] is the pitch class (0 = C) of the chord the open strings form, or null when the
 * tuning is not built on a chord (standard, drop tunings). Chord-aware sweetenings use it.
 */
class Tuning(
    val id: String,
    val name: String,
    val family: TuningFamily,
    val midi: IntArray,
    val flats: Boolean = false,
    val chordRoot: Int? = null,
    val chordName: String? = null,
    val summary: String,
    val details: String,
) {
    val stringCount: Int get() = midi.size

    /** Index 0 is the lowest string, which is string number 6 on a six-string guitar. */
    fun stringNumber(index: Int): Int = stringCount - index

    fun noteName(index: Int): String = Music.noteName(midi[index], flats)
    fun fullName(index: Int): String = Music.fullName(midi[index], flats)

    /** "D G D G B D" */
    fun shortNotes(): String = midi.indices.joinToString(" ") { noteName(it) }

    /** "D2 G2 D3 G3 B3 D4" */
    fun fullNotes(): String = midi.indices.joinToString(" ") { fullName(it) }
}

object Tunings {
    /** E2 A2 D3 G3 B3 E4 */
    val STANDARD_MIDI = intArrayOf(40, 45, 50, 55, 59, 64)

    val all: List<Tuning> = listOf(
        // ---------------------------------------------------------------- Standard & drop
        Tuning(
            id = "standard", name = "Standard (E)", family = TuningFamily.STANDARD,
            midi = intArrayOf(40, 45, 50, 55, 59, 64),
            summary = "The default guitar tuning: E A D G B E.",
            details = "Strings are a perfect fourth apart, except G to B, which is a major third. " +
                "This layout keeps common chord and scale shapes within a few frets and is the " +
                "reference that every other tuning is described against. That single major third " +
                "between strings 3 and 2 is also the main reason guitars are “sweetened”: in equal " +
                "temperament it is about 14 cents wider than a pure third.",
        ),
        Tuning(
            id = "eb_standard", name = "E♭ Standard (half step down)", family = TuningFamily.STANDARD,
            midi = intArrayOf(39, 44, 49, 54, 58, 63), flats = true,
            summary = "Every string a half step lower: E♭ A♭ D♭ G♭ B♭ E♭.",
            details = "Same intervals and chord shapes as standard, one semitone lower. It eases " +
                "string tension (easier bends, slightly fatter tone) and suits singers who prefer a " +
                "lower key. Associated with players such as Jimi Hendrix and Stevie Ray Vaughan.",
        ),
        Tuning(
            id = "d_standard", name = "D Standard (whole step down)", family = TuningFamily.STANDARD,
            midi = intArrayOf(38, 43, 48, 53, 57, 62),
            summary = "Every string a whole step lower: D G C F A D.",
            details = "Standard shapes two semitones lower. Heavier sounding, looser feel; heavier " +
                "string gauges are recommended to keep tension and intonation stable.",
        ),
        Tuning(
            id = "drop_d", name = "Drop D", family = TuningFamily.STANDARD,
            midi = intArrayOf(38, 45, 50, 55, 59, 64),
            summary = "Standard with the 6th string lowered to D: D A D G B E.",
            details = "Only the low E drops a whole step. Power chords on the three lowest strings " +
                "become a one-finger barre, and songs in D get a low open root. Common in rock, " +
                "metal and fingerstyle arrangements.",
        ),
        Tuning(
            id = "double_drop_d", name = "Double Drop D", family = TuningFamily.STANDARD,
            midi = intArrayOf(38, 45, 50, 55, 59, 62),
            summary = "Both E strings lowered to D: D A D G B D.",
            details = "Like Drop D but the high E is also lowered to D, so both outer strings ring " +
                "as D drones. Rich for open-position playing in D and G.",
        ),
        Tuning(
            id = "drop_c", name = "Drop C", family = TuningFamily.STANDARD,
            midi = intArrayOf(36, 43, 48, 53, 57, 62),
            summary = "D Standard with the 6th string dropped to C: C G C F A D.",
            details = "Drop D shapes, a whole step lower. Popular in heavy rock and metal. Use " +
                "heavier strings; light gauges will feel floppy and play sharp when picked hard.",
        ),

        // ---------------------------------------------------------------- Open chord tunings
        Tuning(
            id = "open_d", name = "Open D", family = TuningFamily.OPEN,
            midi = intArrayOf(38, 45, 50, 54, 57, 62), chordRoot = 2, chordName = "D major",
            summary = "Open strings form a D major chord: D A D F♯ A D.",
            details = "A favourite for slide guitar and folk/fingerstyle playing. A straight barre " +
                "at any fret gives a major chord. The F♯ on string 3 is the chord's major third, " +
                "the string that benefits most from sweetening.",
        ),
        Tuning(
            id = "open_dm", name = "Open D minor", family = TuningFamily.OPEN,
            midi = intArrayOf(38, 45, 50, 53, 57, 62), flats = true, chordRoot = 2, chordName = "D minor",
            summary = "Open strings form a D minor chord: D A D F A D.",
            details = "Open D with string 3 lowered a further half step to F. Dark, modal sound used " +
                "in country blues (often called “cross-note” tuning, associated with Skip James). " +
                "Here the F is a minor third, which is narrow in equal temperament, so it is " +
                "sweetened upwards.",
        ),
        Tuning(
            id = "open_e", name = "Open E", family = TuningFamily.OPEN,
            midi = intArrayOf(40, 47, 52, 56, 59, 64), chordRoot = 4, chordName = "E major",
            summary = "Open strings form an E major chord: E B E G♯ B E.",
            details = "Same intervals as Open D, a whole step higher, reached by raising three " +
                "strings. Bright and powerful for slide (Duane Allman is a well-known example). It " +
                "adds tension to the neck, so some players prefer Open D with a capo at the 2nd fret.",
        ),
        Tuning(
            id = "open_g", name = "Open G", family = TuningFamily.OPEN,
            midi = intArrayOf(38, 43, 50, 55, 59, 62), chordRoot = 7, chordName = "G major",
            summary = "Open strings form a G major chord: D G D G B D.",
            details = "The classic blues, slide and rock-rhythm tuning, famously used by Keith " +
                "Richards (often with the 6th string removed). The B string is the chord's major " +
                "third.",
        ),
        Tuning(
            id = "open_gm", name = "Open G minor", family = TuningFamily.OPEN,
            midi = intArrayOf(38, 43, 50, 55, 58, 62), flats = true, chordRoot = 7, chordName = "G minor",
            summary = "Open strings form a G minor chord: D G D G B♭ D.",
            details = "Open G with the B lowered to B♭. Moody and modal; the B♭ is the minor third.",
        ),
        Tuning(
            id = "open_a", name = "Open A", family = TuningFamily.OPEN,
            midi = intArrayOf(40, 45, 52, 57, 61, 64), chordRoot = 9, chordName = "A major",
            summary = "Open strings form an A major chord: E A E A C♯ E.",
            details = "Same intervals as Open G, a whole step higher. Raises three strings, so check " +
                "your string gauges; Open G with a capo at the 2nd fret gives the same pitches with " +
                "less tension.",
        ),
        Tuning(
            id = "open_c", name = "Open C", family = TuningFamily.OPEN,
            midi = intArrayOf(36, 43, 48, 55, 60, 64), chordRoot = 0, chordName = "C major",
            summary = "Open strings form a C major chord: C G C G C E.",
            details = "A deep, piano-like open tuning with a wide spread: three Cs, two Gs and a " +
                "single E on top as the major third. Heavier low strings help the bottom C stay " +
                "in pitch.",
        ),

        // ---------------------------------------------------------------- Modal / suspended
        Tuning(
            id = "dadgad", name = "DADGAD", family = TuningFamily.MODAL,
            midi = intArrayOf(38, 45, 50, 55, 57, 62), chordRoot = 2, chordName = "Dsus4",
            summary = "D A D G A D, a suspended (Dsus4) open tuning.",
            details = "Neither major nor minor, so it suits modal melodies and drones. Strongly " +
                "associated with Celtic and folk fingerstyle (popularised by Davey Graham) and used " +
                "on Led Zeppelin's “Kashmir”. There is no third in the open strings; chord-aware " +
                "sweetening only nudges the fourths and fifths towards pure.",
        ),
        Tuning(
            id = "cgdgcd", name = "CGDGCD", family = TuningFamily.MODAL,
            midi = intArrayOf(36, 43, 50, 55, 60, 62), chordRoot = 0, chordName = "Csus2",
            summary = "C G D G C D, a deep suspended (Csus2) tuning.",
            details = "Think of it as DADGAD shifted to C with a different top: wide, harp-like and " +
                "full of open fifths. Popular in contemporary acoustic fingerstyle.",
        ),
    )

    val default: Tuning get() = all.first()

    fun byId(id: String?): Tuning = all.firstOrNull { it.id == id } ?: default
}

/** Auto-generated, therefore always consistent with the tuning data. */
object TuningGuide {

    private fun stepWords(semitones: Int): String = when (semitones) {
        1 -> "1 semitone (half step)"
        2 -> "2 semitones (whole step)"
        else -> "$semitones semitones"
    }

    /** One line per string describing how to get there from standard tuning. */
    fun fromStandard(t: Tuning): List<String> {
        if (t.stringCount != Tunings.STANDARD_MIDI.size) return emptyList()
        return t.midi.indices.map { i ->
            val from = Tunings.STANDARD_MIDI[i]
            val to = t.midi[i]
            val diff = to - from
            val label = "String ${t.stringNumber(i)}: ${Music.fullName(from, false)} → ${t.fullName(i)}"
            when {
                diff == 0 -> "$label — unchanged"
                diff < 0 -> "$label — down ${stepWords(-diff)}"
                else -> "$label — up ${stepWords(diff)}"
            }
        }
    }

    /** Relative tuning by ear, from the lowest string upwards. */
    fun relative(t: Tuning): List<String> {
        val lines = mutableListOf<String>()
        lines += "Tune string ${t.stringNumber(0)} (${t.fullName(0)}) with the tuner or a reference pitch."
        for (i in 1 until t.stringCount) {
            val fret = t.midi[i] - t.midi[i - 1]
            lines += if (fret == 0) {
                "Open string ${t.stringNumber(i)} matches open string ${t.stringNumber(i - 1)} (${t.fullName(i)})."
            } else {
                "Fret ${fret} on string ${t.stringNumber(i - 1)} = open string ${t.stringNumber(i)} (${t.fullName(i)})."
            }
        }
        return lines
    }
}
