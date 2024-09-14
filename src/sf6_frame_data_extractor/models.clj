(ns sf6-frame-data-extractor.models
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(s/def ::move-type #{"Normal" "Special" "Unique" "SA" "Throws" "Common"})
(s/def ::name string?)
(s/def ::numerical-notation string?)
(s/def ::text-notation string?)
(s/def ::startup (s/nilable int?))
(s/def ::from (s/nilable int?))
(s/def ::to (s/nilable int?))
(s/def ::recovery (s/nilable int?))
(s/def ::on-hit (s/nilable int?))
(s/def ::on-block (s/nilable int?))
(s/def ::cancel-to (s/nilable string?))
(s/def ::damage (s/nilable int?))
(s/def ::combo-scaling (s/coll-of string?))
(s/def ::drive-gauge-gain-hit (s/nilable int?))
(s/def ::drive-gauge-lose-dguard (s/nilable int?))
(s/def ::drive-gauge-lose-punish (s/nilable int?))
(s/def ::sa-gauge-gain (s/nilable int?))
(s/def ::attribute (s/nilable string?))
(s/def ::notes (s/nilable string?))

(s/def ::input (s/keys :req-un [::numerical-notation ::text-notation]))
(s/def ::active (s/nilable (s/keys :req-un [::from ::to])))

(s/def ::move (s/keys :req-un [::move-type
                               ::name
                               ::input
                               ::startup
                               ::active
                               ::recovery
                               ::on-hit
                               ::on-block
                               ::cancel-to
                               ::damage
                               ::combo-scaling
                               ::drive-gauge-gain-hit
                               ::drive-gauge-lose-dguard
                               ::drive-gauge-lose-punish
                               ::sa-gauge-gain
                               ::attribute]
                      :opt-un [::notes]))

(defn parse-int [s]
  (try
    (Integer/parseInt (re-find #"\d+" s))
    (catch Exception _
      nil)))

(defn parse-active [s]
  (try
    (when (and s (string? s))
      (let [[from to] (map parse-int (-> s
                                         (str/replace #"[*,]" "")
                                         (str/split #"-")))]
        (when (and from to)
          {:from from, :to to})))
    (catch Exception _
      nil)))
