(ns solar-test-task.search
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.core.async :refer [>! <! >!! <!! go chan]]
            [clojure.string :as str]))

;; settings ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private max-connections 2)
(defonce ^:private response-format :html) ;; or :json
(defonce ^:private request-socket-timeout 3000)
(defonce ^:private request-connection-timeout 3000)

;; service functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- request-tag-data [tag]
  (try
    (when-let [items (-> (str "https://api.stackexchange.com/2.3/search?pagesize=100&order=desc&sort=creation&site=stackoverflow&tagged=" tag)
                         (http/get {:socket-timeout request-socket-timeout
                                    :connection-timeout request-connection-timeout})
                         :body
                         (json/parse-string keyword)
                         :items)]
      (reduce (fn [acc {:keys [tags is_answered]}]
                (reduce (fn [a t]
                          (cond-> a
                            true (update-in [t "total"] (fnil inc 0))
                            is_answered (update-in [t "answered"] (fnil inc 0))))
                        acc tags))
              {} items))
    (catch Exception e {"ERRORS" {tag 1, (ex-message e) 1}})))

(defn- format-response [tags data]
  (if (= :json response-format)
    {:status 200
     :headers {"Content-Type" "application/json"}
     :body (json/generate-string data)}
    (format
     "<!DOCTYPE html>
<html>
<body>
<h2>%s</h2>
<p>(в задании сказано, что надо в формате JSON, но при этом pretty print :) поэтому возвращаю text/html с интегрированным JSON - переделать на настоящий application/json пара секунд)</p>
<pre>%s</pre>
</body>
</html>"
     (str "tags: " (->> tags (str/join ", ")))
     (json/generate-string data {:pretty true}))))

;; public api handler ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; we can define it ether as global channel - for limiting connections on any async income requests
;; or local one (see below in let form inside search - for limiting connections per each input request)
;; (defonce ^:private con-ch (chan max-connections))

(defn search [{:keys [params]}]
  (let [{:keys [tag]} params
        tags (set (if (string? tag) [tag] tag))
        con-ch (chan max-connections) ;; may comment here & uncomment global one above
        res-ch (chan) ;; may make it buffered as (chan (count tags)) - it affects olny on little performance issues, not critical imho
        ]
    (doseq [tag tags]
      (>!! con-ch 1)
      (go (let [res (request-tag-data tag)]
            (<! con-ch)
            (>! res-ch res))))
    (->> tags
         (reduce (fn [acc _] (merge-with (partial merge-with +) acc (<!! res-ch))) {})
         (format-response tags))))


(comment

  (defn mock [i start]
    (Thread/sleep (+ 1000 (* 5 i)))
    (let [passed (quot (- (. System (nanoTime)) start) 1000000)]
      (prn i passed)
      i))

  (defn test-go [start rng con-ch res-ch]
    (doseq [i rng]
      (>!! con-ch 1)
      (go (let [res (mock i start)]
            (<! con-ch)
            (>! res-ch res)))))

  (defn test-future [start rng con-ch res-ch]
    (doseq [i rng]
      (>!! con-ch 1)
      (future (let [res (mock i start)]
                (<!! con-ch)
                (>!! res-ch res)))))

  (defn run-wrapper [n test-impl]
    (let [start (. System (nanoTime))
          rng (range 1 (inc n))
          con-ch (chan 10)
          res-ch (chan)]
      (test-impl start rng con-ch res-ch)
      (reduce (fn [acc _] (conj acc (<!! res-ch))) [] rng)))

  (run-wrapper 22 test-go) ;; limited by 8 processes - see below
;; =>
;; 1 1005
;; 2 1010
;; 3 1015
;; 4 1020
;; 5 1026
;; 6 1032
;; 7 1037
;; 8 1042
;; 9 2052
;; 10 2061
;; 11 2076
;; 12 2081
;; 13 2092
;; 14 2104
;; 15 2113
;; 16 2123
;; 17 3138
;; 18 3152
;; 19 3173
;; 20 3183
;; 21 3198
;; 22 3214
;; [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22]

  (run-wrapper 22 test-future) ;; fits channel timit of 10 - see below
;; =>
;; 1 1005
;; 2 1013
;; 3 1018
;; 4 1023
;; 5 1033
;; 6 1038
;; 7 1044
;; 8 1049
;; 9 1055
;; 10 1060
;; 11 2063
;; 12 2075
;; 13 2085
;; 14 2095
;; 15 2112
;; 16 2123
;; 17 2131
;; 18 2148
;; 19 2157
;; 20 2162
;; 21 3170
;; 22 3188
;; [1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22]

;;
)