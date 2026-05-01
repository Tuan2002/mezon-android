---
name: mezon-android-ui-design
description: >-
  Guides Mezon Android primary UI — BaseFragment, BaseCell, ThemeColors, ActionBarLayout,
  custom View drawing and list performance. Use when building or changing screens,
  list rows, theming, or touch behavior; when deciding between Views vs Compose.
---

# Mezon Android — UI design and implementation

Primary UI is **custom `View` / `ViewGroup`** (Telegram-style). **Jetpack Compose is not** the stack for main screens unless the project explicitly migrates.

## Core types

| Type | Package / path | Purpose |
|------|------------------|---------|
| `BaseFragment` | `core/BaseFragment.kt` | Screens: `themeColors`, `LayoutHelper`, `NotificationCenter.observe`, stack managed with `ActionBarLayout` / `MainActivity` — not Navigation Component as the main chat shell |
| `BaseCell` | `core/BaseCell.kt` | List rows and dense blocks: `Canvas` drawing, long-press / haptics, `invalidateLite`, `listenInvalidate` |
| `ThemeColors` | `core/ThemeColors.kt` | `@Singleton` shared `TextPaint` / `Paint`, palette keys; reuse instance paints — no per-frame allocations |
| `LayoutHelper` | `core/LayoutHelper.kt` | `dp` / `sp` and layout helpers — match adjacent code |
| Reusable cells | Often `ui/cells/*` or feature-local | Subclass `BaseCell` or established cell types |

## Performance rules

1. **`onDraw` / drawing** — No per-frame allocations. Reuse `StaticLayout`-style patterns, rects, and paints from `ThemeColors` or stable instance fields.

2. **Invalidation** — Prefer targeted `invalidate`, `invalidateLite`, and `listenInvalidate` callbacks. Where adapters support it, use partial updates (`updateVisibleRows`, masks) instead of resetting whole lists.

3. **`BaseCell` render cache** — Respect `drawCached`, `allowCaching()`, `forceNotCacheNextFrame`, and `updatedContent`. Only bypass caching when content truly changed.

4. **Lists** — Prefer `DiffUtil` and the same visible-row update APIs existing adapters use.

## Theme and chrome

- Theme updates flow through **`NotificationCenter`** (e.g. `themeChanged` pattern in project rules); action bars wrapped with **`wrapWithActionBar`** should follow existing subscription patterns.
- `ThemeColors` exposes `ResourcesProvider` for resolved colors; cells and fragments should use theme keys consistently.

## Touch and interaction

- `BaseCell` owns long-press scheduling (`startCheckLongPress`, `cancelCheckLongPress`) and haptics. When subclassing, extend with **`super`** touch patterns unless replacing behavior deliberately.

## Rules for agents

- **Do not** add Compose as the primary UI for new features unless migration is explicit; integrate with `BaseFragment` + custom views.
- **New row UI** → `BaseCell` (or an established subclass), wired through `ThemeColors`.
- Match **`LayoutHelper`** and dimension helpers (`dp`, `sp`) used next to your edits.

## Naming alignment

Screens are `*Fragment` extending **`BaseFragment`** (not AndroidX `Fragment`). Mirror package layout under `com.mezon.mobile` per neighboring files.

## Examples

### Screen: inject, themed chrome, `NotificationCenter`

Condensed from `home/profile/DeviceManageFragment.kt`:

```kotlin
class MySettingsFragment : BaseFragment() {

    private lateinit var controller: MyController

    override fun onInject(entryPoint: FragmentEntryPoint) {
        controller = entryPoint.myController()
    }

    override fun onFragmentCreate(): Boolean {
        super.onFragmentCreate()
        observe(NotificationCenter.themeChanged) { _, _, _ ->
            fragmentView?.setBackgroundColor(themeColors.background)
            // …invalidate list cells or notify adapter…
        }
        return true
    }

    override fun createView(context: Context): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColors.background)
            // …add cells / child views…
        }
        return wrapWithActionBar(getString(R.string.my_screen_title), content)
    }
}
```

`wrapWithActionBar` (in `BaseFragment`) already subscribes to `NotificationCenter.themeChanged` for the bar — add extra `observe` calls only for content the bar does not repaint.

### `BaseCell`: reuse `ThemeColors` paints, mark content dirty

Subclasses draw in `dispatchDraw` / `onDraw` using paints from `ThemeColors`; avoid allocating `Paint` in draw methods. When model data changes, bump `updatedContent` so caching can refresh (see `core/BaseCell.kt`).

```kotlin
class TitleSubtitleCell(context: Context, private val theme: ThemeColors) : BaseCell(context) {

    var title: String = ""
        set(value) {
            if (field == value) return
            field = value
            updatedContent = true
            forceNotCacheNextFrame()
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        val x = LayoutHelper.dp(16f)
        var y = LayoutHelper.dp(12f)
        canvas.drawText(title, x, y, theme.dialogNamePaint)
        y += theme.dialogNamePaint.textSize + LayoutHelper.dp(4f)
        // …second line with theme.dialogMessagePaint, etc….
    }
}
```

### Targeted invalidation from a parent list

When a row needs to trigger a lightweight parent relayout:

```kotlin
cell.listenInvalidate {
    parentListView.invalidate()
}
cell.invalidateLite() // optional: skip invalidateCallback chain if you only need self
```

Adapters that support it should prefer partial row updates (`updateVisibleRows` / masks) over `notifyDataSetChanged()` when-only a few rows change — follow the existing adapter next to your feature.
