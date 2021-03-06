(ns norad.sqs
  (:require [immutant.messaging :as msg]
            [cemerick.bandalore :as sqs]
            [clojure.java.io :refer [resource]]
            [clojure.tools.logging :as log]))

(.setLevel (java.util.logging.Logger/getLogger "com.amazonaws")
           java.util.logging.Level/WARNING)

(defonce credentials (read-string (slurp (resource "sqs.clj"))))
(defonce client (sqs/create-client (:aws-id credentials)
                                   (:aws-secret-key credentials)))

(defn queues []
  (sqs/list-queues client))

(defn get-or-make-queue []
  (try
    (let [qs (queues)]
      (if (some #(.contains % "norad") qs)
        (first qs)
        (do
          (log/info "Creating norad SQS queue...")
          (let [qname (sqs/create-queue client "norad")]
            (log/info "Created queue:" qname)
            qname))))
    (catch Exception _ nil)))

(defn delete-queue []
  (let [qname (get-or-make-queue)]
    (sqs/delete-queue client qname)))

(def q (atom (get-or-make-queue)))

(defn enqueue-message
  [{:keys [body]}]
  (try
    (let [{:keys [queue msg]} (read-string body)
          queue (msg/queue queue)]
      (msg/publish queue msg))
    (catch Exception e
      (log/warn e "Unable to enqueue notification")
      (println "Unable to enqueue notification:" e))))

(defn consume-and-enqueue
  []
  (try
    (if (nil? @q)
      ;; There was a failure attempting to resolve the queue name, try again
      (reset! q (get-or-make-queue))
      ;; Or grab messages from it
      (dorun
       (map
        (sqs/deleting-consumer client enqueue-message)
        (sqs/receive client @q :limit 100))))
    (catch Throwable e
      (log/warn "Exception trying to consume SQS messages:" e))))

(defn publish-message [msg]
  (sqs/send client q msg))
