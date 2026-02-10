(ns logseq.tasks.dev.desktop
  "Tasks for desktop (electron) development"
  (:require [babashka.tasks :refer [shell]]
            [babashka.fs :as fs]
            [logseq.tasks.util :as task-util]))

(defn watch
  "Watches environment to reload cljs, css and other assets"
  []
  (shell "yarn electron-watch"))

(defn open-dev-electron-app
  "Opens the Electron dev app when watch process has built main.js"
  []
  (let [start-time (java.time.Instant/now)]
    (dotimes [_n 1000]
             (if (and (fs/exists? "static/js/main.js")
                      (task-util/file-modified-later-than? "static/js/main.js" start-time))
               ;; Run Electron Forge directly from static/.
               ;; The root-level gulp wrapper can surface false-negative exit codes.
               (shell "yarn --cwd static electron:dev")
               (println "Waiting for app to build..."))
             (Thread/sleep 1000))))
