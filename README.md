# Operation ACARS for Android

**Status: experimental development scaffold — not a working ACARS decoder or release APK.**

An offline-first Android project for developing a VHF ACARS receiver, initially targeting 2400-baud MSK. The intended audio path is a mono receiver output connected to the phone's microphone input.

## Current development status

The project is being developed in stages. The experimental DSP front end and Android audio-capture/test harness are preliminary. They must not be described as a functioning ACARS receiver until validated against known-good recordings and live receiver audio.

The following protocol work remains outstanding before valid messages can be reported:
- robust channel and anti-alias filtering;
- frequency-offset handling and symbol-timing recovery;
- verified ACARS preamble/synchronisation and frame assembly;
- character parity and block-check-sequence validation;
- message parsing and validation against a reference decoder.

A demodulated bit count or apparent sync pattern is not proof of a valid ACARS frame.

## Build status

A GitHub Actions workflow is provided to attempt a debug APK build. A successful build demonstrates compilation only; it does not demonstrate correct ACARS decoding or reliable live audio operation. No release APK is claimed by this repository at this stage.

## Roadmap

1. Validate the demodulator against known-good ACARS recordings and a reference decoder.
2. Implement filtering, frequency-offset handling, and symbol-timing recovery.
3. Implement ACARS frame synchronisation, parity, and BCS validation.
4. Parse and display only validated messages.
5. Test offline recordings and then live receiver audio.
6. Verify on-device behaviour before publishing a versioned APK.

## Reference and licensing

[acarsdec](https://github.com/f00b4r0/acarsdec) is used as a protocol/implementation reference and is licensed GPL-2.0-only. No acarsdec source is included here unless explicitly identified with its licence and corresponding source. Any future incorporation or linking must be reviewed for GPL compliance.

## Scope

Receive-only hobbyist software development. This project does not write firmware to radio equipment and must not be relied on for safety-critical decisions.
