# Decoder engineering notes

## First integration slice

`Msk2400Demodulator` accepts streaming signed PCM16 mono at 48 kHz. It decimates by four, mixes around 1800 Hz, applies a one-sample complex phase discriminator, integrates the discriminator over five 12 kHz samples, and emits hard bits plus a simple confidence estimate.

This is a deliberately small first slice so the audio capture path and DSP boundary exist. It is not a complete or validated ACARS physical-layer receiver. In particular:

- decimation currently has no designed anti-alias/channel filter;
- symbol timing is fixed, with no early/late or PLL recovery;
- bit polarity and exact ACARS framing have not been established from reference vectors;
- the frame synchronizer is generic and requires an independently verified sync word;
- ACARS parity and BCS are not implemented;
- no message may be reported as valid at this stage.

## Next engineering gate

Before implementing character parsing, compare this front-end with `acarsdec` on the same reference audio and establish sample rate, symbol timing, polarity, sync pattern, parity convention and BCS algorithm. Prefer reusing the proven GPL-2.0-only reference core (with full compliance) over reimplementing protocol details from guesswork.

## Local compile check

The three decoder Kotlin source files were compiled successfully with `kotlinc` in isolation. This does not verify the Android app, AudioRecord integration, Gradle configuration, or runtime DSP performance.

## Offline audio test harness (added 2026-10-31)

The development snapshot also includes an `AudioFilePcmDecoder` using Android `MediaExtractor`/`MediaCodec` to decode a selected audio document, downmix it to mono and resample to 48 kHz PCM16 before passing it through the experimental pipeline. The UI reports duration, RMS, peak and demodulated-bit count. It deliberately does not label any bits as ACARS frames or messages.

The supplied `Acars_sample.ogg.ogx` was previously reported as Ogg/Vorbis, mono, 48 kHz and about 0.454 seconds. It has not been run through the Android harness or validated against a reference decoder in this project.

The Android project has not yet been built with the Android SDK/Gradle toolchain. Kotlin/Android API compatibility and handset codec support remain unverified.
