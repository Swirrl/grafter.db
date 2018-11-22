(ns grafter.db.core
  (:require [integrant.core :as ig])
  (:import (java.net URI)))

(defmethod ig/init-key ::pretty-print-uris? [_ opts]
  (when opts
    ;; customise clojure.core print methods, e.g. println
    (defmethod print-method URI [x writer]
      (print-method (symbol (format "#uri \"%s\"" (symbol (str x))))
                    writer)))
  opts)