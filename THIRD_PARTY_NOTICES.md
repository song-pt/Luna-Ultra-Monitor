# Third-party notices

This project depends on AndroidX, Jetpack Compose, Kotlin, Kotlin Coroutines, Gradle, the Android Gradle Plugin, and JUnit for building or testing. Their copyrights and licenses remain with their respective authors.

- AndroidX and Jetpack Compose: Apache License 2.0 — https://github.com/androidx/androidx
- Kotlin and Kotlin Coroutines: Apache License 2.0 — https://github.com/JetBrains/kotlin and https://github.com/Kotlin/kotlinx.coroutines
- Gradle wrapper: Apache License 2.0 — https://github.com/gradle/gradle
- JUnit 4 test dependency: Eclipse Public License 1.0 — https://github.com/junit-team/junit4

Research acknowledgements are listed in `SOURCE_REFERENCES.md`. Acknowledgement does not mean that reference code is bundled in this repository.

## iOS platform adapter (1.1.0)

The iOS target uses Apple system frameworks (SwiftUI, UIKit, Network, VideoToolbox, CoreVideo, CoreMedia and Metal) through the public Xcode SDK. SF Symbols are referenced as system symbols on Apple platforms; no SF Symbols image files are copied into the Android project. Apple SDK, system libraries and symbols remain subject to their own terms. No vendor camera SDK, proprietary camera LUT, certificate, private key or community app source is bundled. Local protocol and Metal tests are project-authored.
