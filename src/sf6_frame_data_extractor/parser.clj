(ns sf6-frame-data-extractor.parser
  (:require [clojure.string :as str]
            [sf6-frame-data-extractor.models :as models]
            [clojure.spec.alpha :as s]
            [net.cgrand.enlive-html :as html]))

(def input-mapping
  { "icon_punch_l.png" "LP"
    "icon_punch_m.png" "MP"
    "icon_punch_h.png" "HP"
    "icon_punch.png"   "P"
    "icon_kick_l.png"  "LK"
    "icon_kick_m.png"  "MK"
    "icon_kick_h.png"  "HK"
    "icon_kick.png"    "K"
    "key-u.png"        "8"
    "key-d.png"        "2"
    "key-r.png"        "6"
    "key-l.png"        "4"
    "key-nutral.png"   "5"
    "key-ul.png"       "7"
    "key-ur.png"       "9"
    "key-dl.png"       "1"
    "key-dr.png"       "3"
    "key-plus.png"     "+"
    "key-or.png"       "/"
    "key-circle.png"   "360"
    "key-lc.png"       "[4]"
    "key-dc.png"       "[2]"
  })

(def motion-mapping
  {"236"   "QCF"
   "214"   "QCB"
   "623"   "DP"
   "421"   "RDP"
   "41236" "HCF"
   "63214" "HCB"
   "360"   "SPD"
   "720"   "720"
   "6"     "f"
   "4"     "b"
   "5"     "n"
   "2"     "d"
   "8"     "u"
   "3"     "df"
   "7"     "ub"
   "9"     "uf"
   "1"     "db"
   "[4]"   "[b]"
   "[2]"   "[d]"
  })

(defn convert-move-type [move-type]
  (case move-type
    "Normal Moves" "Normal"
    "Special Moves" "Special"
    "Unique Attacks" "Unique"
    "Super Arts" "SA"
    "Throws" "Throws"
    "Common Moves" "Common"))


