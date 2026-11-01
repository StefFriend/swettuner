# Sweet Tuner

**A minimal Android guitar tuner with sweetened tunings.**

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84)
![Language](https://img.shields.io/badge/Kotlin-2.1-7F52FF)
![Dependencies](https://img.shields.io/badge/dependencies-none-blue)
![License](https://img.shields.io/badge/license-MIT-green)

Sweet Tuner listens through the phone's microphone and shows, in one glance, the note, the string
and how far off you are, measured against a **sweetened** target rather than plain equal temperament.
It supports standard, drop, open and modal tunings, and explains each one and how to tune it.

---

## Features

- **Microphone input only**: no cable or clip-on needed
- **Minimal screen**: big note name, small string number, cents meter
  - top: current tuning and sweetening type
  - bottom: detected frequency, target frequency and the string's sweetening offset
- **Automatic string detection**, or tap a string at the bottom of the screen to lock it
- **15 tunings**: standard, drop, open and modal
- **6 sweetening profiles**, including chord-aware just intonation for open tunings
- **Built-in guide** for every tuning and sweetening: what it is, how to get there from standard
  tuning, and relative tuning by ear
- **Adjustable reference pitch** (A4 = 415–466 Hz)
- No ads, no network access, no tracking; the only permission is the microphone

## What is a sweetened tuning?

Guitars are fretted for **equal temperament**, where every semitone is exactly 100 cents. That makes
every key playable, but the intervals are not acoustically pure: a major third is about 14 cents wider
than the natural 5:4 ratio, and a fifth is about 2 cents narrower than 3:2. Pressing strings against
the frets also pulls notes sharp, by a different amount on each string.

A **sweetened tuning** sets each open string a few cents away from equal temperament so that the
chords you actually play sound smoother. The idea was popularised by Peterson strobe tuners. Their
built-in sweeteners are proprietary and are **not** reproduced here; Sweet Tuner uses published or
openly derived offsets instead.

Sweetening is always a compromise: what sounds better on open chords can sound worse elsewhere.
To match a piano or a backing track exactly, choose **Equal Temperament**.

## Tunings

| Family | Tunings |
|---|---|
| Standard & drop | Standard (E A D G B E), E♭ Standard, D Standard, Drop D, Double Drop D, Drop C |
| Open (chord) | Open D, Open D minor, Open E, Open G, Open G minor, Open A, Open C |
| Modal & suspended | DADGAD, CGDGCD |

## Sweetening profiles

Offsets are in cents from equal temperament, listed from string 6 to string 1.

| Profile | Offsets | Basis |
|---|---|---|
| **Equal Temperament** | 0 0 0 0 0 0 | No correction |
| **Buzz Feiten (open strings)** | −2 −2 −2 −2 +1 0 (−4 on a dropped 6th string) | Values published by Tom Anderson Guitarworks for BFTS-equipped guitars |
| **Acoustic Stretch** | −12 −10 −8 −4 −6 −3 | Widely circulated chart attributed to James Taylor's setup; compensates for fretting and capo sharpness |
| **Just Thirds** | major third −13.7, minor third +15.6 | Only the string playing the third: the B string in standard tuning, the chord's third in open tunings |
| **Just Open Chord** | depends on the tuning | Every string on its 5-limit just interval above the open chord's root (fifths +2.0, fourths −2.0, thirds as above) |
| **Just Open Chord ½** | half of the above | Smoother open chords while staying closer to equal temperament |

The chord-aware profiles adapt to the selected tuning. In Open G, for example, Just Open Chord gives
`+2.0 0 +2.0 0 −13.7 +2.0`, and in DADGAD it gives `0 +2.0 0 −2.0 +2.0 0`. The menu shows the exact
offsets and target frequencies for the current tuning.

## Download

Every push to this repository builds a debug APK with GitHub Actions:

1. Open the **Actions** tab and select the latest successful **Build APK** run.
2. Download the **SweetTuner-debug-apk** artifact and unzip it.
3. Install the APK on your phone (you will need to allow installs from unknown sources).

## Build from source

Requirements: Android Studio (Ladybug or newer) or JDK 17 plus the Android SDK.

```bash
git clone https://github.com/<your-username>/SweetTuner.git
cd SweetTuner
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Or open the folder in Android Studio and press **Run**.

`./gradlew assembleRelease` builds a minified APK signed with the debug key, which is fine for
personal use. Configure your own signing key before publishing to a store.

Stack: Android Gradle Plugin 8.9.1, Kotlin 2.1.20, Gradle 8.14.3, compileSdk 35, minSdk 26.
The app uses only the Android framework and the Kotlin standard library, with no third-party
dependencies.

## How it works

- **Audio**: `AudioRecord`, mono float PCM at 48 or 44.1 kHz. The `UNPROCESSED` source is used when
  the device supports it, so automatic gain control and noise suppression don't distort the pitch.
- **Pitch detection**: the YIN algorithm (de Cheveigné & Kawahara, 2002) with parabolic
  interpolation, an analysis window of at least four periods of 58 Hz (covers Drop C), and about
  23 analyses per second.
- **Stabilisation**: a median filter over recent frames removes octave glitches, followed by light
  exponential smoothing in the cents domain.
- **String matching**: the detected pitch is compared with every string's sweetened target, and the
  nearest one wins, unless a string is locked.

On synthetic guitar tones (C2 to E4, strong and weak fundamentals, noise and decay), single-frame
error is within 0.75 cents in 99.8 % of 2160 test cases, with a worst case of 1.1 cents on the lowest
strings. Smoothing reduces it further. Real strings are slightly inharmonic, so any tuner can read
a few tenths of a cent differently depending on how the string is plucked.

## Project structure

```
app/src/main/java/app/sweettuner/
├── core/                 Pure Kotlin, no Android dependencies (unit-testable on the JVM)
│   ├── Music.kt          Note names, MIDI ↔ frequency, cents
│   ├── Tunings.kt        Tuning data, descriptions, auto-generated tuning guides
│   ├── Sweetening.kt     Sweetening profiles and per-tuning offset computation
│   └── PitchDetector.kt  YIN detector, string matcher, smoother
├── audio/AudioEngine.kt  Microphone capture and the analysis thread
├── ui/TunerView.kt       The tuner screen, drawn on a Canvas
├── ui/Insets.kt          Edge-to-edge system bar padding
├── MainActivity.kt       Tuner screen and microphone permission
├── MenuActivity.kt       Tuning, sweetening and reference pitch selection with explanations
└── Prefs.kt              Saved settings
```

## Adding a tuning or a sweetening

- **Tuning**: add an entry to `Tunings.all` in `core/Tunings.kt`, with MIDI notes from string 6 to 1.
  Set `chordRoot` (pitch class, 0 = C) if the open strings form a chord, so that the chord-aware
  sweetenings work. The step-by-step guides are generated automatically.
- **Sweetening**: add an entry to `Sweetenings.all` in `core/Sweetening.kt`, either as fixed
  per-string offsets (`SweetKind.PER_STRING`) or as a rule computed from the tuning.

Contributions are welcome: open an issue or a pull request.

## Roadmap

- Custom sweetening with user-defined per-string offsets
- 7-string, 12-string, bass and baritone support
- Optional strobe-style display

## License

Released under the [MIT License](LICENSE). © 2026 Stefano Antonio Amico.

*Buzz Feiten Tuning System is a trademark of its respective owner. Peterson and the names of its
sweeteners are trademarks of Peterson Electro-Musical Products. This project is not affiliated
with or endorsed by either.*
