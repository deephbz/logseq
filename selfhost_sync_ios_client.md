# Self-Hosted Sync + iOS Client Guide

This document summarizes the key answers from this session for running Logseq DB sync against a self-hosted server and using a locally built iOS client.

## 1) Publish paths: cloud vs static export

There are two different publish systems in this repo:

- Updated cloud publish path:
  - Frontend uploads page payload and assets to publish API endpoints (`/pages`, `/assets`).
  - Backend is the publish worker stack (`deps/publish`) with Durable Object + R2 + SSR routes.
- Legacy static export path:
  - Frontend builds static publish artifacts locally (`deps/publishing`) for self-hosting (for example, GitHub Pages).

They are different implementations, not one code path with different targets.

## 2) What `ENABLE_FILE_SYNC_PRODUCTION=true` changes

`ENABLE_FILE_SYNC_PRODUCTION=true` switches the app to production auth/API settings, including:

- `LOGIN-URL`
- `API-DOMAIN`
- Cognito config (`COGNITO-*`)
- `PUBLISH-API-BASE` (`https://logseq.io`)

This flag does not directly change DB sync transport URLs. DB sync transport is controlled by `ENABLE_DB_SYNC_LOCAL` and the sync URL constants.

## 3) Any conflict between self-host sync and publish?

No inherent product conflict.

Possible practical conflict is auth alignment:

- If publish/sync calls require JWT validation, the token issuer/client-id expected by your backend must match the token produced by your app build.

## 4) Where sync URL is configured (desktop + mobile)

Current branch behavior:

- Sync endpoints are selected in `src/main/frontend/config.cljs`.
- Both desktop and mobile use those same values.
- Startup pushes those values into DB worker config (`set-db-sync-config`).

Important current limitation:

- There is no runtime UI field for a custom self-host sync endpoint.
- Local mode (`ENABLE_DB_SYNC_LOCAL=true`) currently maps to hardcoded localhost values:
  - WS: `ws://127.0.0.1:8787/sync/%s`
  - HTTP: `http://127.0.0.1:8787`

So if you want remote self-host sync on both desktop and mobile today, you must modify config and rebuild.

## 5) Your personal Cloudflare worker values

From `personal_cloudflare.md`:

- Worker URL:
  - `https://lseek-sync-createdat20260208.brillliantz.workers.dev`

Client sync endpoints should be:

- `db-sync-http-base`:
  - `https://lseek-sync-createdat20260208.brillliantz.workers.dev`
- `db-sync-ws-url`:
  - `wss://lseek-sync-createdat20260208.brillliantz.workers.dev/sync/%s`

Auth vars in `deps/db-sync/worker/wrangler.personal.toml` already show Cognito settings are configured for JWT validation.

## 6) iOS local build + install workflow

From repo docs and current workflow:

1. Build/watch mobile assets:
   - `yarn mobile-watch`
2. Sync Capacitor iOS project:
   - `npx cap sync ios`
3. Open in Xcode:
   - `npx cap open ios`
4. Connect iPhone, pick your device target, then Run in Xcode.

### Do you need paid Apple Developer Program?

- For local install to your own iPhone via Xcode:
  - Usually no paid membership required (Apple ID personal team is enough).
- For TestFlight/App Store distribution:
  - Paid Apple Developer Program is required.

## 7) Practical note for remote self-host on iPhone

If app is built with `ENABLE_DB_SYNC_LOCAL=true` and unmodified sync URL constants, iPhone will try to reach `127.0.0.1` on the phone itself, not your server.

For remote self-host testing, set sync endpoints to your domain in config and rebuild the mobile app.
