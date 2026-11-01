package app.sweettuner.core

/**
 * A sweetening is a set of small per-string offsets (in cents) applied on top of
 * equal temperament. Offsets are computed for a specific tuning, because some
 * sweetenings depend on which interval each string plays in the open chord.
 */
enum class SweetKind { NONE, PER_STRING, JUST_THIRDS, JUST_CHORD }

class Sweetening(
    val id: String,
    val name: String,
    val shortName: String,
    val kind: SweetKind,
    /** Only for PER_STRING: offsets from string 6 to string 1. */
    private val perString: DoubleArray? = null,
    /** Only for JUST_CHORD: fraction of the just-intonation correction applied (1 = full). */
    private val amount: Double = 1.0,
    val summary: String,
    val details: String,
    val howTo: String,
    val source: String,
) {
    /** True when this sweetening changes anything for [t]. */
    fun isApplicable(t: Tuning): Boolean = when (kind) {
        SweetKind.NONE -> true
        SweetKind.PER_STRING -> t.stringCount == 6
        SweetKind.JUST_THIRDS, SweetKind.JUST_CHORD -> offsets(t).any { it != 0.0 }
    }

    /** Why it does nothing for [t], or null when it applies. */
    fun notApplicableReason(t: Tuning): String? {
        if (isApplicable(t)) return null
        return when (kind) {
            SweetKind.JUST_CHORD ->
                "${t.name} is not built on a chord, so there is no root to make intervals pure against. " +
                    "Use Just Thirds, Buzz Feiten or Acoustic Stretch instead."
            SweetKind.JUST_THIRDS ->
                "${t.name} has no third between its open strings, so nothing changes. " +
                    "Try Just Open Chord, which also tunes the fourths and fifths pure."
            else -> "Not available for this tuning."
        }
    }

    /** Offsets in cents for each string of [t], index 0 = lowest string. */
    fun offsets(t: Tuning): DoubleArray {
        val n = t.stringCount
        val out = DoubleArray(n)
        when (kind) {
            SweetKind.NONE -> Unit

            SweetKind.PER_STRING -> {
                val p = perString ?: return out
                if (n == p.size) {
                    for (i in 0 until n) out[i] = p[i]
                    // Tom Anderson's BFTS chart gives -4 cents for a dropped 6th string (Drop D).
                    if (id == BUZZ_FEITEN && t.midi[0] == Tunings.STANDARD_MIDI[0] - 2 &&
                        t.midi[1] == Tunings.STANDARD_MIDI[1]
                    ) out[0] = -4.0
                }
            }

            SweetKind.JUST_THIRDS -> {
                val root = t.chordRoot
                if (root != null) {
                    for (i in 0 until n) {
                        when (Math.floorMod(t.midi[i] - root, 12)) {
                            4 -> out[i] = JI_DEVIATION[4] // major third: -13.7
                            3 -> out[i] = JI_DEVIATION[3] // minor third: +15.6
                        }
                    }
                } else {
                    // Not a chord tuning: lower any string that sits a major third above the
                    // string below it (G -> B in standard tuning).
                    for (i in 1 until n) {
                        if (t.midi[i] - t.midi[i - 1] == 4) out[i] = JI_DEVIATION[4]
                    }
                }
            }

            SweetKind.JUST_CHORD -> {
                val root = t.chordRoot ?: return out
                for (i in 0 until n) {
                    out[i] = JI_DEVIATION[Math.floorMod(t.midi[i] - root, 12)] * amount
                }
            }
        }
        // Round to 0.1 cent, like hardware strobe tuners.
        for (i in 0 until n) out[i] = Math.round(out[i] * 10.0) / 10.0
        return out
    }

    companion object {
        const val EQUAL = "equal"
        const val BUZZ_FEITEN = "buzz_feiten"
        const val ACOUSTIC = "acoustic_stretch"
        const val JUST_THIRDS = "just_thirds"
        const val JUST_CHORD = "just_chord"
        const val JUST_CHORD_HALF = "just_chord_half"

        /**
         * Deviation (cents) of the 5-limit just interval from the equal-tempered one,
         * indexed by semitones above the root: 16/15, 9/8, 6/5, 5/4, 4/3, 45/32, 3/2, 8/5, 5/3, 16/9, 15/8.
         */
        val JI_DEVIATION = doubleArrayOf(
            0.0, 11.73, 3.91, 15.64, -13.69, -1.96, -9.78, 1.96, 13.69, -15.64, -3.91, -11.73,
        )
    }
}

