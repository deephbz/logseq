install-java:
  brew install openjdk

install-clojure:
  brew install clojure/tools/clojure

setup-env: install-clojure install-java
  yarn install --frozen-lockfile
  cd static && yarn install --frozen-lockfile

release-web: setup-env
  ENABLE_DB_SYNC_LOCAL=true yarn release

release-desktop: setup-env
  ENABLE_DB_SYNC_LOCAL=true yarn release-electron

build-db-sync:
  cd deps/db-sync && yarn install --frozen-lockfile && yarn release

# Backward-compatible alias.
build: release-web
  @:

dev-start:
  ENABLE_DB_SYNC_LOCAL=true bb dev:electron-start

local-cf-worker-build: build-db-sync
  @:

local-cf-one-time-migration: build-db-sync
  cd deps/db-sync/worker && wrangler d1 migrations apply DB --local

local-cf-init: local-cf-one-time-migration
  @:

local-cf: build-db-sync
  cd deps/db-sync/worker && yarn dev


# Requires `just dev-watch` in a separate shell.
dev-electron-debug port="9333":
  cd static && yarn electron:dev -- -- --remote-debugging-port={{port}}
