(ns rapio.core-test
  (:require [clojure.test :refer :all]
            [rapio.core :refer :all]
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

(deftest pspit-tests
  (let [temp-dir (System/getProperty "java.io.tmpdir")
        temp-file-name (str temp-dir "/" (System/currentTimeMillis) "_")
        ^bytes content (force test-resource-bytes)]

    (try

      ;(spit temp-file-name @test-resource-str)
      (pspit temp-file-name content :threads 2)

      (is (Arrays/equals content
                         ^bytes (pslurp temp-file-name :raw-bytes? true)))
      (finally
        (jio/delete-file temp-file-name)))
    )

  )
