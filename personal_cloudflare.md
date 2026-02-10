# Personal Cloudflare DB Sync Runbook

This document records the personal Cloudflare setup and operational steps for Logseq DB sync.
This is based on the setup performed on 2026-02-08.

## Scope

- Vendor: Cloudflare (personal account controlled, not Logseq controlled).
- Repo: `/Users/brz/repos/logseq`
- Worker package path: `/Users/brz/repos/logseq/deps/db-sync/worker`

## Current Personal Environment

- Cloudflare account email: `brillliantz@outlook.com`
- Cloudflare account id: `85a9eae8b4804aba15d3443df1edfc7f`
- Worker name: `lseek-sync-createdat20260208`
- Worker URL: `https://lseek-sync-createdat20260208.brillliantz.workers.dev`
- D1 database name: `lseek-sync-createdat20260208`
- D1 database id: `cff69043-8f68-4000-8acb-24734406ed9b`
- R2 bucket name: `lseek-sync-createdat20260208`
- Personal wrangler config file: `/Users/brz/repos/logseq/deps/db-sync/worker/wrangler.personal.toml`

## What Was Done

1. Verified Cloudflare auth with `wrangler whoami`.
2. Confirmed the checked-in `wrangler.toml` points at Logseq-owned resources, not personal resources.
3. Created personal D1 database.
4. Created personal R2 bucket.
5. Added `wrangler.personal.toml` with personal bindings and ids.
6. Built worker bundle via `npm run release` in `/Users/brz/repos/logseq/deps/db-sync`.
7. Applied D1 migrations.
8. Deployed worker with personal config.
9. Verified:
   - `GET /health` returns `200` with `{"ok":true}`.
   - `GET /graphs` without token returns `401` with `{"error":"unauthorized"}`.

## One-Time Bootstrap (Fresh Machine)

From `/Users/brz/repos/logseq/deps/db-sync/worker`:

```bash
npx wrangler login
npx wrangler whoami
```

Create resources (if absent):

```bash
npx wrangler d1 create lseek-sync-createdat20260208
npx wrangler r2 bucket create lseek-sync-createdat20260208
```

Check inventory:

```bash
npx wrangler d1 list
npx wrangler r2 bucket list
```

## Deploy Procedure

Build worker bundle:

```bash
cd /Users/brz/repos/logseq/deps/db-sync
npm run release
```

Apply DB schema to remote D1:

```bash
cd /Users/brz/repos/logseq/deps/db-sync/worker
npx wrangler d1 migrations apply lseek-sync-createdat20260208 --remote --config wrangler.personal.toml
```

Deploy worker:

```bash
npx wrangler deploy --config wrangler.personal.toml
```

Verify:

```bash
curl -i https://lseek-sync-createdat20260208.brillliantz.workers.dev/health
curl -i https://lseek-sync-createdat20260208.brillliantz.workers.dev/graphs
```

## Day-2 Admin and DevOps

List deployments:

```bash
npx wrangler deployments list --name lseek-sync-createdat20260208
```

Tail logs:

```bash
npx wrangler tail --config wrangler.personal.toml
```

Run local dev worker:

```bash
npx wrangler dev --config wrangler.personal.toml
```

Inspect D1:

```bash
npx wrangler d1 execute lseek-sync-createdat20260208 --remote --command "SELECT name FROM sqlite_master WHERE type='table';"
```

## Known Blockers and Fixes

### Blocker: Not authenticated
- Symptom: `You are not authenticated. Please run wrangler login.`
- Fix: `npx wrangler login`.

### Blocker: Wrong account resources in default config
- Symptom: deploy/list failures against non-existent worker or foreign ids.
- Fix: always use `--config wrangler.personal.toml` for personal environment commands.

### Blocker: Migration accidentally applied to local-only D1
- Symptom: Wrangler output shows local `.wrangler/state` path.
- Fix: rerun migration with `--remote`.

### Blocker: Unauthorized API responses
- Symptom: `/graphs`, `/sync/*`, `/assets/*` return `401`.
- Reason: current server code expects JWT auth.
- Fix path:
  - either use valid JWT/Cognito setup, or
  - merge no-auth/self-host auth changes in product branch.

## Notes for Product Work

- Infrastructure is operational.
- Auth is the current functional gating item for end-to-end personal use.
- Keep production deploys and local testing separated by explicit config files.
