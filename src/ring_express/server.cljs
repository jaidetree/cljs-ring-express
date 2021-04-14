(ns ring-express.server
  (:require
   [cljs.pprint :refer [pprint]]
   ["url" :refer [URL]]
   ["express" :as express]
   [reagent.dom.server :as rdom]))

(defonce app-ref (atom nil))

(defn wrap-default-view
  [handler]
  (fn [req]
    (handler
     {:headers {:content-type "text/html"}
      :body (rdom/render-to-string [:h1 "Hello World"])})))

(defn wrap-logging
  [handler]
  (fn [req]
    (pprint req)
    (let [res (handler req)]
      (pprint res)
      res)))

(def middleware
  (-> identity
      (wrap-default-view)
      (wrap-logging)))

(defn str->url
  [url-str]
  (URL. (str "http://" url-str)))

(defn req->hash-map
  [^js/Object req]
  (let [url (str->url (str (.. req -headers -host) (.-url req)))]
    {
     :server-port (if-let [port (some-> url (.-port) (js/Number))]
                    port
                    80)
     :server-name (.-hostname req)
     :remote-addr (.-ip req)
     :uri (.-path req)
     :query-string (.-search url)
     :query (.-query req)
     :scheme (.-protocol req)
     :request-method (keyword (.-method req))
     :headers (js->clj (.-headers req) :keywordize true)
     :body (.-body req)
     }))

(defn set-headers
  [res res-map]
  (.set res (clj->js (:headers res-map))))

(defn set-body
  [res res-map]
  (.send res (:body res-map)))

(defn create-server
  []
  (-> (express)
      (.all "*"
            (fn [req res next]
              (let [req (req->hash-map req)
                    res-map (middleware req)]
                (set-headers res res-map)
                (set-body res res-map))))
      (.listen 4000)))

(defn main
  [& _args]
  (println "Server starting")
  (let [app (create-server)]
    (.on js/process 'SIGTERM'
         (fn []
           (println "Gracefully shutting down server")
           (.close app (fn []
                         (println "Server shut down")
                         (reset! app-ref nil)))))
    (reset! app-ref app)
    app))

(defn stop
  [done]
  (when-some [app @app-ref]
    (.close app
            (fn [err]
              (js/console.log "server stopped" err)
              (done)))))

(defn start
  []
  (main))
