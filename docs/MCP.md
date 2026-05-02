# MCP servers

This repo can use [Model Context Protocol](https://modelcontextprotocol.io/) servers while working in **Cursor**, **[Claude Code](https://code.claude.com/docs/en/mcp)**, or any other MCP-capable client:

| Server | Purpose |
|--------|---------|
| **[FadCat](https://github.com/anonfaded/FadCat)** | Logcat, device listing, log search/analysis, FadCam media tooling, performance helpers — debugging-oriented. |
| **[android-mcp](https://github.com/CursorTouch/Android-MCP)** | UI automation via **ADB** + **uiautomator2** (tap, swipe, type, device state, notifications) — device control like a user. |

The checked-in **Cursor** project config lives at **[`.cursor/mcp.json`](../.cursor/mcp.json)**. **Claude Code** does not read that file; it uses **[`.mcp.json`](#claude-code)** at the repository root (project scope) or **`~/.claude.json`** (user/local scope). After installing the binaries below, restart the IDE or MCP connections as needed.

---

## Shared prerequisites

1. **Android device or emulator** — API level in line with the app (Android **10+** for `android-mcp` per upstream).
2. **USB debugging** (physical device) or running emulator — accept the **RSA fingerprint** prompt when `adb` connects.
3. **`adb` on `PATH`** — from Android SDK platform-tools. Verify:

   ```bash
   adb version
   adb devices
   ```

   You should see at least one device in `device` state (not `unauthorized`). If stuck: `adb kill-server && adb start-server`, reconnect cable, retry.

FadCat can use a **bundled ADB** on supported installs; `android-mcp` still expects a working ADB/uiautomator2 setup for automation.

---

## FadCat — install and setup

FadCat runs an **stdio MCP server** with:

```bash
fadcat --mcp
```

### Install the `fadcat` command

1. **Recommended:** install from the **[GitHub releases](https://github.com/anonfaded/FadCat/releases)** for your OS (macOS / Linux / Windows). Use the packaged build so the `fadcat` CLI is registered.
2. **From source (developers):** clone [FadCat](https://github.com/anonfaded/FadCat), then:

   ```bash
   pip3 install -r requirements.txt
   python3 FadCat.py
   ```

   For MCP without the GUI entrypoint, follow FadCat’s “Local Dev” MCP snippet using `python3 -m src.mcp` and `PYTHONPATH` pointing at the repo — see their README **MCP Setup (IDE)**.

3. Confirm the CLI is visible to Cursor’s environment:

   ```bash
   which fadcat
   fadcat --mcp
   ```

   (The second command starts the server; it will appear to “hang” — that is normal for stdio MCP; stop with Ctrl+C when testing manually.)

### Cursor / MCP config

Use the same shape as [`.cursor/mcp.json`](../.cursor/mcp.json):

```json
{
  "mcpServers": {
    "fadcat": {
      "type": "stdio",
      "command": "fadcat",
      "args": ["--mcp"],
      "env": { "PYTHONUNBUFFERED": "1" }
    }
  }
}
```

- **`PYTHONUNBUFFERED`**: avoids buffered stdout breaking the MCP stdio protocol.
- If `fadcat` is not on the default PATH Cursor inherits, set `"command"` to an **absolute path** to the executable.

**Docs:** [FadCat README — MCP Setup](https://github.com/anonfaded/FadCat#-_-mcp-setup-ide)

---

## Android-MCP — install and setup

[Android-MCP](https://pypi.org/project/android-mcp/) is published on PyPI. It declares **Python ≥ 3.13** and depends on **uiautomator2** (and related libraries).

### 1. Install `uv` (recommended) or use `pipx`

The project config uses **`uvx`** so a global `pip install` is not required:

```bash
# macOS / Linux (example — see https://docs.astral.sh/uv/getting-started/installation/)
curl -LsSf https://astral.sh/uv/install.sh | sh
```

Verify:

```bash
uvx --version
```

### 2. One-shot run (what Cursor runs)

This matches [`.cursor/mcp.json`](../.cursor/mcp.json):

```bash
uvx --python 3.13 android-mcp
```

The first time, `uvx` downloads Python 3.13 (if needed) and the `android-mcp` package into its cache.

### 3. Device automation prerequisites (uiautomator2)

Upstream expects **uiautomator2** on the device side. If tools fail to control the UI, open the **[Android-MCP repository](https://github.com/CursorTouch/Android-MCP)** and follow their **Installation** / **Getting Started** (clone + `uv sync` path, or `uiautomator2` init steps) until `adb devices` shows your device and their quick tests pass.

Typical checklist:

- Emulator or device with **developer options** + **USB debugging**.
- `adb devices` shows `device`.
- Run any **ATX/uiautomator2** init command their README specifies (often required once per device).

### Cursor / MCP config

```json
{
  "mcpServers": {
    "android-mcp": {
      "command": "uvx",
      "args": [
        "--python",
        "3.13",
        "android-mcp"
      ]
    }
  }
}
```

If `uvx` is not on PATH inside Cursor, replace `"command"` with the absolute path from `which uvx`.

**Security note:** this server can drive the real device UI. Prefer **emulators** or **non-production** devices when letting agents execute tool calls.

---

## Enabling MCP in Cursor

1. **Project-level:** keep [`.cursor/mcp.json`](../.cursor/mcp.json) at the repo root (already committed here).
2. **User-level:** Cursor also supports global MCP settings in the app; use the same `mcpServers` JSON if you want these servers available in every workspace.
3. After edits, **restart Cursor** or use the command palette action to refresh MCP connections (wording may vary by version).
4. In the MCP panel, confirm **fadcat** and **android-mcp** show as connected; if not, open the log output and fix `command not found`, Python, or ADB errors.

---

## Claude Code

[Claude Code](https://code.claude.com/) stores MCP servers separately from **Claude Desktop** (`claude_desktop_config.json` on macOS under `~/Library/Application Support/Claude/`, etc.). For this CLI/editor flow, use **project** `.mcp.json`, **`claude mcp`**, or entries in **`~/.claude.json`**, per the [official MCP docs](https://code.claude.com/docs/en/mcp).

### Project file: `.mcp.json`

Add a **`.mcp.json`** file at the **repository root** (same level as `app/`) so everyone on the team can share the same definitions. The format matches the `mcpServers` object you use elsewhere:

```json
{
  "mcpServers": {
    "fadcat": {
      "type": "stdio",
      "command": "fadcat",
      "args": ["--mcp"],
      "env": { "PYTHONUNBUFFERED": "1" }
    },
    "android-mcp": {
      "command": "uvx",
      "args": ["--python", "3.13", "android-mcp"]
    }
  }
}
```

- Claude Code may **prompt for approval** the first time it loads project-scoped servers. To reset those choices: `claude mcp reset-project-choices`.
- You can use **`${VAR}`** / **`${VAR:-default}`** in `command`, `args`, and `env` for machine-specific paths (see [env expansion](https://code.claude.com/docs/en/mcp#environment-variable-expansion-in-mcpjson)).

Optional: keep this file in git next to [`.cursor/mcp.json`](../.cursor/mcp.json) so Cursor and Claude Code stay aligned (two entries to update when paths change).

### CLI: `claude mcp add` (stdio, project scope)

From the repo root, after [installing Claude Code](https://code.claude.com/docs/en/setup):

```bash
# FadCat — options must come before the server name; `--` separates the spawn command
claude mcp add --transport stdio --scope project --env PYTHONUNBUFFERED=1 fadcat -- fadcat --mcp

# android-mcp via uvx
claude mcp add --transport stdio --scope project android-mcp -- uvx --python 3.13 android-mcp
```

Manage and debug:

```bash
claude mcp list
claude mcp get fadcat
```

Inside an active Claude Code session, use the **`/mcp`** command to inspect server status and authentication.

**User-wide servers** (all projects): use `--scope user` instead of `project`; configuration is merged into **`~/.claude.json`**, not `.mcp.json`.

### Settings reference

- Claude Code settings overview: [https://code.claude.com/docs/en/settings](https://code.claude.com/docs/en/settings)
- MCP configuration (scopes, transports, examples): [https://code.claude.com/docs/en/mcp](https://code.claude.com/docs/en/mcp)

---

## Quick troubleshooting

| Symptom | What to check |
|--------|----------------|
| `fadcat: command not found` | Install from releases or add the install dir to PATH; use full path in `mcp.json`. |
| FadCat MCP hangs or no tools | Ensure `type: "stdio"`, `args: ["--mcp"]`, and `PYTHONUNBUFFERED=1`. |
| `uvx` not found | Install [uv](https://docs.astral.sh/uv/); restart terminal/Cursor. |
| android-mcp import or Python errors | PyPI requires **Python 3.13+**; align `--python 3.13` with an installed runtime. |
| Device offline / unauthorized | Cable, authorize debugging, `adb kill-server && adb start-server`. |
| Taps do nothing | Complete **uiautomator2** / ATX setup per [Android-MCP](https://github.com/CursorTouch/Android-MCP); disable overlays blocking accessibility if needed. |
| Claude Code ignores MCP | Use **`.mcp.json`** at repo root or `~/.claude.json`, not Claude Desktop’s `claude_desktop_config.json`. Run `claude mcp list` from the project. Approve project servers if prompted; try `claude mcp reset-project-choices` if stuck. |

---

## References

- FadCat: [https://github.com/anonfaded/FadCat](https://github.com/anonfaded/FadCat)
- Android-MCP: [https://github.com/CursorTouch/Android-MCP](https://github.com/CursorTouch/Android-MCP) · [PyPI `android-mcp`](https://pypi.org/project/android-mcp/)
- Cursor MCP: [https://docs.cursor.com](https://docs.cursor.com) (MCP / context section)
- Claude Code MCP: [https://code.claude.com/docs/en/mcp](https://code.claude.com/docs/en/mcp)
