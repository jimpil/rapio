(ns rapio.core
  (:require [rapio.internal :refer [local-file-size chunk-for-n ->file]])
  (:import  [java.io RandomAccessFile]
            [java.util.concurrent CountDownLatch]))

(def ^:private CPUS
  (.availableProcessors (Runtime/getRuntime)))

(def ^:constant ARRAY-CAPACITY
  (int (- Integer/MAX_VALUE 2)))


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
     (let [ret# (do ~@body)]
       (.countDown ^CountDownLatch ~(with-meta latch {:tag 'java.util.concurrent.CountDownLatch}))
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
   If <threads> = 1 and <raw-bytes?> = false, delegates to `clojure.core/slurp`.
   LIMITATION: works on files up to 2GB."
  [source & {:keys [^String encoding threads raw-bytes?]
             :or {encoding "UTF-8"
                  threads  CPUS}}]
  (assert (pos? threads))

  (if (or raw-bytes? (> threads 1))
    (let [total (local-file-size source)
          _ (assert (<= total ARRAY-CAPACITY)
                    "File too big! See `pslurp-big` for files larger than 2GB...")
          chunks (chunk-for-n threads total)
          target (byte-array total)]
      ;; sanity check
      ;(assert (= threads (count chunks)))
      ;; main work
      (do-latched-work chunks
        (fn [start size]
          (let [source (->file source)]
            (with-open [^RandomAccessFile in (RandomAccessFile. source "r")]
              (.seek in start)
              (.read in target start size)))))
      ;; return value
      (cond-> target (not raw-bytes?) (String. encoding)))
    ;; don't even bother if threads = 1 AND caller wants String - use plain clojure.core/slurp
    (slurp source :encoding encoding)))


(defn pslurp-big
  "Similar to `pslurp`, but intended to be used with files larger than 2GB.
   Returns a seq (per `pmap`) of N byte-arrays."
  [source]
  (let [total (local-file-size source)
        _ (assert (> total ARRAY-CAPACITY)
                  "File not big enough! See `pslurp` for files smaller than 2GB...")
        nchunks (cond-> (quot total ARRAY-CAPACITY)
                        (pos? (rem total ARRAY-CAPACITY))
                        inc)
        chunks (chunk-for-n nchunks total)]
    ;; sanity check
    ;(assert (= nchunks (count chunks)))
    ;; main work
    (doall
      (pmap
        (fn [[start end]]
          (let [source (->file source)
                size (- end start)
                target (byte-array size)]
            (with-open [^RandomAccessFile in (RandomAccessFile. source "r")]
              (.seek in start)
              (.read in target 0 size))
            target))
        chunks))))


(defn pspit
  ""
  [dest content & {:keys [threads ^String encoding]
                   :or {encoding "UTF-8"
                        threads  CPUS}}]
  (assert (pos? threads))

  (let [^bytes all-bytes (if (string? content)
                           (.getBytes ^String content encoding)
                           content)
        total (alength all-bytes)
        chunks (chunk-for-n threads total)]
    ;; sanity check
    ;(assert (= threads (count chunks)))
    ;; main work
    (do-latched-work chunks
      (fn [start size]
        (with-open [^RandomAccessFile out (RandomAccessFile. (->file dest) "rw")]
          (.seek out start)
          (.write out all-bytes start size))))))

(defn pspit-big
  "Similar to `pspit`, but intended to be used against multiple <contents>,
   whose concatenation wouldn't fit in a single String or byte-array (larger than 2GB)."
  [dest contents & {:keys [^String encoding]
                    :or {encoding "UTF-8"}}]
  (let [all-bytes (map (fn [content]
                         (if (string? content)
                           (.getBytes ^String content encoding)
                           content))
                       contents)
        total (->> all-bytes
                   (map #(alength ^bytes %))
                   (apply +))
        _ (assert (< total Long/MAX_VALUE))
        chunks (chunk-for-n (count contents) total)]
    ;; sanity check
    ;(assert (= threads (count chunks)))
    ;; main work
    (dorun
      (pmap
        (fn [[start _] ^bytes content]
          (with-open [^RandomAccessFile out (RandomAccessFile. (->file dest) "rw")]
            (.seek out start)
            (.write out content 0 (alength content))))
        chunks
        all-bytes))))

