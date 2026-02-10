# E2E Task Status (asset restore regression)

## Task (what you asked for)

- Add a real end-to-end regression for: **delete local graph, download remote graph, assets are restored automatically** (no page visit required).
- Demonstrate **fail pre-fix** and **pass post-fix**, using **two git worktrees**.
- Rewrite history so the **E2E test commit lands immediately before the fix commit**.

## Status

- Done, and verified with worktrees.

## Key commits (rewritten history)

- E2E regression test commit: `cedd4f7c9` (`test(e2e): assert assets restored after remote graph download`)
- Fix commit: `892191903` (`fix(db-sync): restore missing assets after snapshot download`)
  - This fix commit includes what used to be split across `a760ad40e` + `6ac18abcf` (squashed together during history rewrite).

## Verification (two worktrees)

Worktrees live under the repo parent dir:
- `../logseq-wt-pre`
- `../logseq-wt-post`

### Pre-fix (fails)

In `../logseq-wt-pre`:
- `git checkout -f cedd4f7c9`
- `clojure -M:cljs release db-worker`
- `cd clj-e2e && bb run-rtc-extra-part2-asset-test`

Observed failure (expected):
- Asset only appears **after visiting the page** (`:reason :downloaded-only-after-page-visit`), meaning the download flow did not proactively restore missing assets.

### Post-fix (passes)

In `../logseq-wt-post`:
- `git checkout -f 892191903`
- `clojure -M:cljs release db-worker`
- `cd clj-e2e && bb run-rtc-extra-part2-asset-test`

Observed pass (expected):
- Asset file exists in OPFS (`window.pfs`) after graph download, without requiring a page visit.

## Speedup changes (temporary but safe, env-gated)

Goal: make the red-green loop ~10x faster by avoiding:
1) running the entire `rtc-extra-part2-test` namespace, and
2) creating + syncing + deleting a brand new RTC graph every run, and
3) Playwright’s default `slow-mo` delay (100ms).

### Fast env vars

- `LOGSEQ_E2E_FAST=1`
  - Sets Playwright slow-mo to `0` (via `clj-e2e/src/logseq/e2e/config.clj`).
- `LOGSEQ_E2E_ONLY_ASSET_RESTORE=1`
  - Runs only the asset restore regression test using `test-ns-hook` (via `clj-e2e/test/logseq/e2e/rtc_extra_part2_test.clj`).
- `LOGSEQ_E2E_REUSE_RTC_GRAPH=1`
  - Reuses a stable remote graph name `rtc-extra-part2-test-graph-reused` instead of creating/deleting one every run (via `clj-e2e/test/logseq/e2e/fixtures.clj`).

### New bb task

- `cd clj-e2e && bb run-rtc-extra-part2-asset-test`
  - Equivalent to setting the 3 env vars above and running the ns.
  - Implemented in `clj-e2e/bb.edn`.

## Test design notes (important decisions)

- `clj-e2e` runs the web build, so assets live in OPFS (via `window.pfs`), not `~/logseq/graphs/...`.
- The test calls `e2e-assets/clear-assets-dir!` (rimraf `/<graph>/assets`) after deleting the local graph, to emulate Electron’s “delete local graph directory” semantics.
- The test uploads a unique temp `.txt` file each run to avoid asset dedup ("asset exists already") and to keep the regression deterministic.
