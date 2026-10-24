# Source and protocol provenance

The Android application in this repository is an independent Kotlin/OpenGL ES implementation. It does not vendor source files, user interfaces, icons, watermarks, firmware, protobuf files, or media assets from the reference repositories below.

| Reference | License/status | Use in this project |
|---|---|---|
| https://github.com/diamondfsd/luna-ai-cut | MIT | Publicly documented protocol behavior and terminology were consulted. No source file is vendored. |
| https://github.com/Ripwords/insta360-luna-ultra-desktop | No repository license found during the audit | Publicly observable command numbers, field meanings, and measured device behavior were used as research leads. Its TypeScript/Rust code, UI, icons, official watermark assets, screenshots, and documentation text are not included. |
| https://github.com/derrickyau9/insta360-luna-ultra-reverse-engineering | Apache-2.0 | Device research context only. Root, ADB, SSH, and installation scripts are not included. |

Protocol constants and packet layouts are implemented independently in:

- `app/src/main/java/com/lunamonitor/beta/protocol/Ucd2Protocol.kt`
- `app/src/main/java/com/lunamonitor/beta/protocol/CameraControlProtocol.kt`
- `app/src/main/java/com/lunamonitor/beta/camera/LunaSession.kt`

The application contains no camera firmware, extracted official application code, official logo, official watermark, or official image asset.

## 1.1.0 additions (2026-10-24)

- https://opencapture.org/openinstacine/docs/devices/luna-ultra/controls/ — command 226, MOVE axes and ZigZag, release/recenter distinction, repeat-stop timing and observed firmware scope. Facts used for original Kotlin/Swift encoders; no implementation copied.
- https://opencapture.org/openinstacine/docs/protocol/live-view/ — complete UCD2 video AU boundaries, checksum requirement, wake command 118 and current live-start body. Old working request is retained as a fallback.
- https://github.com/erik-sutton95/OpenInstaCine — public protocol documentation research lead, not a code dependency or vendored implementation. Repository identifies Apache-2.0; this project does not import its source or built-in official LUT.
- https://developer.android.com/reference/android/media/MediaCodec — native decoder lifecycle/low latency.
- https://developer.apple.com/documentation/videotoolbox and https://developer.apple.com/documentation/metal — native iOS platform APIs. Swift adaptation of this project's own protocol and grading; no third-party phone app implementation is included.
- https://docs.sidestore.io/ — installation tool documentation only, not bundled or integrated; re-signing/install remains the user's responsibility.
