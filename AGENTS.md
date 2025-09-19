# Repository Guidelines

## Launguage
- Use Japanese to response

## Project Structure & Module Organization
- Source: `src/main/java/org/etwas/streamtweaks` (core) and `.../client` (client entrypoint) and `.../twitch/auth` (Twitch OAuth + storage).
- Resources: `src/main/resources` (`fabric.mod.json`, `stream-tweaks.mixins.json`).
- Build/Run outputs: `build/`, dev runtime: `run/` (Fabric Loom).
- Java version: 21. Group: `org.etwas.streamtweaks`. Mod ID: `stream-tweaks`.

## Build, Test, and Development Commands
- `gradle build` — Compile, run checks, and produce the mod JAR in `build/libs/`.
- `gradle runClient` — Launch Minecraft client in the dev environment.
- `gradle runServer` — Launch a dedicated server in the dev environment.
- `gradle genSources` — Generate and remap sources for IDE usage.

## Coding Style & Naming Conventions
- Java style, 4‑space indentation, UTF‑8, prefer max line length ~120.
- Packages: lowercase dot notation (e.g., `org.etwas.streamtweaks.twitch.auth`).
- Classes: PascalCase; methods/fields: camelCase; constants: UPPER_SNAKE.
- Keep entrypoints minimal: initialization in `StreamTweaks` and `StreamTweaksClient`.
- No unused mixins; add to `stream-tweaks.mixins.json` only when needed.

## Testing Guidelines
- Currently no unit tests present. Prefer JUnit 5 in `src/test/java` when adding tests.
- Name tests mirroring sources (e.g., `TwitchOAuthClientTest`).
- Run `gradle build` locally before PRs to verify.

## Commit & Pull Request Guidelines
- Use Conventional Commits where possible: `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`.
- Commits should be small and focused; include rationale in the body when non‑obvious.
- PRs must include: clear description, before/after notes, reproduction or verification steps, and related issue links.
- For behavior changes, attach logs from `runClient` or screenshots when UI/chat output is relevant.

## Security & Configuration Tips
- OAuth tokens are stored via Fabric config: typically `run/config/stream-tweaks/twitch-credentials.json` in dev (or the Fabric config dir in user installs). Never commit credentials.
- Avoid logging secrets; prefer high‑level statuses. If adding new config files, keep them under the mod’s config dir.

## Agent‑Specific Notes
- Respect the structure above and Gradle tasks. Update `fabric.mod.json` and mixins in lockstep with code changes. Keep changes minimal and scoped.
