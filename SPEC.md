# Self-Hosted DB Sync SPEC (Single Source of Truth)

## Dev Guidance

- use beads (CLI cmd bd) to create clear task for implementation tasks. 
- commit all changes with atomic commit and proper commit message. 
- Refer to docs at docs/agent-guide/ and submodules/skills/
- Session retrospectives (turn agent back-and-forth into better docs/skills): `docs/agent-guide/003-session-retrospective.md`
- Run existing unittests, and launch dev electron instance connecting debug console for integration testing when proper.

## Scope
- Primary target: macOS desktop client built locally from this repo.
- Primary outcome: self-hosted DB sync is technically and operationally feasible.
- Collaboration is out of scope.
- Existing E2EE paths should be reused where practical instead of inventing a new crypto model.

## Product Decisions
- MVP is technical feasibility, not production hardening.
- UI gating must be bypassed/worked around for self-host mode (non-RTC-group users must reach sync flows).
- Auth path for MVP and Scope B is existing E2EE + Cognito only.
- Vendor decision is locked: Cloudflare Worker path is the execution/deployment vendor for both MVP and future stages.

## Declarative Deliverables
- Desktop self-host deliverable (highest priority): locally built macOS desktop DB client syncs against a self-hosted server end-to-end.
- Account deliverable: official Logseq account without RTC-group membership can still access self-host sync workflow via UI gating workaround.
- Auth deliverable: existing E2EE + Cognito path works against self-host server.
- Build deliverable: local build is reproducible from this repository using documented dev commands.
- Remote deliverable: Cloudflare Worker path is documented and verified for local and remote operation.

## Verified Repo Facts (As-Is)
- UI gating is currently group-based (`rtc-group?`) and includes `team` / `rtc_2025_07_10`.
- DB sync local routing is already supported via `ENABLE_DB_SYNC_LOCAL` and local WS/HTTP defaults in frontend config.
- Protected sync APIs and WS flows currently rely on bearer-token JWT verification (Cognito-oriented env vars in current implementation).
- Node adapter is already first-class in repo (`build:node-adapter`, `start:node-adapter`) and supports local filesystem/sqlite persistence.
- Wrangler/Miniflare path is first-class for Worker-local behavior (`wrangler dev`) and validates DO/D1/R2 shape locally.
- iOS/mobile build workflows exist in this repo (`build-ios-release.yml`, `release-mobile` path), so mobile client code is at least partially open in-tree.

## Build Toolchain Constraints (From CI)
- Node: `22` (current branch CI baseline).
- Clojure CLI: `1.11.1.1413` in CI workflows (newer local CLI may still work, but CI parity should be preferred for debugging).
- Java: mostly `11` for app CI, and `21` for some deps workflows (including `deps-db-sync`).
- Practical implication: for DB-sync-focused local work, Java 21 is acceptable and aligns with `deps-db-sync` workflow.

## Bootstrap Verification Snapshot
- `yarn install` succeeded at repo root.
- `deps/db-sync`: `yarn install --frozen-lockfile` succeeded.
- `deps/db-sync`: `yarn build:node-adapter` succeeded (`:db-sync-node` build complete).
- Node adapter runtime smoke test succeeded:
  - `GET /health` returned `{"ok":true}`.
  - `GET /graphs` without token returned `401` (`{"error":"unauthorized"}`), matching protocol expectations.
- Desktop/local watch path succeeded with `ENABLE_DB_SYNC_LOCAL=true yarn watch`:
  - `:app`, `:db-worker`, `:inference-worker`, and `:electron` all reached build-complete state.
- `bb dev:db-sync-start` launches successfully after ensuring `wrangler` is on PATH:
  - `wrangler dev` starts local server on `http://localhost:8787`.
  - db-sync worker and desktop watchers run concurrently.

## Vendor Decision (Locked)
- Chosen vendor for MVP and future stages: Cloudflare Worker path (Wrangler + Miniflare runtime for local dev).
- Node adapter remains available as a diagnostic/compatibility path, but it is not the primary deliverable path.
- All success criteria and runbooks should treat Cloudflare as the canonical runtime.