object Sweetenings {
    val all: List<Sweetening> = listOf(
        Sweetening(
            id = Sweetening.EQUAL, name = "Equal Temperament", shortName = "Equal (none)",
            kind = SweetKind.NONE,
            summary = "No sweetening: every string exactly on its equal-tempered pitch.",
            details = "Every semitone is exactly 100 cents. This is what a normal tuner does and " +
                "the correct choice when you need to match keyboards or a backing track exactly. " +
                "Its compromise is that thirds are wide (major) or narrow (minor), which is most " +
                "audible on open chords and with distortion.",
            howTo = "Tune every string until the needle is centred.",
            source = "Standard 12-tone equal temperament.",
        ),
        Sweetening(
            id = Sweetening.BUZZ_FEITEN, name = "Buzz Feiten (open strings)", shortName = "Buzz Feiten",
            kind = SweetKind.PER_STRING,
            perString = doubleArrayOf(-2.0, -2.0, -2.0, -2.0, 1.0, 0.0),
            summary = "Open-string offsets of the Buzz Feiten Tuning System: E A D G −2 ¢, B +1 ¢, e 0 ¢.",
            details = "The Buzz Feiten Tuning System (BFTS) combines a compensated nut with specific " +
                "intonation offsets at the bridge, so that chords sound balanced all over the neck. " +
                "These offsets are designed for guitars fitted with BFTS; on an ordinary guitar " +
                "they still act as a mild sweetening. On a dropped 6th string (Drop D) the offset " +
                "becomes −4 ¢. Do not use harmonics with BFTS: open-string and 12th-fret targets differ.",
            howTo = "Tune each open string to the shown target. For BFTS intonation at the 12th fret " +
                "the published targets are 0 ¢ on E, B, A, low E and +1 ¢ on G and D (set with " +
                "Equal Temperament and a fretted note, not this preset).",
            source = "Offsets as published by Tom Anderson Guitarworks for BFTS-equipped guitars.",
        ),
        Sweetening(
            id = Sweetening.ACOUSTIC, name = "Acoustic Stretch (capo-friendly)", shortName = "Acoustic Stretch",
            kind = SweetKind.PER_STRING,
            perString = doubleArrayOf(-12.0, -10.0, -8.0, -4.0, -6.0, -3.0),
            summary = "All strings slightly flat, thicker cores more: compensates for fretting and capo sharpness.",
            details = "Pressing a string (or clamping a capo) stretches it and pulls the note sharp; " +
                "strings with a thicker steel core go sharper. Tuning the open strings flat by " +
                "graded amounts makes fretted chords and capo positions land closer to pitch. " +
                "This chart is the widely circulated one attributed to James Taylor's setup " +
                "(values vary between sources). Because open strings are flat, check it against " +
                "other instruments before a gig.",
            howTo = "Tune each open string to the shown flat target, then check a few fretted chords " +
                "or a capo position. Adjust by ear if your guitar, strings or capo behave differently.",
            source = "Forum-circulated chart (−12/−10/−8/−4/−6/−3 ¢, strings 6→1). Not Peterson's proprietary ACU values.",
        ),
        Sweetening(
            id = Sweetening.JUST_THIRDS, name = "Just Thirds", shortName = "Just Thirds",
            kind = SweetKind.JUST_THIRDS,
            summary = "Only the string that plays a third is retuned: major thirds −13.7 ¢, minor thirds +15.6 ¢.",
            details = "An equal-tempered major third is 13.7 cents wider than the pure 5:4 ratio, and " +
                "a minor third is 15.6 cents narrower than 6:5. In standard tuning this lowers the " +
                "B string (the G–B third) — the trick often credited to Eddie Van Halen for " +
                "cleaner distorted chords. In open tunings it targets the chord's third: F♯ in " +
                "Open D, B in Open G, G♯ in Open E, C♯ in Open A, E in Open C, F in Open D minor. " +
                "Open chords become beatless; some fretted shapes on that string will sound flat.",
            howTo = "Tune all other strings normally, then tune the third string to its offset target. " +
                "Play the open chord: the slow wobble (beating) should disappear.",
            source = "Derived from just-intonation ratios (5:4 = 386.3 ¢, 6:5 = 315.6 ¢).",
        ),
        Sweetening(
            id = Sweetening.JUST_CHORD, name = "Just Open Chord (full)", shortName = "Just Chord",
            kind = SweetKind.JUST_CHORD, amount = 1.0,
            summary = "For open & modal tunings: every string tuned to a pure interval above the chord root.",
            details = "Each string is placed on its 5-limit just-intonation pitch relative to the " +
                "tuning's root: fifths +2.0 ¢, fourths −2.0 ¢, major thirds −13.7 ¢, minor thirds " +
                "+15.6 ¢, seconds +3.9 ¢; roots stay at 0. The open chord rings completely pure, " +
                "ideal for slide, drones and DADGAD-style playing. The cost: fretted notes far from " +
                "the open chord drift further from equal temperament. Not available for standard " +
                "and drop tunings, which have no chord root.",
            howTo = "Tune the root strings first (0 ¢), then the fifths/fourths, then the third last. " +
                "Check the open chord and a barre at the 12th fret.",
            source = "Derived from just-intonation ratios (3:2, 4:3, 5:4, 6:5, 9:8).",
        ),
        Sweetening(
            id = Sweetening.JUST_CHORD_HALF, name = "Just Open Chord (½ compromise)", shortName = "Just Chord ½",
            kind = SweetKind.JUST_CHORD, amount = 0.5,
            summary = "Half of the just-intonation correction: sweeter open chords, safer fretted playing.",
            details = "Same idea as Just Open Chord, but each offset is halved (for example the major " +
                "third becomes −6.8 ¢). Beating on the open chord is much slower, while fretted " +
                "melodies and playing with other instruments stay closer to equal temperament. A " +
                "good everyday choice for open tunings.",
            howTo = "Tune as for Just Open Chord.",
            source = "50% of the just-intonation deviations.",
        ),
    )

    val default: Sweetening get() = all.first()

    fun byId(id: String?): Sweetening = all.firstOrNull { it.id == id } ?: default
}

const val ABOUT_SWEETENING: String =
    "Guitars are fretted for equal temperament, where every semitone is exactly 100 cents. " +
        "Equal temperament lets you play in every key, but its intervals are not acoustically pure: " +
        "major thirds are about 14 cents wide and fifths about 2 cents narrow. On top of that, " +
        "pressing strings against frets pulls notes sharp by different amounts on different strings.\n\n" +
        "A sweetened tuning tunes each open string a few cents away from equal temperament, so " +
        "that the chords you actually play sound smoother. The idea was popularised by Peterson " +
        "strobe tuners, whose built-in sweeteners are proprietary; this app uses published or " +
        "openly derived offsets instead.\n\n" +
        "Sweetening is always a compromise: what sounds better on open chords can sound worse " +
        "elsewhere. When you need to match a piano or a backing track exactly, use Equal Temperament."
