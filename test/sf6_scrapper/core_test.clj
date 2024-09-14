(ns sf6-scrapper.core-test
  (:require [clojure.test :refer :all]
            [sf6-frame-data-extractor.parser :refer [translate-motion-input]]))

(deftest test-translate-motion-input
  (testing "Translation of motion inputs"
    (are [input expected] (= expected (translate-motion-input input))
      "236236+K" "2xQCF+K"
      "236214+P" "QCFQCB+P"
      "421421+P" "2xRDP+P"
      "[4]646+P" "[b]fbf+P"
      "236+P"    "QCF+P"
      "214+K"    "QCB+K"
      "623+HP"   "DP+HP"
      "41236+LK" "HCF+LK"
      "360+P"    "SPD+P"
      "720+K"    "720+K"
      "6+MK"     "f+MK"
      "[2]8+MP"  "[d]u+MP")))
