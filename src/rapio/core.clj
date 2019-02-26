(ns rapio.core
  (:require [rapio.internal :refer [local-file-size chunk-for-threads]]
            [clojure.java.io :as jio])
  (:import  [java.io File RandomAccessFile]
            [java.util.concurrent CountDownLatch]))

(def ^:private available-cpus
  (.availableProcessors (Runtime/getRuntime)))


(defmacro with-latch
  [[sym latch] & body]
  `(let [~sym ~(with-meta latch {:tag 'java.util.concurrent.CountDownLatch})]
     (do ~@body)
     (.await ~sym)))


(defmacro latched-future
  "Same as `clojure.core/future`, but 'linked' with a
   CountDownLatch which is `.countDown()` after <body> executes."
  [latch & body]
  `(future
     (let [l# ~(with-meta latch {:tag 'java.util.concurrent.CountDownLatch})
           ret# (do ~@body)]
       (.countDown l#)
       ret#)))

(defn- do-latched-work
  "Takes some chunks and a <work> fn of two arguments (start,size).
   Spawns `(count chunks)` latched-futures to work on each chunk.
   Returns nil."
  [chunks work]
  (with-latch [latch (CountDownLatch. (count chunks))]
    (doseq [[start end] chunks]
      (latched-future latch
        (work start (unchecked-subtract end start))))))


(defn pslurp
  "A parallel version of `clojure.core/slurp` intended to be used
   against local files. Therefore, the only concrete types allowed
   as <source> are String, File & URI/URL (only of 'file' protocol/scheme).
   <opts> can include :encoding (default 'UTF-8'), :raw-bytes? (default false),
   and/or :threads, which controls how the contents of <source> will be chunked,
   and ultimately the level of parallelism. Defaults to `.availableProcessors()`.
   If <threads> = 1 and <raw-bytes?> = false, delegates to `clojure.core/slurp`."
  [source & {:keys [^String encoding threads raw-bytes?]
             :or {encoding "UTF-8"
                  threads available-cpus}}]
  (assert (pos? threads))

  (if (or raw-bytes? (> threads 1))
    (let [total (local-file-size source)
          chunks (chunk-for-threads threads total)
          target (byte-array total)]
      ;; sanity check
      ;(assert (= threads (count chunks)))
      ;; main work
      (do-latched-work chunks
        (fn [start size]
          (let [^File source (cond-> source (not (instance? File source)) jio/file)]
            (with-open [^RandomAccessFile in (RandomAccessFile. source "r")]
              (.seek in start)
              (.read in target start size)))))
      ;; return value
      (cond-> target (not raw-bytes?) (String. encoding)))
    ;; don't even bother if threads = 1 AND caller wants String - use plain clojure.core/slurp
    (slurp source :encoding encoding)))


(defn pspit
  ""
  [dest content & {:keys [threads ^String encoding]
                   :or {encoding "UTF-8"
                        threads available-cpus}}]
  (assert (pos? threads))

  (let [all-bytes (if (string? content)
                    (.getBytes ^String content encoding)
                    content)
        total (alength ^bytes all-bytes)
        chunks (chunk-for-threads threads total)]
    ;; sanity check
    ;(assert (= threads (count chunks)))
    ;; main work
    (do-latched-work chunks
      (fn [start size]
        (let [^File f (cond-> dest (not (instance? File dest)) jio/file)]
          (with-open [^RandomAccessFile out (RandomAccessFile. f "rw")]
            (.seek out start)
            (.write out all-bytes start size)))))))
