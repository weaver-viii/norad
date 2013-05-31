(ns norad.sqs
  (:require [immutant.messaging :as msg]
            [cemerick.bandalore :as sqs]
            [clojure.java.io :refer [resource]]))

(.setLevel (java.util.logging.Logger/getLogger "com.amazonaws")
           java.util.logging.Level/WARNING)

(defonce credentials (read-string (slurp (resource "sqs.clj"))))
(defonce client (sqs/create-client (:aws-id credentials)
                                   (:aws-secret-key credentials)))

(defn queues []
  (sqs/list-queues client))

(defn get-or-make-queue []
  (let [qs (queues)]
    (if (some #(.contains % "norad-notifications") qs)
      (first qs)
      (do
        (println "Creating norad SQS queue...")
        (let [qname (sqs/create-queue client "norad-notifications")]
          (println "Created queue:" qname)
          qname)))))

(defn delete-queue []
  (let [qname (get-or-make-queue)]
    (sqs/delete-queue client qname)))

(defonce q (get-or-make-queue))

(defn enqueue-notification
  [{:keys [body] :as msg}]
  (msg/publish "queue.notifications" body))

(defn consume-and-notify
  []
  (dorun
   (map
    (sqs/deleting-consumer client enqueue-notification)
    (sqs/receive client q :limit 100))))

(defn publish-message [msg]
  (sqs/send client q msg))
