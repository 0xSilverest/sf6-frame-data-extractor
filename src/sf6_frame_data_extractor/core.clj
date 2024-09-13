(ns sf6-frame-data-extractor.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as str]
            [net.cgrand.enlive-html :as html]))

(def user-agent "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")

(defn fetch-html [url]
  (let [response (http/get url
    {:headers {"User-Agent" user-agent
               "Accept" "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
               "Accept-Language" "en-US,en;q=0.5"
               "Accept-Encoding" "gzip, deflate, br"
               "DNT" "1"
               "Connection" "keep-alive"
               "Upgrade-Insecure-Requests" "1"
               "Sec-Fetch-Dest" "document"
               "Sec-Fetch-Mode" "navigate"
               "Sec-Fetch-Site" "none"
               "Sec-Fetch-User" "?1"
               "Cache-Control" "max-age=0"}
     :cookie-policy :standard
     :cookie-store (clj-http.cookies/cookie-store)
     :throw-exceptions false})]

    (if (= 200 (:status response))
      (-> response
          :body
          java.io.StringReader.
          html/html-resource)
      (throw (ex-info "Failed to fetch page"
        {:status (:status response)
         :body (:body response)})))))

(defn extract-parenth-text [s]
  (or (second (re-find #"\(([^)]+)\)" s)) ""))

(defn remove-parenth-text-at-start [s]
  (str/replace s #"^\s*\([^)]*\)\s*" ""))

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
  })

(def motion-mapping
  {"236"   "QCF"
   "214"   "QCB"
   "623"   "DP"
   "421"   "RDP"
   "41236" "HCF"
   "63214" "HCB"
   "6"     "F"
   "4"     "B"
   "5"     "N"
   "2"     "D"
   "8"     "U"})

(defn find-longest-motion [input]
  (->> motion-mapping
       (sort-by (comp count key) >)
       (filter #(str/starts-with? input (key %)))
       first))

(defn simplify-motion [input]
  (if (empty? input)
    []
    (if-let [[motion shortcut] (find-longest-motion input)]
      (cons shortcut (simplify-motion (subs input (count motion))))
      (cons (subs input 0 1) (simplify-motion (subs input 1))))))

(defn combine-repeated-motions [motions]
  (reduce
    (fn [acc motion]
      (if (= motion (last acc))
        (conj (pop acc) (str "2x" motion))
        (conj acc motion)))
    []
    motions))

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

(defn transform-input [translate-fn input]
  (let [parts (str/split input #"([/()+])")
        transformed-parts
        (map (fn [part]
               (cond
                 (re-matches #"\d+" part)
                 (translate-fn part)

                 (re-matches #"[LMHPK]+" part)
                 (group-buttons (re-seq #"(?:[LMH][PK]|[PK])" part))

                 :else part))
             parts)
        special-chars (re-seq #"[/()+]" input)]
    (str/join (interleave transformed-parts (concat special-chars (repeat ""))))))

(defn transform-to-text [input]
  (transform-input #(str/join (combine-repeated-motions (simplify-motion %))) input ))

(defn parse-input [node]
  (let [parenth-text (extract-parenth-text
                            (html/text (first (html/select node [:.frame_classic___gpLR]))))
        num-notation (->> (html/select node [:.frame_classic___gpLR])
                       first
                       :content
                       (map parse-input-element)
                       (remove str/blank?)
                       (str/join "")
                       remove-parenth-text-at-start
                       str/trim
                       (transform-input identity))]
    {:num-notation num-notation
     :text-notation (transform-to-text num-notation)
     :parenthetical-note parenth-text
    }))

(defn sanitize-text [text]
  (when text
    (let [trimmed (-> text
                      (str/replace #"\n" "")
                      (str/replace #"\s+" " ")
                      str/trim)]
      (when-not (str/blank? trimmed)
        trimmed))))

(defn extract-text [node selector]
  (-> node (html/select selector) first html/text sanitize-text))

(defn extract-list [node selector]
  (->> (html/select node selector)
       (map html/text)
       (map sanitize-text)
       (remove nil?)))

(defn convert-move-type [move-type]
  (case move-type
    "Normal Moves" "Normal"
    "Special Moves" "Special"
    "Unique Attacks" "Unique"
    "Super Arts" "SA"
    "Throws" "Throws"
    "Common Moves" "Common"))

(defn parse-move [move-type row]
  (let [input (parse-input row)
        existing-notes (extract-text row [:.frame_note_hfwBr])
        combined-notes (str/join " " (remove str/blank? [(:parenthetical-note input) existing-notes]))]
  {:move-type (convert-move-type (sanitize-text move-type))
   :name (extract-text row [:.frame_arts__ZU5YI])
   :input {:numerical-notation (str/trim (:num-notation input))
           :text-notation      (str/trim (:text-notation input))}
   :startup (extract-text row [:.frame_startup_frame__Dc2Ph])
   :active (extract-text row [:.frame_active_frame__6Sovc])
   :recovery (extract-text row [:.frame_recovery_frame__CznJj])
   :on-hit (extract-text row [:.frame_hit_frame__K7xOz])
   :on-block (extract-text row [:.frame_block_frame__SfHiW])
   :cancel-ability (extract-text row [:.frame_cancel__hT_hr])
   :damage (extract-text row [:.frame_damage__HWaQm])
   :combo-scaling (extract-list row [:.frame_combo_correct__hCDUB :li])
   :drive-gauge-gain-hit (extract-text row [:.frame_drive_gauge_gain_hit___Jg7j])
   :drive-gauge-lose-dguard (extract-text row [:.frame_drive_gauge_lose_dguard__4uQOc])
   :drive-gauge-lose-punish (extract-text row [:.frame_drive_gauge_lose_punish__mFrmM])
   :sa-gauge-gain (extract-text row [:.frame_sa_gauge_gain__oGcqw])
   :attribute (extract-text row [:.frame_attribute__1vABD])
   :notes combined-notes}))

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

(defn scrape-to-json [url output-file]
  (let [parsed-html (fetch-html url)
        moves (parse-moves parsed-html)
        json-data (json/generate-string moves {:pretty true})]
    (spit output-file json-data)
    (println "Data has been scraped and saved to" output-file)))

(defn -main [& args]
  (if (empty? args)
    (println "No args provided!")
    (doseq [arg args]
      (let [url (str "https://www.streetfighter.com/6/character/" arg "/frame")
           output-file (str arg "_moves_data.json")]
       (scrape-to-json url output-file)))))
