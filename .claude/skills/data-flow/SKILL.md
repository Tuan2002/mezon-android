---
name: mezon-android-data-flow
description: >-
  Guides Mezon Android data and reactive architecture — controller-owned caches,
  NotificationCenter, Room dual-write, SocketEventDispatcher, and REST via MezonApi.
  Use when adding features that touch network, persistence, sockets, or screen updates;
  when refactoring ChatController, *Controller classes, DAOs, or fragment observers.
---

# Mezon Android — data flow

Mezon Android is **not** MVVM-with-`StateFlow` for primary screens. The stack is Telegram-style: **singleton controllers** own in-memory state, **Room** persists asynchronously, and **NotificationCenter** drives UI on the main thread.

## Mental model

| Piece | Role | Typical location |
|-------|------|------------------|
| Controllers | `@Singleton` Hilt services: caches (`ArrayList`, `LongSparseArray`, etc.), REST, socket reactions, Room writes | e.g. `home/ChatController.kt`, `home/DialogsController.kt`, `home/clans/ChannelController.kt` |
| `NotificationCenter` | Per-account and global event bus; **integer event IDs** defined in companion with `nextId()` | `core/NotificationCenter.kt` |
| `BaseFragment` | Non–AndroidX “fragment”; `observe(eventId)` / Hilt via `inject` + `FragmentEntryPoint` | `core/BaseFragment.kt` |
| `SocketEventDispatcher` | Multiplexes `MezonSocket` into typed `SharedFlow`s for controllers | `network/SocketEventDispatcher.kt` |
| `MezonApi` / proto | REST and generated DSL (`com.mezon.mezon.api.*`, realtime `com.mezon.mezon.rtapi.*`) | `network/` |
| `ApiCacheTracker` | Prevents duplicate hot REST paths where already integrated | `network/ApiCacheTracker.kt` |
| Room | WAL, `@Upsert`, bounded queries in existing patterns | `data/db/` |

## End-to-end diagram

```text
WebSocket ──► SocketEventDispatcher (SharedFlows) ──► Controller (merge caches + Room on io)
REST      ──► MezonApi ──► Controller ──► NotificationCenter (main thread)
Room      ──► Controller init / cold paths ──► caches + NC events

NotificationCenter ──► BaseFragment.observe ──► adapters / cells / partial updates
```

## Rules for agents

1. **Dual-write** — Update in-memory structures first on the right dispatcher; persist with Room from **`@IoDispatcher`** / `withContext(ioDispatcher)`. Never block the main thread on DB I/O.

2. **Notify UI from controllers** — After meaningful cache changes, post `NotificationCenter` on the main thread (`postNotificationOnMainThread`, `postNotificationNameOnUIThread`, etc.). **New event IDs**: add constants only in `NotificationCenter` companion using the existing `private fun nextId() = totalEvents++` pattern next to peers like `messagesDidLoad`.

3. **Subscribe from fragments** — Use `observe(eventId) { … }` on `BaseFragment`. Use global instance (`NotificationCenter.getGlobalInstance()` / `getGlobalNotificationCenter()`) when the event is account-global. Rely on existing `BaseFragment` teardown so observers do not leak.

4. **Sockets** — Do not bypass `SocketEventDispatcher` for inbound typing. Controllers should collect its `SharedFlow`s from `@ApplicationScope` coroutines, mirroring `ChatController`’s `init { appScope.launch { observeIncomingMessages() } … }`.

5. **REST** — Respect `ApiCacheTracker` and helpers like `apiCacheKey` where the codebase already gates calls; avoid parallel ad-hoc caches.

6. **Cold start** — Load bounded data from DAOs in controller init, then refresh from network; copy the surrounding controller file’s style.

7. **New features** — Prefer extending an existing **controller** + **NotificationCenter events** + **fragment observers**. Do not introduce a parallel `StateFlow`-per-screen architecture for primary flows unless the project explicitly migrates.

## Dependency injection reminders

- Controllers: `@Singleton` + `@Inject constructor`, often with `@IoDispatcher` and `@ApplicationScope`.
- `BaseFragment` subclasses: call `inject(context)` before `entryPoint()`; use `FragmentEntryPoint`, not constructor-injected singletons.

## Quick reference — `ChatController`

Reference implementation: `home/ChatController.kt` — combines `MessageDao`, `SocketEventDispatcher`, `ApiCacheTracker`, `NotificationCenter`, and `appScope`/`ioDispatcher` in `init`.

## Examples

Patterns below are abbreviated; copy field names, dispatchers, and notification arity from the nearest real caller (e.g. `ChatController`, `DeviceManageFragment`).

### New `NotificationCenter` event ID

Add one line in the companion next to existing IDs (`core/NotificationCenter.kt`):

```kotlin
// Inside companion object, after other nextId()-backed vals:
val myFeatureDidChange = nextId()
```

### Controller: socket → cache / Room → main-thread notify

Mirror `ChatController`’s `init` and `observeIncomingMessages()` style:

```kotlin
@Singleton
class MyFeatureController @Inject constructor(
    private val myDao: MyDao,
    private val socketEventDispatcher: SocketEventDispatcher,
    private val notificationCenter: NotificationCenter,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val cache = LongSparseArray<MyEntity>()

    init {
        appScope.launch { observeSocket() }
    }

    private suspend fun observeSocket() {
        // Use a real SharedFlow from SocketEventDispatcher (e.g. channelMessages, typingEvents, …).
        socketEventDispatcher.someSharedFlow.collect { event ->
            val entity = event.toEntity()
            synchronized(this) { cache.put(entity.id, entity) }
            appScope.launch(ioDispatcher) { myDao.upsert(entity) }
            notificationCenter.postNotificationOnMainThread(
                NotificationCenter.myFeatureDidChange,
                entity.id
            )
        }
    }
}
```

### REST path gated by `ApiCacheTracker`

Same idea as `ChatController.loadMessages` offline / skip-cache branches:

```kotlin
val cacheKey = apiCacheKey("listThings", channelId)

appScope.launch(ioDispatcher) {
    if (!networkMonitor.isOnline.value) {
        val rows = myDao.listLatest(channelId, limit)
        notificationCenter.postNotificationOnMainThread(
            NotificationCenter.messagesDidLoad, // or your event
            channelId,
            ArrayList(rows),
            /* …match existing arg pattern for that id… */
        )
        return@launch
    }
    if (!forceRefresh && cacheTracker.shouldCall(cacheKey) == ApiCacheTracker.ShouldCall.SKIP) {
        val rows = myDao.listLatest(channelId, limit)
        if (rows.isNotEmpty()) {
            notificationCenter.postNotificationOnMainThread(
                NotificationCenter.messagesDidLoad,
                channelId,
                ArrayList(rows),
                /* … */
            )
            return@launch
        }
    }
    // …network fetch, dao upsert, invalidate(cacheKey) as appropriate…
}
```

### Fragment: `observe` in `onFragmentCreate`

From `DeviceManageFragment`-style screens (`home/profile/DeviceManageFragment.kt`):

```kotlin
override fun onInject(entryPoint: FragmentEntryPoint) {
    myController = entryPoint.myController()
}

override fun onFragmentCreate(): Boolean {
    super.onFragmentCreate()
    observe(NotificationCenter.myFeatureDidChange) { _, _, args ->
        val id = args.getOrNull(0) as? Long ?: return@observe
        refreshRowFor(id)
    }
    return true
}
```

Use `observeGlobal(eventId) { … }` when handling account-global events (same pattern, global NC instance).
