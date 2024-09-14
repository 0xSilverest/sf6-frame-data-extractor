(ns sf6-frame-data-extractor.core
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clj-http.cookies]
            [clojure.string :as str]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [net.cgrand.enlive-html :as html]
            [sf6-frame-data-extractor.parser :as parser]
  ))

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

(defn move-to-csv-row [move]
  [(get move :name)
   (get-in move [:input :numerical-notation])
   (get-in move [:input :text-notation])
   (get move :move-type)
   (str (get-in move [:active :from]))
   (str (get-in move [:active :to]))
   (str (get move :startup))
   (str (get move :recovery))
   (str (get move :on-hit))
   (str (get move :on-block))
   (get move :cancel-to)
   (str (get move :damage))
   (str/join "," (get move :combo-scaling))
   (str (get move :drive-gauge-gain-hit))
   (str (get move :drive-gauge-lose-dguard))
   (str (get move :drive-gauge-lose-punish))
   (str (get move :sa-gauge-gain))
   (get move :attribute)
   (get move :notes)])

(defn export-to-csv [moves output-file]
  (let [headers ["Name" "Numerical Notation" "Text Notation" "Move Type" "Active From" "Active To" "Startup" "Recovery" "On Hit" "On Block" "Cancel To" "Damage" "Combo Scaling" "Drive Gauge Gain (Hit)" "Drive Gauge Loss (DGuard)" "Drive Gauge Loss (Punish)" "SA Gauge Gain" "Attribute" "Notes"]
        rows (map move-to-csv-row moves)]
    (with-open [writer (io/writer output-file)]
      (csv/write-csv writer (cons headers rows)))))

(defn export-data [moves format output-file]
  (case format
    "json" (spit output-file (json/generate-string moves {:pretty true}))
    "csv"  (export-to-csv moves output-file)
    (throw (ex-info "Unsupported format" {:format format}))))

(defn scrape-to-json [url output-file]
  (let [parsed-html (fetch-html url)
        moves (parser/parse-moves parsed-html)
        json-data (json/generate-string moves {:pretty true})]
    (spit output-file json-data)
    (println "Data has been scraped and saved to" output-file)))

(defn scrape-and-export [url output-file format]
  (let [parsed-html (fetch-html url)
        moves (parser/parse-moves parsed-html)]
    (export-data moves format output-file)
    (println "Data has been scraped and saved to" output-file "in" format "format")))

(defn -main [& args]
  (if (< (count args) 2)
    (println "Usage: lein run <character> --format <json|csv>")
    (let [character (first args)
          format-arg (nth args 1)
          format (if (= format-arg "--format") (nth args 2) "json")
          url (str "https://www.streetfighter.com/6/character/" character "/frame")
          output-file (str character "_moves_data." format)]
      (scrape-and-export url output-file format))))
