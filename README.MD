# Mezon Android

Native Android client for the Mezon messaging platform. The app is built in **Kotlin** with a **Telegram-style** UI layer: custom `Canvas`-based cells, a custom navigation stack, and **Room** for offline-first persistence. Architecture centers on controllers, `NotificationCenter`, and custom views (no Jetpack Compose for primary UI).

---

## Table of contents

- [Overview](#overview)
- [Requirements](#requirements)
- [Getting started](#getting-started)
- [Protocol buffer setup](#protocol-buffer-setup)
- [Firebase configuration](#firebase-configuration)
- [Build and test](#build-and-test)
- [Updating `mezon-protocol`](#updating-mezon-protocol)
- [Architecture](#architecture)
- [Repository layout](#repository-layout)
- [Technology stack](#technology-stack)
- [Performance notes](#performance-notes)
- [Protocol and networking](#protocol-and-networking)

---

## Overview

| Area | Approach |
|------|----------|
| **UI** | Custom `View` / `ViewGroup` cells (`BaseCell`), `StaticLayout`, shared paints from `ThemeColors` — optimized for list scrolling |
| **State** | `@Singleton` controllers hold in-memory caches (`ArrayList`, `LongSparseArray`); UI updates via `NotificationCenter` (not `StateFlow` for screens) |
| **Persistence** | Dual-write: update memory first, then async Room `@Upsert` on I/O dispatcher |
| **Realtime** | OkHttp WebSocket with protobuf `Envelope`; events fanned out in `SocketEventDispatcher` |
| **Remote API** | Ktor + OkHttp, protobuf request/response bodies |

---

## Requirements

| Tool | Notes |
|------|--------|
| **Android Studio** | Current stable channel (e.g. Ladybug or newer) |
| **JDK** | 17+ (bundled with Android Studio is fine) |
| **Android SDK** | `compileSdk 35`, `minSdk 24` (see `app/build.gradle.kts`) |
| **Kotlin** | 1.9.x (see `gradle/libs.versions.toml`) |

---

## Getting started

### 1. Clone this repository

```bash
git clone <mezon-android-repository-url> ~/AndroidStudioProjects/mezon
```

### 2. Clone `mezon-protocol` (required for code generation)

Place the protocol repo anywhere on disk and keep the **absolute path** — the `:core-proto` module uses symlinks to it.

```bash
git clone https://github.com/mezonai/mezon-protocol.git ~/dev/mezon-protocol
```

### 3. Create proto symlinks

The Gradle Protobuf plugin resolves imports using **absolute** symlink targets (do not use relative paths like `../../../`).

```bash
cd ~/AndroidStudioProjects/mezon
mkdir -p core-proto/src/main/proto

PROTO_DIR="/absolute/path/to/mezon-protocol"   # set to your actual clone

ln -sf "$PROTO_DIR/api"   core-proto/src/main/proto/api
ln -sf "$PROTO_DIR/rtapi" core-proto/src/main/proto/rtapi
```

Example:

```bash
ln -sf /Users/<you>/dev/mezon-protocol/api   core-proto/src/main/proto/api
ln -sf /Users/<you>/dev/mezon-protocol/rtapi core-proto/src/main/proto/rtapi
```

Verify:

```bash
ls -la core-proto/src/main/proto/
# api   -> /absolute/path/to/mezon-protocol/api
# rtapi -> /absolute/path/to/mezon-protocol/rtapi
```

### 4. Open and sync

Open the `mezon/` directory in Android Studio and let Gradle sync complete.

---

## Protocol buffer setup

- Generated sources live in the **`:core-proto`** module.
- Symlinks under `core-proto/src/main/proto/` must point to the **`api/`** and **`rtapi/`** trees inside `mezon-protocol`.

---

## Firebase configuration

`app/google-services.json` is not committed. Obtain it from the Firebase project (e.g. `mezon-772fa`) or from your team, then place it at:

```text
mezon/app/google-services.json
```

Copy `mezon/mezon.secrets.properties.example` to `mezon/mezon.secrets.properties` and fill in values (the real file is not committed; obtain from your team or internal secret store). Gradle fails early if this file is missing.

---

## Build and test

All commands are run from the `mezon/` directory:

```bash
cd mezon

./gradlew assembleDebug
./gradlew installDebug
./gradlew assembleRelease
./gradlew test
```

For a full debug build including proto generation:

```bash
./gradlew assembleDebug
```

---

## Updating `mezon-protocol`

When the protocol repository changes, pull and regenerate:

```bash
cd /path/to/mezon-protocol
git pull origin main

cd /path/to/mezon
./gradlew :core-proto:generateDebugProto
./gradlew app:compileDebugKotlin
```

---

## Architecture

Data flow (high level):

```text
WebSocket ──► SocketEventDispatcher ──► Controller (cache + async Room)
REST     ──► Controller                ──► NotificationCenter.postOnMainThread
Room     ──► Controller init / cold load ──► same caches + UI events

NotificationCenter ──► BaseFragment.observe() ──► adapters / cell.invalidate / partial row updates
```

### Layers

| Layer | Responsibility |
|-------|------------------|
| **Controller** | `@Singleton` services: synchronized in-memory models, REST and socket side effects, dual-write to Room, post NC events from `init` / API / socket |
| **NotificationCenter** | Main-thread event bus (integer event IDs); fragments register with `BaseFragment.observe()` |
| **BaseFragment** | Custom lifecycle (managed by `ActionBarLayout`, not `AndroidX FragmentManager` for the main stack) |
| **Cells** | `BaseCell` subclasses: `onDraw(Canvas)`, `update(mask)`, shared theme paints |
| **Room** | WAL, `@Upsert`, bounded list queries (e.g. message cap per channel) |
| **SocketEventDispatcher** | Demultiplexes `Envelope` into typed `SharedFlow`s for controllers |

### Navigation

Single-activity: **`MainActivity`**. Screen stack and transitions use **`ActionBarLayout`** (custom `ArrayList` of `BaseFragment`, animated transitions, swipe-back). This is not the AndroidX Navigation Component graph.

### Example `NotificationCenter` consumers

| Event (examples) | Typical publisher | Typical subscriber |
|--------------------|-------------------|--------------------|
| `dialogsNeedReload` | `DialogsController` / messages pipeline | `MessagesFragment` |
| `messagesDidLoad` / new/update/delete | `ChatController` | `ChatFragment` |
| `clansDidLoad` / channels | `ClansController` / `ChannelController` | `ClansFragment` |
| `themeChanged` / `languageChanged` | `ThemeManager` / `LocaleManager` | Fragments via rebuild or observers |
| `connectionStateChanged` | `ConnectionController` | Shell / home |
| `sessionExpired` | `AuthRepository` | `MainActivity` |

The canonical list and IDs live in `NotificationCenter` and related controllers.

---

## Repository layout

```text
app/src/main/java/com/mezon/mobile/
├── MainActivity.kt
├── MezonApplication.kt
├── auth/                 # AuthRepository, LoginFragment
├── core/                 # BaseFragment, ActionBarLayout, NotificationCenter, ThemeColors, BaseCell, …
├── data/db/              # MezonDatabase, DAOs, entities
├── di/                   # Hilt modules, dispatchers
├── home/                 # Controllers, MainTabsActivity, chat / messages / clans / profile / notifications
├── network/              # MezonApi, MezonSocket, SocketEventDispatcher, ApiCacheTracker
├── notification/        # FCM, local notifications, active channel
├── session/              # SessionManager, theme, locale
├── ui/cells/             # Reusable custom views (action bar, settings rows, …)
└── util/                 # ContentParser, image helpers, …
```

A fuller map of types may exist in the parent workspace (e.g. `CLAUDE.md` at the monorepo root, if present).

---

## Technology stack

| Area | Technology |
|------|------------|
| Language | Kotlin 1.9.x |
| Build | Gradle with Kotlin DSL, **KSP** (Room, Hilt) — versions in `gradle/libs.versions.toml` |
| DI | Hilt |
| HTTP | Ktor + OkHttp |
| WebSocket | OkHttp, binary protobuf |
| DB | Room, WAL, `@Upsert` |
| Images | Custom `MezonImageLoader` (OkHttp, memory + disk cache) + `ImageReceiver` / `AvatarDrawable` |
| Session | DataStore Preferences |
| Push | Firebase Cloud Messaging (see BOM in version catalog) |
| Messages | Protobuf Lite (`:core-proto`) |
| Concurrency | Kotlin coroutines, `@ApplicationScope` / `@IoDispatcher` |

---

## Performance notes

- **Lists**: `DiffUtil` where appropriate; `updateVisibleRows(mask)`-style partial updates to avoid full adapter churn; scroll-state guards where implemented.
- **Controllers**: in-memory update first, Room write off the main thread.
- **Cold start**: load bounded data from Room in controllers, then refresh from network.
- **API**: `ApiCacheTracker` (TTL) to reduce duplicate REST work.
- **Canvas**: avoid per-frame allocations in `onDraw`; reuse layouts and paints per cell instance as documented in project guidelines.

---

## Protocol and networking

| Artifact | Role |
|----------|------|
| `api/api.proto` | REST messages (`com.mezon.mezon.api`) |
| `rtapi/realtime.proto` | WebSocket `Envelope` and realtime payloads (`com.mezon.mezon.rtapi`) |

- **WebSocket** (illustrative): `wss://<ws_url>/ws?token=<token>&status=true&platform=1&lang=en&format=protobuf`
- **REST**: `Content-Type: application/proto` with bearer token (auth flows may use JSON + Basic as defined by the API).

---

*Internal engineering documentation: keep setup steps in sync with `gradle/libs.versions.toml` and `app/build.gradle.kts` when versions change.*
