# Logseq Sync Architecture Map

## Project Layout

```
src/
  main/frontend/
    worker/sync.cljs          # Core sync orchestrator (upload/download graph, asset ops)
    worker/db_worker.cljs      # DB worker: import-datoms-to-db!, schema, snapshot restore
    handler/assets.cljs        # Asset CRUD: read, write, upload, download, metadata
    handler/editor.cljs        # Block/asset creation (new-asset-block)
    components/block.cljs      # Asset rendering (asset-cp), triggers download requests
    fs.cljs                    # FS abstraction: stat, read-file-raw, write-asset-file!, file-exists?
    fs/protocol.cljs           # FS protocol interface
    fs/node.cljs               # Electron FS impl (IPC to electron process)
    fs/memory_fs.cljs          # Web FS impl (OPFS / in-memory)
    state.cljs                 # Global state atoms (:assets/asset-file-write-finish, :rtc/*)
  electron/electron/
    handler.cljs               # IPC dispatch: set-ipc-handler!, defmethod handle per op
    utils.cljs                 # Electron-side file ops: read-file, read-file-raw, write-file
deps/
  db-sync/
    src/logseq/db_sync/worker/
      dispatch.cljs            # Cloudflare Worker request router (/sync/<graph-id>/...)
      routes/sync.cljs         # Reitit route definitions for sync DO
      handler/sync.cljs        # Route handlers inside the Durable Object
    test/logseq/db_sync/       # Unit tests (run with `bb dev:db-sync-test`)
clj-e2e/
    src/logseq/e2e/            # E2E test helpers (graph, assets, util, config, etc.)
    test/logseq/e2e/           # Playwright-based e2e tests (bb dev in clj-e2e/)
```

## Data Flow: Graph Upload

```
upload-graph! (sync.cljs)
  |-- d/datoms @source-conn :eavt
  |-- offload-large-titles-in-datoms
  |-- (when e2ee?) sync-crypt/<encrypt-datoms
  |-- <create-temp-sqlite-conn  -->  fetch-kvs-rows in batches
  |-- POST /sync/<graph-id>/snapshot/upload?reset=true|false
  |-- (when empty rows = done)
  |     |-- client-op/add-all-exists-asset-as-ops   (queues asset ops)
  |     \-- process-asset-ops!                       (flushes to R2)
  \-- cleanup-temp-sqlite!
```

**Key insight**: `add-all-exists-asset-as-ops` only _queues_ ops; without
`process-asset-ops!` the assets never reach R2. A re-downloaded graph will
have DB entries but empty `assets/` directory.

## Data Flow: Graph Download / Snapshot Restore

```
import-datoms-to-db! (db_worker.cljs)
  |-- receive snapshot rows via fetch
  |-- restore into local sqlite
  |-- query all :logseq.class/Asset entities
  \-- for each asset: <download-remote-asset-if-missing!  (batches of 10)
```

**Key insight**: The snapshot only contains DB rows. Asset files live in R2
and must be fetched separately after restore.

## Data Flow: Asset Render & On-Demand Download

```
asset-cp component (block.cljs)
  :will-mount
  |-- fs/file-exists? repo-dir path   -->  stat via IPC (errors silenced)
  |     \-- resets ::file-exists? atom
  :did-mount / :did-update
  |-- maybe-request-asset-download!
  |     |-- checks file-ready? = (file-exists? OR asset-file-write-finished?)
  |     \-- assets-handler/maybe-request-remote-asset-download!
  |           |-- should-request-remote-asset-download?
  |           |     checks: repo, uuid, asset-type, not external-url,
  |           |             not file-ready?, not transfer-in-progress?,
  |           |             remote-metadata present (origin/master gating)
  |           \-- invokes worker :thread-api/db-sync-request-asset-download
```

**Gating issue**: `should-request-remote-asset-download?` on origin/master
requires `remote-metadata` to be set on the entity. After a fresh snapshot
restore, entities may lack this property, blocking downloads.

## Data Flow: Asset Upload (RTC)

```
DB change event  -->  new-task--rtc-upload-asset (assets.cljs)
  |-- <read-asset  -->  fs/read-file-raw  -->  IPC "readFileRaw"
  |     (can race with file write = transient ENOENT)
  |-- (when aes-key) encrypt
  |-- HTTP PUT to presigned R2 URL
  \-- progress tracked via :rtc/asset-upload-download-progress
```

