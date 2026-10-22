# Studio EQ

A system-wide equalizer for Android that works across Spotify, Podbean and
other audio and podcast apps, with per-device profiles and dedicated support
for Beats headphones. Like the battery app next door, it runs entirely
on-device: no `INTERNET` permission, no accounts.

## Features

| Area | What you get |
|------|--------------|
| **Equalizer** | 10-band graphic EQ (31 Hz – 16 kHz, ±12 dB, 0.5 dB steps), live frequency-response graph, 16 built-in presets (genres plus *Podcast / Voice* and *Audiobook*), save/delete your own |
| **Tone & output** | Bass shelf, treble shelf, virtualizer (stereo width), loudness enhancer, L/R balance, preamp, auto-headroom (no clipping from boosts), limiter |
| **Engine** | DynamicsProcessing at 31 one-third-octave bands on Android 9+, so the smoothed slider curve, shelves and corrections are reproduced accurately; falls back to the platform Equalizer on Android 8 |
| **Per-device profiles** | Each output (Beats, car, wired, phone speaker…) keeps its own EQ and loads automatically when it connects |
| **Beats** | Recognises Studio Buds/Buds +, Fit Pro, Solo Buds, Powerbeats Pro/Pro 2, Flex, Solo 4/Pro/3, Studio Pro/3 and Pill from the Bluetooth name; shows the headset battery level; offers a family-matched correction; model-specific tips |
| **Headphone correction** | Built-in starting points for each Beats family and the phone speaker, or import an **AutoEq** profile (GraphicEQ or ParametricEQ text) for a measured correction of your exact model |
| **Per-app presets** | e.g. Podbean → *Podcast / Voice* while Spotify keeps your music curve; both still get the headphone correction |
| **Integration** | Spotify's *Settings → Playback → Equalizer* opens this app; Quick Settings tile; status notification with an off switch; restarts after reboot |

## How it reaches other apps

Android does not let one app process another's audio directly. Every
third-party EQ (Wavelet, Poweramp Equalizer, etc.) uses the same three
mechanisms, and so does this one:

1. **Announced sessions** — players broadcast
   `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` with their audio session ID when
   playback starts. Spotify and most music players do this. The EQ attaches to
   that session.
2. **Advanced detection** — for players that don't announce (some podcast apps
   may not), the app reads the system's playback list (`dumpsys audio`) every
   3 s to find their sessions. This needs a one-time permission grant from a
   computer:

   ```
   adb shell pm grant com.tenan.equalizer android.permission.DUMP
   ```

3. **Global mode** — attaches to the phone's whole output mix (session 0).
   Works for any app with no setup, but applies one curve to everything (no
   per-app presets), and some phone firmwares ignore it.

The **Apps** tab lists every session currently being equalised and how it was
found, so you can see at a glance whether Podbean is covered. If it isn't
listed while playing, grant advanced detection or switch to Global mode.

## Beats: what's possible and what isn't

Possible here: automatic per-model profiles, correction curves, battery level
(the value the headset reports to Android, after you allow Bluetooth access),
AAC codec and fit guidance, per-app presets for podcasts vs music.

Not possible from a third-party app: switching Noise Cancelling/Transparency,
firmware updates, the ear-tip fit test, per-bud battery levels, or Apple's
head-tracked Spatial Audio. Those use Apple's private protocol and live in
Apple's **Beats** app for Android, which works alongside this one.

The built-in Beats corrections are deliberately mild (within ±3.5 dB) and
counter the Beats house sound — a raised bass shelf, low-mid warmth and a dip
in the 2–4 kHz presence region. They are not measurements of a specific unit.
For a measured curve, open [autoeq.app](https://autoeq.app), choose your
model, export *Wavelet* (GraphicEQ) or *Equalizer APO* (ParametricEQ), and
paste it via **Device → Import AutoEq profile**.

## Using it

1. Install the APK (from CI: the `studio-eq-debug-apk` artifact) and open it.
2. Flip the **Equalizer** switch on and allow notifications (they keep the
   engine alive and give you an off switch).
3. With your Beats connected, open **Device**, allow Bluetooth access for the
   battery reading, and apply the suggested correction or import AutoEq.
4. On **Apps**, give Podbean the *Podcast / Voice* preset. Start playback in
   Spotify and Podbean and check both appear under *Now equalising*.
5. In Spotify, turn its built-in equalizer off so the two don't stack.
6. On Samsung and other aggressive battery managers, exempt the app from
   battery optimisation (button on the Apps tab).

## Code map

| Package | Role |
|---------|------|
| `dsp` | Pure Kotlin: band grids, monotone-cubic curve interpolation, RBJ biquads, presets, AutoEq parser, `ProcessingPlan` (curve → 31 band gains, preamp, balance) |
| `device` | Output-device detection and battery, `BeatsCatalog`, built-in corrections |
| `session` | Session registry, broadcast receiver, `dumpsys audio` parser/scanner, known audio apps |
| `engine` | `EffectChain` (DynamicsProcessing / Equalizer + Virtualizer + LoudnessEnhancer per session), `EqService` (foreground service), Quick Settings tile, boot receiver |
| `data` | `EqConfig` model, JSON codec, `EqRepository` |
| `ui` | Compose screens: Equalizer, Device, Apps |

Unit tests (`./gradlew :equalizer:testDebugUnitTest`) cover the DSP math,
AutoEq parsing, Beats identification, dumpsys parsing, session bookkeeping
and config persistence. CI also boots an emulator, simulates Spotify
announcing a session and pressing its Equalizer button, switches the EQ on
from the Quick Settings tile, and fails on any crash.