## Node Adapter vs Wrangler (How To Choose)
- Choose `wrangler dev` when validating the canonical Cloudflare runtime (Durable Objects + D1 + R2), or before remote deploy.
- Choose the Node adapter when you want faster local iteration on auth/data flow without Cloudflare bindings.
- For this project:
  - MVP and forward path: Wrangler (Cloudflare) is the default.
  - Node adapter: fallback/diagnostic path, and useful for isolating server behavior.

## MVP Execution Result (2026-02-08)
- Verdict: `PASS` (desktop + local self-host feasibility confirmed).
- Local desktop build checks passed:
  - `ENABLE_DB_SYNC_LOCAL=true clojure -M:cljs compile app`
  - `npm run test:node-adapter`
- UI gating workaround is implemented:
  - `rtc-group?` now allows local DB-sync mode via `config/db-sync-local?`.
- E2EE/Cognito path is the active target for MVP and Scope B.

## MVP Blockers + Clarifications
- Current technical blocker status:
  - No hard blocker found for MVP feasibility on mac desktop + self-host server.
- Clarifications resolved:
  1. MVP scope is desktop + local self-host only.
  2. Auth mode is E2EE/Cognito.

## Spec A: MVP Feasibility (Local, E2EE/Cognito)

### Goal
Prove end-to-end technical feasibility with minimum moving parts.

### Constraints
- Use a small disposable test graph only.
- Hardcoded client/server endpoints and toggles are acceptable.
- Security hardening is explicitly deferred.
- MVP must include explicit execution vendor selection.

### Success Criteria
- A locally built mac desktop DB client can connect to a self-hosted sync server.
- A test graph can be uploaded to self-host and pulled back cleanly.
- Basic tx sync works in both directions (edit on client A, observe on client B or after restart/pull).
- Asset upload/download works for at least one image/file attachment.
- App restart + reconnect does not corrupt graph state.
- A short reproducible runbook exists with exact commands and expected checks.
- Feasibility verdict is explicit: `PASS` or `FAIL`, with observed blockers if failed.
- MVP execution vendor is explicitly selected and justified in writing.
- Miniflare feasibility is explicitly recorded (`usable` or `not usable`) with notes.

### MVP Vendor Selection (Locked)

#### Chosen vendor: Cloudflare Worker local simulation via Wrangler (Miniflare runtime)
- Intended use: local and remote feasibility path aligned with future target runtime (DO + D1 + R2 behavior).
- Current evaluation:
  - `usable` for local MVP feasibility testing,
  - should be run through `wrangler dev` (recommended path) rather than custom Miniflare API wiring.

#### Non-chosen path: Node adapter runtime
- Intended use: self-host deployment path and local feasibility without Cloudflare runtime.
- Status in this SPEC: optional fallback/diagnostic path, not the primary success path.

### Non-Goals (MVP)
- Production auth/security.
- Multi-user collaboration flows.
- Official iOS app compatibility.
- Production-grade vendor bakeoff and long-term cost optimization.

## Spec B: Full Desktop Self-Host

### Goal
Deliver a usable self-host desktop sync mode that does not require Logseq RTC-group membership.

### Success Criteria: Client
- Self-host mode is available to desktop users without RTC-group gating.
- Self-host endpoint configuration supports remote HTTP/WS(S) server addresses.
- Default hosted behavior remains unchanged when self-host mode is disabled.
- Uses Logseq official Cognito login, then use the token for auth with self-hosted sync.
- Existing collaboration UX won't be used. Leave them there.

### Remote Test Readiness Gaps (Current)
- Gap 1 (endpoint targeting): desktop DB sync endpoint selection is still compile-time binary (`127.0.0.1:8787` when local flag is on, otherwise Logseq hosted prod URL), and does not yet support a user-configurable remote self-host URL.
- Gap 2 (auth alignment): remote self-host Worker must be configured for the same Cognito issuer/client-id expected by desktop build (`ENABLE_FILE_SYNC_PRODUCTION=true` path).
- Gap 3 (remote acceptance checks): verification section is deferred, so explicit remote pass/fail checks (`/graphs`, `/sync/*` websocket 101, `/assets/*`) are not yet formalized in this SPEC.