**Race condition**: The DB change event fires before the file is fully
written to disk. The upload retry logic (p/loop, 20 attempts, 200ms delay)
handles transient ENOENT.

## IPC Architecture (Electron)

```
Renderer (ClojureScript)          Electron Main (ClojureScript)
--------------------------        ----------------------------
fs/read-file-raw                  electron.handler/set-ipc-handler!
  -> protocol/read-file-raw         ipcMain.handle "main" channel
    -> Node.read-file-raw             (bean/->clj args)
      -> ipc/ipc "readFileRaw"        (handle window message)  ; defmethod dispatch
        -> wrap-throw-ex-info           -> utils/read-file-raw
                                           (fs/readFileSync path)
```

The IPC error handler (handler.cljs:477) catches all errors. It silences
`"mkdir"` and `"stat"` ops but logs everything else as `IPC error:`.
`"readFileRaw"` ENOENT was noisy because it wasn't silenced -- fixed by
adding `existsSync` guard in `utils/read-file-raw`.

## CF Worker Routing (db-sync)

```
dispatch.cljs
  |-- /assets/<path>  -->  assets-handler (with graph-access check)
  |-- OPTIONS         -->  CORS preflight
  |-- /sync           -->  400 "missing graph id"  (guard)
  |-- /sync/          -->  400 "missing graph id"  (guard)
  |-- /sync/<graph-id>/...
  |     |-- extract graph-id from path
  |     |-- graph-access check (auth)
  |     |-- WebSocket upgrade? --> .fetch stub request
  |     |-- else: rewrite URL, forward to Durable Object
  \-- fallback        -->  404
```

Routes inside the Durable Object are in `routes/sync.cljs` (reitit).
Handlers in `handler/sync.cljs`.

## Key State Atoms

| Atom path | Purpose |
|-----------|---------|
| `:assets/asset-file-write-finish` | `{repo {asset-id-str timestamp}}` -- marks local writes done |
| `:rtc/asset-upload-download-progress` | `{repo {asset-id-str {:loaded N :total N :direction :upload\|:download}}}` |
| Worker state: `current-client` | `{:repo ... :graph-id ...}` -- active WS sync client |

## Debug Tips

### Electron CDP
```bash
just dev-start-debug          # port 9333 by default
just dev-start-debug 9222     # custom port
# Then connect Chrome DevTools to ws://127.0.0.1:9333
```

### Noisy IPC errors
Errors from IPC ops are logged at `electron/handler.cljs:477-481`.
`"mkdir"` and `"stat"` are silenced. If you see `IPC error:` for
`readFileRaw`, the asset file doesn't exist on disk (expected during
sync, should be handled by caller's p/catch).

### Asset sync not triggering
Check `should-request-remote-asset-download?` in `assets.cljs`:
- Requires `remote-metadata` on the entity (blocks fresh-restore downloads)
- Requires `asset-type` to be non-blank
- Skips `external-url` assets
- Skips if transfer already in progress

### Watching sync logs
The worker posts `:rtc-log` messages with `{:type :rtc.log/upload}`
or similar. Subscribe to state changes on `:rtc/` keys.

### Local CF worker
```bash
just local-cf                 # runs wrangler dev for deps/db-sync/worker
# Requires ENABLE_DB_SYNC_LOCAL=true in the client build
```

### E2E tests (sync/RTC)
```bash
cd clj-e2e && bb run-rtc-extra-part2-test
# or from root:
bb dev:e2e-rtc-extra-part2-test
```
Env vars: `LOGSEQ_E2E_FAST=1` (skip waits), `LOGSEQ_E2E_REUSE_RTC_GRAPH=1`.

### Common failure modes
| Symptom | Cause | Fix location |
|---------|-------|-------------|
| Assets missing after graph download | Snapshot restore doesn't fetch R2 files | `db_worker.cljs` import-datoms-to-db! |
| Assets missing after graph upload | `process-asset-ops!` not called | `sync.cljs` upload-graph! |
| Upload fails with ENOENT | File write races with DB change event | `assets.cljs` retry logic |
| Blank asset labels in UI | `block/title` nil for synced assets | `block.cljs` fallback helper |
| `IPC error: readFileRaw` in console | Asset not on disk, read before download | `electron/utils.cljs` existsSync guard |
| Download never triggers | `remote-metadata` missing on entity | `assets.cljs` gating check |
