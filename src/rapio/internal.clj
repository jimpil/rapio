(ns rapio.internal
  (:import [java.io File]
           [java.net URL URI]
           [java.nio.file Paths Files]))

(defprotocol LocalPath
  (local-path [this]))

(extend-protocol LocalPath
  String
  (local-path [s]
    (Paths/get s (make-array String 0)))

  File
  (local-path [file]
    (.toPath file))
  URL
  (local-path [url]
    (if (= "file" (.getProtocol url))
      (Paths/get (.toURI url))
      (throw (IllegalArgumentException. "Non-local URL!"))))
  URI
  (local-path [uri]
    (if (= "file" (.getScheme uri))
      (Paths/get uri)
      (throw (IllegalArgumentException. "Non-local URI!"))))
  )


(defn local-file-size
  "Returns the number of bytes for the specified local <source>
   which can be a String/File/URL/URI."
  [source]
  (Files/size (local-path source)))

(defn chunk-for-threads
  [threads total-length]
  (let [qchunk (quot total-length threads)]
    (->> [total-length]
         (concat
           (map (partial * qchunk)
                (range threads)))
         (partition 2 1))))
