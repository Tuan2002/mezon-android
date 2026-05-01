# Contributing to Mezon Android

This guide describes **who does what**, how we expect **human** and **AI-assisted** changes to land, and where to read before touching the codebase.

---

## Roles

### Maintainers

- Set direction for larger refactors, protocol bumps, and release readiness.
- Review merge requests for correctness, architecture fit, and risk on **chat, realtime, and persistence** paths.
- Keep shared docs ([Architecture](./ARCHITECTURE.md), [MCP](./MCP.md), README) consistent with reality.

### Contributors (engineers)

- Ship **focused** changes: one concern per merge request when practical.
- Follow existing patterns in the same package (controllers, `NotificationCenter`, `BaseFragment`, `BaseCell`, Room).
- Run a **debug build** (and relevant tests) before requesting review; note gaps if the environment is incomplete (e.g. missing `google-services.json`).
- Do **not** commit secrets, `local.properties`, or generated paths that are machine-specific. See [README.MD](../README.MD) for Firebase and `mezon.secrets.properties`.

### AI coding agents (Cursor, Claude Code, and similar)

Agents are treated as **tools used by contributors**: output is still **your** change set to review and own.

**Responsibilities**

1. **Load project constraints** before editing:
   - [Architecture](./ARCHITECTURE.md) and [.cursor/rules/data-flow.mdx](../.cursor/rules/data-flow.mdx), [ui-design.mdx](../.cursor/rules/ui-design.mdx), [name-convention.mdx](../.cursor/rules/name-convention.mdx) (or the mirrored `.claude/rules/` copies if you use Claude Code with those paths).
   - Optional deep dives: [.claude/skills/data-flow/SKILL.md](../.claude/skills/data-flow/SKILL.md), [ui-design/SKILL.md](../.claude/skills/ui-design/SKILL.md).
2. **Stay in scope**: implement the requested behavior only; avoid broad refactors, renames, or unrelated file churn.
3. **Match the codebase**: extend existing controllers and `NotificationCenter` events rather than introducing parallel MVVM/`StateFlow` shells for primary screens; use `BaseFragment` + custom views for main UI, not Compose, unless the project explicitly migrates.
4. **Preserve threading contracts**: dual-write memory first; Room on I/O; UI signals on the main thread via `NotificationCenter`.
5. **Do not invent** new socket plumbing: use **`SocketEventDispatcher`** for inbound multiplexing; respect **`ApiCacheTracker`** where already used.
6. **Secrets and local config**: never add real tokens, `google-services.json`, or team-only properties to the repo; do not paste secrets into prompts that leave the machine uncontrolled.
7. **MCP (optional)**: [MCP setup](./MCP.md) (FadCat, android-mcp) is for debugging and device automation; it does not replace reading the architecture docs.

**Humans using agents** should paste or attach the relevant rules/skills in the session when possible, and should **verify** diffs (especially `NotificationCenter`, controllers, and Room migrations) before pushing.

---

## Suggested workflow

1. **Branch** from the integration branch your team uses (e.g. `main` or `develop`).
2. **Implement** with small, reviewable commits and clear messages.
3. **Open a merge request** with:
   - What changed and **why** (plain language).
   - How to test (screens, flows, or `./gradlew` commands).
   - Known limitations (e.g. protocol symlink not updated, feature flag).
4. **Respond to review** with follow-up commits or comments; avoid force-push noise unless your team standard says otherwise.

---

## Build and checks

From the repository root (see [README.MD — Build and test](../README.MD#build-and-test)):

```bash
./gradlew assembleDebug
./gradlew test
```

Fix compile and lint issues introduced by your change. If CI differs from local results, note that in the MR.

---

## Documentation and AI artifacts

- User-facing or technical doc updates belong in **`docs/`** when they help the whole team (you may be asked to add a short note when behavior changes).
- **Cursor rules** (`.cursor/rules/`) and **agent skills** (`.claude/skills/`) are optional but should stay **aligned** with [ARCHITECTURE.md](./ARCHITECTURE.md) when you change conventions; avoid duplicating long prose in three places unless necessary.

---

## Getting help

- Architecture and layering: [ARCHITECTURE.md](./ARCHITECTURE.md).
- Local setup and protos: [README.MD](../README.MD).
- Editor MCP (debug/automation): [MCP.md](./MCP.md).

For product or API semantics, follow your team’s channel for Mezon protocol and backend contracts.
