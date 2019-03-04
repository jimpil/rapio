(ns rapio.internal-test
  (:require [clojure.test :refer :all]
            [rapio.internal :refer [chunk-for-n]]))


(deftest chunk-for-n-tests

  (let [n2 (chunk-for-n 2 1800)
        n3 (chunk-for-n 3 1800)
        n4 (chunk-for-n 4 1800)
        n5 (chunk-for-n 5 1800)
        n6 (chunk-for-n 6 1800)]

    (is (= [[0 900] [900 1800]] n2))
    (is (= [[0 600] [600 1200] [1200 1800]] n3))
    (is (= [[0 450] [450 900] [900 1350] [1350 1800]] n4))
    (is (= [[0 360] [360 720] [720 1080] [1080 1440] [1440 1800]] n5))
    (is (= [[0 300] [300 600] [600 900] [900 1200] [1200 1500] [1500 1800]] n6))

    )
  )