### Remote Testing Preconditions (Current)
- Desktop build uses production file-sync auth defines (default in `shadow-cljs.edn` unless overridden), so token issuer/client-id aligns with Worker vars in personal Cloudflare config.
- Worker is deployed and migrated per `/Users/brz/repos/logseq/perosnal_cloudflare.md`.
- Until endpoint configurability is implemented, remote testing requires either:
  - a local proxy that binds `127.0.0.1:8787` and forwards HTTP+WS to the deployed Worker URL, or
  - a code change to make DB sync endpoint runtime-configurable.

### Success Criteria: Server
- Cloudflare Worker deployment supports the selected self-host auth strategy (Cognito-compatible mode can remain available).
- Cloudflare Worker can operate in the chosen auth mode for self-host usage.
- `/graphs`, `/sync/*`, and `/assets/*` function correctly in selected self-host auth mode.
- Durable Object, D1, and R2 binding/config requirements are documented and testable.

### Success Criteria: E2EE
- Existing E2EE implementation is reused where practical.
- Full-graph sync (including encrypted payload paths used by current desktop flow) works in self-host mode.
- Any E2EE feature not supported in no-collaboration mode is explicitly listed.

### Success Criteria: Verification (deferred)
- A deterministic verification checklist exists and is executable by another person.
- Checklist covers:
  - graph create/upload/download,
  - tx sync consistency,
  - reconnect behavior,
  - asset sync,
  - backup/restore sanity check.
- Troubleshooting section exists for common failures (401/403, endpoint mismatch, hidden UI, stale state).

### Success Criteria: Documentation
- One operator runbook exists for local validation.
- One operator runbook exists for remote deployment.
- Config surface is documented (all required env vars, flags, defaults, and examples).
- Known risks and explicit non-goals are documented.

## Open Decisions
1. Self-host mode switch model: compile-time only, runtime only, or both.

## References

