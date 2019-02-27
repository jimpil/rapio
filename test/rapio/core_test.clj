(ns rapio.core-test
  (:require [clojure.test :refer :all]
            [rapio.core :refer :all]
            [rapio.internal :as internal]
            [clojure.java.io :as io]
            [clojure.java.io :as jio])
  (:import  [java.util Arrays]))

(defonce test-resource
  (delay ;; 2.5MB text-file
    (io/resource "test_file.exclude")))

(defonce test-resource-str
  (delay
    (slurp @test-resource)))

(defonce test-resource-bytes
  (delay
    (.getBytes ^String @test-resource-str)))

(deftest pslurp-tests

  (testing "returning raw-bytes - serial"
    (is (Arrays/equals
          ^bytes (force test-resource-bytes)
          ^bytes (pslurp @test-resource :raw-bytes? true :threads 1))))

  (testing "returning raw-bytes - parallel"
    (is (Arrays/equals
          ^bytes (force test-resource-bytes)
          ^bytes (pslurp @test-resource :raw-bytes? true :threads 2)))

    (is (Arrays/equals
          ^bytes (force test-resource-bytes)
          ^bytes (pslurp @test-resource :raw-bytes? true :threads 3)))

    (is (Arrays/equals
          ^bytes (force test-resource-bytes)
          ^bytes (pslurp @test-resource :raw-bytes? true))))

  (testing "returning String - parallel up to 4 threads"
    (is (= @test-resource-str
           (pslurp @test-resource :threads 2)))

    (is (= @test-resource-str
           (pslurp @test-resource :threads 3)))

    (is (= @test-resource-str
           (pslurp @test-resource :threads 4))))
  )


(defn- pcopy ;; `clojure.java.io/copy` is optimised for [File File]
  [src dest]
  (->> (pslurp src :raw-bytes? true)
       (pspit dest)))

(deftest pspit-tests
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        temp-file-name (str temp-dir "/" (System/currentTimeMillis) "_")
        temp-file-name-copy (str temp-file-name 2)
        ^bytes content (force test-resource-bytes)]

    (try

      ;(spit temp-file-name @test-resource-str)
      (pspit temp-file-name content :threads 2)
      (pcopy temp-file-name temp-file-name-copy)

      (is (Arrays/equals content
                         ^bytes (pslurp temp-file-name :raw-bytes? true)))
      (is (Arrays/equals content
                         ^bytes (pslurp temp-file-name-copy :raw-bytes? true)))

      (finally
        (jio/delete-file temp-file-name)
        (jio/delete-file temp-file-name-copy)))
    )

  )

(comment ;; local testing of pslurp/pspit-big

  ;; increase REPL heap before eval-ing this!

    (let [large-file "/home/dimitris/Desktop/B375/update.zip" ;; 2.2GB
          arrays (pslurp-big large-file)
          lengths (map alength arrays)
          lengths-sum (apply + lengths)
          large-file-copy (str large-file "-DELETEME")]
      (try

        (pspit-big large-file-copy arrays)

        (and
          (= lengths-sum ;; didn't miss any bytes
             (internal/local-file-size large-file))

          (= lengths-sum
             (internal/local-file-size large-file-copy)))

        (finally
          (jio/delete-file large-file-copy)))
      )

  )

