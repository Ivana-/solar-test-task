(ns solar-test-task.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [solar-test-task.search :as search]))

(defroutes app-routes
  (GET "/search" [] search/search)
  (route/not-found "<!DOCTYPE html>
<html>
<body>
<h2>Тестовое задание Solar</h2>
<p>отрабатывает запросы на URL /search с параметрами tag, пример http://localhost:3000/search?tag=clojure&tag=scala</p>
</body>
</html>"))

(def app
  (wrap-defaults app-routes site-defaults))
