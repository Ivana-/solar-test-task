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

;; first way - limiting max-connections for so requests per each input request (local tag-ch etc.)

;; (defn search [{:keys [params]}]
;;   (let [{:keys [tag]} params
;;         tags (set (if (string? tag) [tag] tag))
;;         tag-ch (chan max-connections)
;;         res-ch (chan)]
;;     (doseq [_ tags] (go (>! res-ch (request-tag-data (<! tag-ch)))))
;;     (doseq [tag tags] (>!! tag-ch tag))
;;     (->> tags
;;          (reduce (fn [acc _] (merge-with (partial merge-with +) acc (<!! res-ch))) {})
;;          (format-response tags))))

;; second way - limiting max-connections for so requests per all input requests (global tag-ch, adding [tag res-ch] etc.)
;; needs in very strong (!) tests for avoid blocking global tag-ch and that funny logic when one request creates processes
;; which may return data to another one etc.

(defonce ^:private tag-ch (chan max-connections))

(defn search [{:keys [params]}]
  (let [{:keys [tag]} params
        tags (set (if (string? tag) [tag] tag))
        res-ch (chan)]
    (doseq [_ tags] (go (let [[tag ch] (<! tag-ch)]
                          (>! ch (request-tag-data tag)))))
    (doseq [tag tags] (>!! tag-ch [tag res-ch]))
    (->> tags
         (reduce (fn [acc _] (merge-with (partial merge-with +) acc (<!! res-ch))) {})
         (format-response tags))))
