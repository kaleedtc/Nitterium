# Nitterium - Project Context

## Project Overview

**Nitterium** is a native Android application developed in **Kotlin**. It is a modern Android project utilizing **Jetpack Compose** for its UI, adhering to current Android development best practices. The app functions as a privacy-focused wrapper for Nitter instances, allowing users to browse Twitter/X content without an account. It features a hybrid approach using native navigation and state management combined with a WebView for content rendering.

## Architecture

The application follows the **MVI (Model-View-Intent)** architectural pattern with **Unidirectional Data Flow (UDF)**.

* **Single Activity:** `MainActivity` is the sole entry point, hosting a Compose `NavHost`.
* **UI Layer:** Built with Jetpack Compose. Each feature (screen) has a `Contract` defining:
  * **State:** Immutable data class representing the UI.
  * **Event:** Actions triggered by the user (Intent).
  * **Effect:** One-time side effects (e.g., Navigation, Snackbars).
* **ViewModel:** Handles business logic, processes Events, updates State, and emits Effects.
* **Data Layer:** Repositories abstracting data sources (DataStore, File System).

## Tech Stack

* **Language:** Kotlin
* **UI Framework:** Jetpack Compose (Material 3)
* **Navigation:** Jetpack Compose Navigation (Type-safe routes)
* **Build System:** Gradle (Kotlin DSL - `.gradle.kts`)
* **Async/Concurrency:** Kotlin Coroutines & Flows
* **Image Loading:** Coil 3
* **Persistence:**
  * **Settings:** Jetpack DataStore (Preferences)
  * **Subscriptions:** JSON serialization (Kotlinx Serialization) to local file storage.
* **Networking:** OkHttp (via Coil), WebView (Content)
* **Minimum SDK:** 31 (Android 12)
* **Target/Compile SDK:** 36 (Android 15)
* **JDK Version:** Java 11

## Key Files & Directories

### Root Directory

* `build.gradle.kts`: Root-level build configuration. Defines plugins.
* `settings.gradle.kts`: Project settings, including module definitions (`:app`).
* `gradle/libs.versions.toml`: Version catalog for dependency management.

### App Module (`app/`)

* `src/main/AndroidManifest.xml`: Defines `MainActivity` and Intent Filters for deep linking (`twitter.com`, `www.twitter.com`, `mobile.twitter.com`, `x.com`, `nitter.net`, `t.co`, `xcancel.com`, `vxtwitter.com`, `fxtwitter.com`).

* `src/main/java/com/kaleedtc/nitterium/`:
  
  * `NitteriumApplication.kt`: Application class. Initializes repositories (`SubscriptionRepository`, `UserPreferencesRepository`) and ImageLoader.
  * `MainActivity.kt`: Entry point. Sets up the Theme and `NitteriumApp`.
  * `MainViewModel.kt`: Manages global app state (Theme settings).
  
  #### UI Layer (`ui/`)
  
  * `NitteriumApp.kt`: Main scaffold. Contains `NavHost` and Bottom Navigation.
  * `common/`: Reusable components (e.g., `NitterWebView`, `MviViewModel`).
  * `navigation/`: Type-safe navigation routes (`Destinations.kt`).
  * `feature/`: Feature-specific modules.
    * `search/`: Home screen & WebView wrapper. Handles deep links and subscription toggling.
    * `subscriptions/`: Lists saved users.
    * `settings/`: App configuration (Instance URL, Theme).
    * `profile/`: User profile view (reused logic from Search/WebView).
  
  #### Data Layer (`data/`)
  
  * `repository/`:
    * `SubscriptionRepository.kt`: Manages subscriptions stored in `subscriptions.json`.
    * `UserPreferencesRepository.kt`: Manages instance URL and theme settings using DataStore.
  * `model/`: Data classes (e.g., `Subscription`).

## Building and Running

The project uses the Gradle Wrapper (`gradlew`).

### Common Commands

* **Build Debug APK:**
  
  ```bash
  ./gradlew assembleDebug
  ```
  
  Output location: `app/build/outputs/apk/debug/app-debug.apk`

* **Run Unit Tests:**
  
  ```bash
  ./gradlew test
  ```

* **Run Android Tests (Instrumented):**
  
  ```bash
  ./gradlew connectedAndroidTest
  ```

* **Lint Check:**
  
  ```bash
  ./gradlew lint
  ```

## Development Conventions

* **UI:** 100% Jetpack Compose.
* **State Management:** Strict MVI. State is immutable.
* **Navigation:** Use Type-safe arguments defined in `ui/navigation/Destinations.kt`.
* **Theming:** Material 3. Theme customization should be done in `app/src/main/java/com/kaleedtc/nitterium/ui/theme/`.
* **Code Style:** Standard Kotlin conventions.