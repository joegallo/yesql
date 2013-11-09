(ns yesql.core
  (:refer-clojure :exclude [replace])
  (:require [clojure.string :refer [join replace split-lines]]
            [clojure.java.jdbc :as sql]
            [clojure.java.jdbc.sql :refer [select where]]
            [yesql.named-parameters :refer :all]
            [yesql.util :refer :all]))

(defn sql-comment-line?
  "If string is an SQL comment line, returns the text after the comment marker, otherwise nil."
  [string]
  (->> string
       (re-matches #"^\p{Blank}*--\p{Blank}*(.*)")
       second))

(defn extract-docstring
  "Returns the docstring, if any, within the given sqlfile."
  [sqlfile]
  (->> sqlfile
       split-lines
       (map sql-comment-line?)
       (remove nil?)
       (join "\n")))

(defn extract-query
  "Returns the query for the given sqlfile."
  [sqlfile]
  (->> sqlfile
       split-lines
       (remove sql-comment-line?)
       (join "\n")))

(defn make-query-function
  [query]
  (fn [db & parameters]
    (lazy-seq
     (sql/query db
                (cons query parameters)))))

(defn- replace-question-mark-with-gensym
  [parameter]
  (if (= parameter '?)
    (gensym "P_")
    parameter))

;; TODO Tidy
;; TODO :file metadata. Seems to get swallowed by 'def.
(defmacro defquery
  "Defines a query function, as defined in the given SQL file.
Any comments in that file will form the docstring."
  [name filename]
  (let [file (slurp-from-classpath filename)
        docstring (extract-docstring file)
        query (extract-query file)
        split-query (split-at-parameters query)
        arglist (vec (filter symbol? split-query))
        dbsym (gensym "DB_")]
    `(def ~(with-meta name
             {:arglists `(quote ~(list (vec (cons 'db arglist))))
              :doc docstring})
       (fn ~(vec (cons dbsym arglist))
         (lazy-seq
          (sql/query ~dbsym
                     (reassemble-query '~split-query ~arglist)))))))
