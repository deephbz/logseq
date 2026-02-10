(ns logseq.tasks.dev.db-sync
  "Tasks for db-sync dev processes"
  (:require [babashka.process :refer [process]]
            [clojure.string :as string]))

(defn- env-value
  [name default]
  (or (System/getenv name) default))

(defn- truthy?
  [s]
  (contains? #{"1" "true" "yes"} (some-> s string/trim string/lower-case)))

(defn- apply-local-d1-migrations!
  [d1-database]
  (println "Applying local D1 migrations for" d1-database)
  (let [result @(process ["bash" "-lc" (str "wrangler d1 migrations apply " d1-database " --local")]
                         {:dir "deps/db-sync/worker"
                          :inherit true})]
    (when-not (zero? (:exit result))
      (throw (ex-info "Failed to apply local D1 migrations"
                      {:d1-database d1-database
                       :result result})))))

(defn- processes
  [auth-mode]
  [{:name "db-sync-watch"
    :dir "deps/db-sync"
    :cmd "clojure -M:cljs watch db-sync"}
   {:name "wrangler-dev"
    :dir "deps/db-sync/worker"
    :cmd (str "wrangler dev --var LOGSEQ_SYNC_AUTH_MODE:" auth-mode)}
   {:name "yarn-watch"
    :dir "."
    :cmd "ENABLE_DB_SYNC_LOCAL=true yarn watch"}])

(defn start
  []
  (let [auth-mode (env-value "LOGSEQ_SYNC_AUTH_MODE" "cognito")
        d1-database (env-value "LOGSEQ_SYNC_D1_DB" "logseq-sync-graph-meta-prod")
        skip-migrations? (truthy? (env-value "LOGSEQ_SYNC_SKIP_LOCAL_MIGRATIONS" ""))]
    (println "Starting db-sync processes in foreground.")
    (println "Auth mode:" auth-mode)
    (println "Use Ctrl-C to stop.")
    (when-not skip-migrations?
      (apply-local-d1-migrations! d1-database))
    (let [procs (mapv (fn [{:keys [name dir cmd]}]
                        (println "Running:" name "-" cmd)
                        (process ["bash" "-lc" cmd] {:dir dir :inherit true}))
                      (processes auth-mode))]
      (doseq [proc procs]
        @proc))))
