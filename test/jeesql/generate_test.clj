(ns jeesql.generate-test
  (:require [expectations :refer :all]
            [clojure.template :refer [do-template]]
            [jeesql.statement-parser :refer [tokenize]]
            [jeesql.generate :refer :all]))

(do-template [statement _ expected-parameters]
  (expect expected-parameters
          (expected-parameter-list statement *ns*))

  "SELECT * FROM user"
  => #{}

  "SELECT * FROM user WHERE user_id = :id"
  => #{:id}

  "SELECT * FROM user WHERE user_id = :name"
  => #{:name}

  "SELECT * FROM user WHERE user_id = :name AND country = :country AND age IN (:ages)"
  => #{:name :country :ages})

;;; Testing in-list-parmaeter for "IN-list" statements.
(expect [true true true nil true]
        (mapv in-list-parameter?
              (list []
                   (list)
                   (lazy-seq (cons 1 [2]))
                   {:a 1}
                   #{1 2 3})))

;;; Testing reassemble-query
(do-template [statement parameters _ rewritten-form]
  (expect rewritten-form
          (rewrite-query-for-jdbc (tokenize statement nil)
                                  parameters))

  "SELECT age FROM users WHERE country = :country"
  {:country "gb"}
  => ["SELECT age FROM users WHERE country = ?" "gb"]

  "SELECT age FROM users WHERE (country = :c1 OR country = :c2) AND name = :name"
  {:c1 "gb" :c2 "us"
   :name "tom"}
  => ["SELECT age FROM users WHERE (country = ? OR country = ?) AND name = ?" "gb" "us" "tom"]

;;; Vectors trigger IN expansion
  "SELECT age FROM users WHERE country = :country AND name IN (:names)"
  {:country "gb"
   :names ["tom" "dick" "harry"]}
  => ["SELECT age FROM users WHERE country = ? AND name IN (?,?,?)" "gb" "tom" "dick" "harry"]

;;; Lists trigger IN expansion
  "SELECT age FROM users WHERE country = :country AND name IN (:names)"
  {:country "gb"
   :names (list "tom" "dick" "harry")}
  => ["SELECT age FROM users WHERE country = ? AND name IN (?,?,?)" "gb" "tom" "dick" "harry"]

;;; Lazy seqs of cons of vectors trigger IN expansion
  "SELECT age FROM users WHERE country = :country AND name IN (:names)"
  {:country "gb"
   :names (lazy-seq (cons "tom" ["dick" "harry"]))}
  => ["SELECT age FROM users WHERE country = ? AND name IN (?,?,?)" "gb" "tom" "dick" "harry"]

;;; Maps do not trigger IN expansion
  "INSERT INTO json (source, data) VALUES (:source, :data)"
  {:source "google"
   :data {:a 1}}
  => ["INSERT INTO json (source, data) VALUES (?, ?)" "google" {:a 1}]

  "INSERT INTO json (data, source) VALUES (:data, :source)"
  {:source "google"
   :data {:a 1}}
  => ["INSERT INTO json (data, source) VALUES (?, ?)" {:a 1} "google"]

;;; Empty IN-lists are allowed by Jeesql and output as NULL
  "SELECT age FROM users WHERE country = :country AND name IN (:names)"
  {:country "gb"
   :names []}
  => ["SELECT age FROM users WHERE country = ? AND name IN (NULL)" "gb"]

  "SELECT * FROM users WHERE group_ids IN(:group_ids) AND parent_id = :parent_id"
  {:group_ids [1 2]
   :parent_id 3}
  => ["SELECT * FROM users WHERE group_ids IN(?,?) AND parent_id = ?" 1 2 3])