(defn clean-text [text]
  (when text
    (let [trimmed (-> text
                      (str/replace #"\n" "")
                      (str/replace #"\s+" " ")
                      str/trim)]
      (when-not (str/blank? trimmed)
        trimmed))))


(defn extract-text [node selector]
  (-> node (html/select selector) first html/text clean-text))

(defn extract-list [node selector]
  (->> (html/select node selector)
       (map html/text)
       (map clean-text)
       (remove nil?)))

(defn extract-parenth-content [s]
  (or (second (re-find #"\(([^)]+)\)" s)) ""))

(defn remove-parenth-content-at-start [s]
  (str/replace s #"^\s*\([^)]*\)\s*" ""))

(defn combine-360-motions [input]
  (-> input
      (str/replace #"360360" "720")))

(defn find-longest-motion [input]
  (->> motion-mapping
       (sort-by (comp count key) >)
       (filter #(str/starts-with? input (key %)))
       first))

(defn combine-repeated-buttons [input]
  (let [parts (str/split input #"\+")]
    (->> parts
         (partition-by identity)
         (mapcat (fn [group]
                   (if (> (count group) 1)
                     [(str (count group) "x" (first group))]
                     group)))
         (str/join "+"))))

(defn parse-input-element [element]
  (if (= (:tag element) :img)
    (let [src (get-in element [:attrs :src])
          filename (re-find #"[^/]+$" src)]
      (get input-mapping filename ""))
    (let [text (str/trim (html/text element))]
      (if (re-matches #"[LMHPKN]" text)
        ""
        text))))

(defn group-buttons [buttons]
  (if (> (count buttons) 1)
    (str/join "+" buttons)
    (first buttons)))

(defn transform-motion [motion]
  (get motion-mapping motion motion))

(defn translate-motion-input [input]
  (loop [remaining input
         result []
         current-motion ""
         current-count 1]
    (if (empty? remaining)
      (let [final-result (if (empty? current-motion)
                           result
                           (conj result (if (> current-count 1)
                                          (str current-count "x" current-motion)
                                          current-motion)))]
        (str/join "" final-result))
      (let [[motion rest-of-input] (if-let [[_ bracket-motion] (re-find #"^\[(.*?)\]" remaining)]
                                     [(str "[" bracket-motion "]") (subs remaining (+ 2 (count bracket-motion)))]
                                     (if-let [[longest-motion _] (find-longest-motion remaining)]
                                       [longest-motion (subs remaining (count longest-motion))]
                                       [(str (first remaining)) (subs remaining 1)]))
            translated-motion (transform-motion motion)]
        (if (= translated-motion current-motion)
            (recur rest-of-input result current-motion (inc current-count))
            (recur rest-of-input
                   (if (empty? current-motion)
                     result
                     (conj result (if (> current-count 1)
                                    (str current-count "x" current-motion)
                                    current-motion)))
                   translated-motion
                   1))))))

(defn transform-notation [input & {:keys [translate-fn]
                                   :or {translate-fn translate-motion-input}}]
  (let [parts (str/split input #"(\+|/)")
        transformed-parts
        (map (fn [part]
               (cond
                 (re-matches #"\[.*?\]\d*" part)
                 (translate-fn part)

                 (re-matches #"\d+" part)
                 (translate-fn part)

                 (re-matches #"[LMHPK]+" part)
                 (group-buttons (re-seq #"(?:[LMH][PK]|[PK])" part))

                 :else part))
             parts)
        special-chars (re-seq #"[/()+]" input)]
    (str/join (interleave transformed-parts (concat special-chars (repeat ""))))))

(defn transform-to-text [input]
  (transform-notation input))

(defn transform-input [translate-fn input]
  (transform-notation input :translate-fn translate-fn))

(defn parse-target-combos [input]
  (if-let [[_ first-part second-part]
          (re-find #"^(.*?[LMH][PK])(?![\s/+])(.*)$" input)]
    (if (empty? second-part)
      [first-part]
      [first-part second-part])
    [input]))

(def special-move-patterns
  #"236|QCF|214|QCB|623|DP|421|RDP|41236|HCF|63214|HCB|360|SPD|720|\[4\]|\[2\]"
)

(defn determine-normal-type [input name]
  (cond
    (or (str/starts-with? input "8")
        (str/includes? (str/lower-case name) "jumping")) {:num "8"
                                                          :text "j."}
    (str/starts-with? input "2") {:num  "2"
                                  :text "cr."}
    (or (str/starts-with? input "5")
        (str/includes? (str/lower-case name) "standing")) {:num "5"
                                                           :text "st."}
    :else {:num "" :text ""}))

(defn simplify-normal-input [input]
  (if (re-find special-move-patterns input)
    input
    (-> input
        (str/replace #"^[258]\+" "")
        (str/replace "+" ""))))

(defn transform-to-final-text-input [normal-type simplified-notation]
    (if (re-find special-move-patterns simplified-notation)
      (combine-repeated-buttons (transform-to-text simplified-notation))
      (str normal-type (transform-to-text simplified-notation))))

(defn parse-input [move-type node name]
  (let [parenth-text (extract-parenth-content
                       (html/text (first (html/select node [:.frame_classic___gpLR]))))
        num-notation (->> (html/select node [:.frame_classic___gpLR])
                          first
                          :content
                          (map parse-input-element)
                          (remove str/blank?)
                          (str/join "")
                          remove-parenth-content-at-start
                          str/trim
                          (transform-input identity)
                          combine-360-motions)
        normal-type  (if (= move-type "Normal")
                       (determine-normal-type num-notation name)
                       {:num "" :text ""})
        simplified-num-notation (if (= move-type "Normal")
                                  (simplify-normal-input num-notation)
                                  num-notation)
        simplified-text-notation (if (= move-type "Normal")
                                   (simplify-normal-input (translate-motion-input simplified-num-notation))
                                   (translate-motion-input simplified-num-notation))
        num-notation-split (parse-target-combos (str/join "" [(normal-type :num) simplified-num-notation]))
        text-notation-split (map #(transform-to-final-text-input (normal-type :text) %)
                                 (parse-target-combos simplified-text-notation))]
    {:num-notation (str/join "." (map #(str/replace % "+" "") num-notation-split))
     :text-notation (str/join "." text-notation-split)
     :parenthetical-note parenth-text}))

(defn parse-move [move-type row]
  (let [sanitized-move-type (convert-move-type (clean-text move-type))
        name (extract-text row [:.frame_arts__ZU5YI])
        input (parse-input sanitized-move-type row name)
        existing-notes (extract-text row [:.frame_note_hfwBr])
        combined-notes (str/join " " (remove str/blank? [(:parenth-note input) existing-notes]))
        move {:move-type sanitized-move-type
              :name name
              :input {:numerical-notation (str/trim (:num-notation input))
                      :text-notation      (str/trim (:text-notation input))}
              :startup (models/parse-int (extract-text row [:.frame_startup_frame__Dc2Ph]))
              :active (models/parse-active (extract-text row [:.frame_active_frame__6Sovc]))
              :recovery (models/parse-int (extract-text row [:.frame_recovery_frame__CznJj]))
              :on-hit (models/parse-int (extract-text row [:.frame_hit_frame__K7xOz]))
              :on-block (models/parse-int (extract-text row [:.frame_block_frame__SfHiW]))
              :cancel-to (extract-text row [:.frame_cancel__hT_hr])
              :damage (models/parse-int (extract-text row [:.frame_damage__HWaQm]))
              :combo-scaling (extract-list row [:.frame_combo_correct__hCDUB :li])
              :drive-gauge-gain-hit (models/parse-int (extract-text row [:.frame_drive_gauge_gain_hit___Jg7j]))
              :drive-gauge-lose-dguard (models/parse-int (extract-text row [:.frame_drive_gauge_lose_dguard__4uQOc]))
              :drive-gauge-lose-punish (models/parse-int (extract-text row [:.frame_drive_gauge_lose_punish__mFrmM]))
              :sa-gauge-gain (models/parse-int (extract-text row [:.frame_sa_gauge_gain__oGcqw]))
              :attribute (extract-text row [:.frame_attribute__1vABD])
              :notes combined-notes}]
    (if (s/valid? ::models/move move)
      move
      (do
        (println "Invalid move data:" move)
        (throw (ex-info "Invalid move data" (s/explain-data ::models/move move)))))))

(defn heading-row? [row]
  (some #(= "frame_heading__hh7Ah" %) (html/attr-values row :class)))

(defn parse-moves [parsed-html]
  (loop [rows (html/select parsed-html [:tbody :tr])
         current-move-type ""
         moves []]
    (if (empty? rows)
      moves
      (let [row (first rows)]
        (if (heading-row? row)
          (recur (rest rows)
                 (extract-text row [:span])
                 moves)
          (recur (rest rows)
                 current-move-type
                 (conj moves (parse-move current-move-type row))))))))
