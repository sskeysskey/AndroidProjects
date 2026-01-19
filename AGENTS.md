# AGENTS.md - Context & Rules for AI Agents

This file defines the environment, coding standards, and operational protocols for AI agents (Sisyphus, Cursor, Copilot) working in this repository.

## 1. Project Structure & Tech Stack

This repository contains multiple distinct Android projects. Agents must identify which project they are working in before modifying code.

### A. ONews (Modern App)
- **Path**: `/ONews`
- **Language**: 100% Kotlin
- **UI Framework**: **Jetpack Compose** (Material 3). NO XML layouts.
- **Architecture**: MVVM (ViewModel + StateFlow). Manual Dependency Injection.
- **Networking**: Retrofit 2 + OkHttp + Gson.
- **Async**: Coroutines & Flow.
- **Build System**: Gradle with **Version Catalogs** (`libs.versions.toml`).

### B. SpaceWar & YGtetris (Legacy/Games)
- **Path**: `/SpaceWar`, `/YGtetris`
- **Language**: Kotlin.
- **UI Framework**: Custom Views (`Canvas` rendering) + XML for container Activities.
- **Architecture**: Game Loop pattern. 
    - `SpaceWar`: `Manager` + `Entity` classes.
    - `YGtetris`: separated `State`, `Logic`, `Rendering`.
- **Build System**: Standard Gradle (Groovy DSL).

---

## 2. Build, Test, and Lint Commands

Execute these commands from the specific project root (e.g., inside `ONews/`).

### Common Commands
| Action | Command |
|--------|---------|
| **Build Debug APK** | `./gradlew assembleDebug` |
| **Run All Unit Tests** | `./gradlew testDebugUnitTest` |
| **Run Lint Checks** | `./gradlew lintDebug` |
| **Clean Project** | `./gradlew clean` |

### Running Specific Tests
To run a single test class or method, use the `--tests` filter:

```bash
# Run a specific test class
./gradlew testDebugUnitTest --tests "com.sskeysskey.onews.NewsViewModelTest"

# Run a specific test method
./gradlew testDebugUnitTest --tests "com.sskeysskey.onews.NewsViewModelTest.fetchNews_success"
```

*Note: If working in ONews, ensure you are using the correct namespace `com.sskeysskey.onews`.*

---

## 3. Code Style & Guidelines

### General Kotlin Rules (All Projects)
- **Indentation**: 4 spaces.
- **Imports**: No wildcard imports (`import java.util.*`). Explicitly import used classes.
- **Null Safety**: 
  - Use `?` for nullable types.
  - Use `?.` for safe calls.
  - **Avoid `!!`** operator. Use `?:` (Elvis operator) or `requireNotNull`.
- **Concurrency**: Use `Coroutines` (`viewModelScope`, `lifecycleScope`). Avoid `Thread` or `AsyncTask`.

### Jetpack Compose Guidelines (ONews Only)
1.  **State Hoisting**: Keep Composables stateless where possible. Pass data in, pass events out.
    ```kotlin
    // Good
    @Composable
    fun NewsItem(news: News, onClick: () -> Unit) { ... }
    ```
2.  **Preview**: Always include a `@Preview` for UI components.
3.  **Modifiers**: Pass a `modifier: Modifier = Modifier` as the first optional argument to every Composable.
4.  **Side Effects**: Use `LaunchedEffect` for one-off events. Use `remember` to cache expensive calculations.

### Game Development Guidelines (SpaceWar / YGtetris)
1.  **Rendering**: Graphics logic belongs in the `View`'s `onDraw` or a dedicated Renderer class.
2.  **Logic Separation**: Keep game logic (physics, state) separate from Android View code if possible (reference `YGtetris` pattern).
3.  **Performance**: Avoid object allocation inside `onDraw` (causes GC stutter).

### Error Handling
- **Network**: Wrap Retrofit calls in `try-catch` blocks handling `HttpException` and `IOException`.
- **Serialization**: Be tolerant of missing JSON fields. Use nullable types in Data Classes if the API is unstable.
- **Logging**: Use `Log.e(TAG, "msg", exception)` for errors. Do not swallow exceptions silently.

---

## 4. Agent Operational Protocols

### File Editing
- **Atomic Changes**: Do not mix refactoring with logic changes.
- **Verification**: ALWAYS run `lsp_diagnostics` on the file you just edited.
- **Imports**: If you add a library usage, check `build.gradle` (or `libs.versions.toml` for ONews) to ensure the dependency exists.

### Creating New Files
- **Compose**: Place new Screens in a `ui/screens` package (if exists) or grouping by feature.
- **ViewModels**: Name as `FeatureViewModel`.
- **Tests**: Mirror the source package structure in `src/test/java`.

### Dependency Management
- **ONews**: Add dependencies to `gradle/libs.versions.toml` first, then reference in `build.gradle.kts`.
    ```toml
    [libraries]
    new-lib = { group = "com.example", name = "library", version.ref = "newLibVersion" }
    ```
- **Others**: Add directly to `dependencies { ... }` in `build.gradle`.

---

## 5. Known Issues / Context
- **ONews**: Uses manual Dependency Injection (Factories) instead of Hilt. Respect this pattern.
- **Games**: `SpaceWar` has simpler architecture than `YGtetris`. When porting features, prefer the `YGtetris` modular approach.
