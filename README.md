# Marea AI

Native Android assistant (Kotlin + Jetpack Compose) with an animated 2D avatar built from your own character image, voice input/output and a configurable AI backend. Buildable entirely from a phone through GitHub Actions.

## Status (honest)
- Source code is complete, but it **has not been compiled or run yet**. The authoring environment had no internet/Android SDK. The first GitHub Actions run is the first real build. If it fails, open the `marea-ai-build-logs` artifact (or the log), find the *first* `e:` error line and fix that file.
- No Gradle wrapper JAR is committed (a binary file cannot be generated offline). The workflow installs pinned **Gradle 8.9** itself, which is compatible with Android Gradle Plugin 8.5.2.

## Versions
AGP 8.5.2 · Gradle 8.9 · Kotlin 1.9.24 · Compose compiler 1.5.14 · Compose BOM 2024.06.00 · JDK 17 · compileSdk/targetSdk 34 · minSdk 26 · app id `com.marea.ai`

## File tree
```
settings.gradle.kts / build.gradle.kts / gradle.properties
.github/workflows/android.yml        CI: tests + debug APK
app/build.gradle.kts
app/src/main/AndroidManifest.xml     permissions: RECORD_AUDIO, INTERNET
app/src/main/res/drawable-nodpi/marea_character.webp   avatar artwork
app/src/main/res/mipmap-*/ic_launcher.png              icon (face crop)
app/src/main/java/com/marea/ai/
  MainActivity.kt, ChatViewModel.kt
  avatar/AvatarState.kt   state model (IDLE, LISTENING, THINKING, SPEAKING, ERROR)
  avatar/MareaAvatar.kt   artwork + animated overlays; AvatarRig = feature positions
  voice/SpeechInput.kt    SpeechRecognizer wrapper
  voice/SpeechOutput.kt   SpeechOutput interface + Android TextToSpeech
  ai/AiService.kt         AiService interface, chat-completions client, "unconfigured" service
  data/                   models, settings (SharedPreferences), JSON history file
  ui/                     theme, main screen, settings screen
app/src/test/.../MareaUnitTests.kt
```

## Upload from an Android phone
1. Create a new empty repository on github.com (mobile browser, "Desktop site" helps).
2. Unzip `MareaAI.zip` on the phone (Files app) and use **Add file → Upload files** to upload the folders. Make sure `.github/workflows/android.yml` ends up at that exact path (hidden-folder uploads can fail in the browser: if so, create the file with **Add file → Create new file**, type the path `.github/workflows/android.yml`, and paste the content).
3. Commit to the `main` branch.

## Build and download the APK
1. Repository → **Actions** → *Build Marea AI debug APK* → **Run workflow** (it also runs on every push to `main`).
2. When it is green, open the run → **Artifacts** → `marea-ai-debug-apk`, download, unzip, install the APK (allow "install unknown apps").

## Secrets
None required. API details are typed into the app (Settings → AI connection) and stored only on the phone. Anything embedded in an APK (including via GitHub secrets) can be extracted, so for a shared app put the key on your own HTTPS backend.

## Avatar
Static artwork with animated overlays: eyelids (blink, half-closed when thinking/error), mouth opening while TTS is really playing, accent border per state. Eyebrow movement is not implemented (flat artwork). If overlays look misaligned on a different image, edit the fractions in `AvatarRig` in `MareaAvatar.kt`.

## Features and limits
| Feature | Notes |
|---|---|
| Chat UI, settings, dark theme, 2 accents | done |
| Voice input | Android SpeechRecognizer; mic permission asked on tap with explanation; availability checked |
| Voice output | Android TTS; uses the phone's voice/language (Hindi voice must be installed on the phone) |
| AI | generic `{baseUrl}/chat/completions` client; you supply URL/model/key; shows honest error if unconfigured |
| History | JSON file, latest 200 messages, clear button, can be disabled |
| Not done | cloud TTS, phoneme lip-sync, long-term AI memory, backend server, emulator/UI tests |

## Privacy
Local: history, settings, key. Sent to your configured AI provider: your messages and recent context. Phone speech services may use their vendor's cloud. No background recording.

## Testing report
Unit tests exist for avatar state resolution, empty input, AI error/parse handling, https enforcement and history save/load/clear/corruption. **None have been executed yet.** They run in CI via `:app:testDebugUnitTest`. Not covered: UI/emulator tests, permission-denial flow and TTS-failure flow (need an emulator or device).