### User-Provided DB References
- DB graph changes (Markdown graph vs DB graph): [db-version-changes.md](https://github.com/logseq/docs/blob/master/db-version-changes.md)
- DB graph guide: [db-version.md](https://github.com/logseq/docs/blob/master/db-version.md)
- DB graph unofficial FAQ: [Logseq DB Unofficial FAQ](https://discuss.logseq.com/t/logseq-db-unofficial-faq/32508)

### Runtime/Vendor References
- Miniflare docs: [Cloudflare Workers Miniflare](https://developers.cloudflare.com/workers/testing/miniflare/)

### In-Repo References
- Desktop and local dev setup: `/Users/brz/repos/logseq/docs/develop-logseq.md`
- DB sync protocol: `/Users/brz/repos/logseq/docs/agent-guide/db-sync/protocol.md`
- DB sync implementation guide: `/Users/brz/repos/logseq/docs/agent-guide/db-sync/db-sync-guide.md`


## User manual test - local

Start local cloudflare (miniflare)

```
cd /Users/brz/repos/logseq/deps/db-sync
yarn release

cd /Users/brz/repos/logseq/deps/db-sync/worker
wrangler d1 migrations apply logseq-sync-graph-meta-prod --local
wrangler dev --local --port 8787 --var LOGSEQ_SYNC_AUTH_MODE:cognito
```

Build dev client

```
cdcd /Users/brz/repos/logseq
ENABLE_DB_SYNC_LOCAL=true yarn electron-watch
```

Run electron
```
cd /Users/brz/repos/logseq
yarn dev-electron-app
```

Run with chrome console debug port
```
cd /Users/brz/repos/logseq/static
./node_modules/.bin/electron --remote-debugging-port=9222 .
```


# User manual test - remote self-host sync server
build 
```
cd /Users/brz/repos/logseq
ENABLE_DB_SYNC_LOCAL=true ENABLE_FILE_SYNC_PRODUCTION=true yarn watch
```

proxy because logseq client hard-codes to use 127.0.0.1:8787 as sync server:
```
❯ brew install caddy

cat > /tmp/logseq-sync-proxy.Caddyfile <<'EOF'
:8787 {
  reverse_proxy https://lseek-sync-createdat20260208.brillliantz.workers.dev {
    header_up Host lseek-sync-createdat20260208.brillliantz.workers.dev
  }
}
EOF

caddy run --config /tmp/logseq-sync-proxy.Caddyfile
```

Run client
```
cd /Users/brz/repos/logseq/static
./node_modules/.bin/electron --remote-debugging-port=9222 .
```

Monitor remote logs
```
cd /Users/brz/repos/logseq/deps/db-sync/worker
npx wrangler tail --config wrangler.personal.toml
```

## E2E Test Scenarios Checklist (Desktop + Self-Host)

These scenarios are the canonical acceptance checks for self-host DB sync.

### Scenario 1: Create a new graph, upload, then restart and pull

1. Start local server (Wrangler):
   - `cd deps/db-sync/worker`
   - `wrangler d1 migrations apply logseq-sync-graph-meta-prod --local`
   - `wrangler dev --local --port 8787 --var LOGSEQ_SYNC_AUTH_MODE:cognito`
2. Start desktop watch build:
   - `ENABLE_DB_SYNC_LOCAL=true yarn electron-watch`
3. Launch Electron with Chrome devtools port:
   - `cd static && ./node_modules/.bin/electron --remote-debugging-port=9222 .`
4. In the app:
   - Create a small disposable DB graph.
   - Enable DB sync for that graph, upload it.
   - Make 2 to 3 edits (create pages, blocks).
5. Restart Electron and confirm:
   - No corruption.
   - Sync reconnects.
   - Remote txs pull cleanly.

### Scenario 2: Asset upload and download (single attachment)

1. In the test graph, attach a small image (png) to create an asset entity.
2. Confirm asset upload occurs.
3. Quit the app, delete the local asset file under:
   - `~/logseq/graphs/<graph>/assets/<uuid>.<ext>`
4. Reopen the graph and confirm the asset auto-downloads.

### Scenario 3: Delete local graph, download from remote, assets restored to disk

1. Ensure the graph is fully uploaded (including at least one asset).
2. Delete the local graph directory:
   - `~/logseq/graphs/<graph>`
3. Relaunch the app, pick the remote graph, download it.
4. Expected:
   - Graph opens successfully.
   - Assets are restored under `~/logseq/graphs/<graph>/assets/` without needing to manually open each asset block.

### Scenario 4: Remote self-host via local proxy

1. Build with production auth defines:
   - `ENABLE_DB_SYNC_LOCAL=true ENABLE_FILE_SYNC_PRODUCTION=true yarn watch`
2. Run the Caddy proxy binding `:8787` forwarding to Worker URL.
3. Repeat Scenarios 1 to 3 against the remote server.

### Scenario 5: Remote log sanity

- Requests to `/sync` (without `/sync/<graph-id>/...`) are treated as malformed and return `400 {"error":"missing graph id"}`.
- This avoids "Unknown" entries in proxy or tail logs for accidental `/sync?...` requests.

## Implementation Notes (Decisions)

- Asset restore after snapshot download is proactive (missing remote assets are downloaded into `~/logseq/graphs/<graph>/assets/` during the download flow, instead of only on-demand when an asset block renders).
- `/sync/<graph-id>` (no tail path) is treated as a graph-level health check.
- `/sync` and `/sync/` (no graph id) return `400` with a structured error, to avoid confusing tail log output.
- `clj-e2e` runs the web build (OPFS via `window.pfs`), so the asset-restore regression test clears `/<graph>/assets` via `window.workerThread.rimraf` to emulate Electron "delete local graph" semantics (removing `~/logseq/graphs/<graph>/assets`).
- The E2E regression uploads a unique temp file each run to avoid asset dedup ("asset exists already") affecting test determinism.
