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
