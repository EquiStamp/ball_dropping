(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'ball-dropping/server)
(def version "1.0.0")
(def class-dir "target/classes")
(def basis (b/create-basis {:project "deps.edn"}))
(def uber-file (format "target/%s-%s-standalone.jar" (name lib) version))

(defn clean [_]
  (b/delete {:path "target"})
  (b/delete {:path "classes"}))

(defn uber [_]
  (clean nil)
  (println "Copying source files...")
  (b/copy-dir {:src-dirs ["src/clj" "resources"]
               :target-dir class-dir})
  (println "Compiling Clojure files...")
  (b/compile-clj {:basis basis
                  :src-dirs ["src/clj"]
                  :class-dir class-dir
                  :compile-opts {:direct-linking true}
                  :ns-compile ['ball-dropping.lambda]})
  (println "Creating uberjar...")
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis basis
           :main 'ball-dropping.lambda})) 