(ns ca.cawala.geotrie-test
  (:require [clojure.test :refer :all]
            [ca.cawala.geotrie :refer :all]))

(deftest area-test
  (testing "Test population of Newfoundland"
    (let [pop (main)]
      (is (and (> 517678 pop) (< 517677 pop))))))
