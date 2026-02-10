# Repository Guidelines

## Project Structure & Module Organization
- `src/` is the main codebase.
  - `src/main/` contains core application logic.
  - `src/main/mobile/` is the mobile app code.
  - `src/main/frontend/components/` houses UI components.
  - `src/main/frontend/inference_worker/` and `src/main/frontend/worker/` hold webworker code, including RTC in `src/main/frontend/worker/rtc/`.
- `src/electron/` is Electron-specific code.
- `src/test/` contains unit tests.
- `deps/` contains internal dependencies/modules.
- `clj-e2e/` contains end-to-end tests.

## Build, Test, and Development Commands
- `bb dev:lint-and-test` runs linters and unit tests.
- `bb dev:test -v <namespace/testcase-name>` runs a single unit test (example: `bb dev:test -v logseq.some-test/foo`).
- E2E tests live in `clj-e2e/`; run them from that directory if needed.

## Coding Style & Naming Conventions
- ClojureScript keywords are defined via `logseq.common.defkeywords/defkeyword`; use existing keywords and add new ones in the shared definitions.
- Follow existing namespace and file layout; keep related workers and RTC code in their dedicated directories.
- Prefer concise, imperative commit subjects aligned with existing history (examples: `fix: download`, `enhance(rtc): ...`).
- Clojure map keyword name should prefer `-` instead of `_`, e.g. `:user-id` instead of `:user_id`.

## Testing Guidelines
- Unit tests live in `src/test/` and should be runnable via `bb dev:lint-and-test`.
- Name tests after their namespaces; use `-v` to target a specific test case.
- Run lint/tests before submitting PRs; keep changes green.
- For `clj-e2e` changes, prefer small edits and run a fast compile gate first (example: from `clj-e2e/`, run `bb rtc-extra-part2-test`) before the slower `bb run-rtc-extra-part2-test`.
- When proving regressions across commits, prefer `git worktree` checkouts to avoid a dirty working tree and to make pre-fix vs post-fix results reproducible.
- Keep E2E assertions deterministic and minimal (avoid switching assertion strategy mid-iteration unless necessary).
- Remember: `clj-e2e` runs the web build. Files on disk like `~/logseq/graphs/...` are usually not observable there. Use browser-observable storage/APIs (for DB graphs, lightning-fs via `window.pfs`) when you need to assert asset presence.

## Commit & Pull Request Guidelines
- Commit subjects are short and imperative; optional scope prefixes appear (e.g., `fix:` or `enhance(rtc):`).
- PRs should describe the behavior change, link relevant issues, and note any test coverage added or skipped.

## Agent-Specific Notes
- Review notes live in `prompts/review.md`; check them when preparing changes.
- DB-sync feature guide for AI agents: `docs/agent-guide/db-sync/db-sync-guide.md`.
- DB-sync protocol reference: `docs/agent-guide/db-sync/protocol.md`.
- New properties should be added to `logseq.db.frontend.property/built-in-properties`.
- Avoid creating new class or property unless you have to.
- Avoid shadow var, e.g. `bytes` should be named as `payload`.
