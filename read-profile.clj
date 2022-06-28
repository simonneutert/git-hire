(defn- lower-case-str-map-key-compare
  "compares the lower-cased stringified key of a map against a lower-cased string"
  [m s]
  (filterv #(str/includes? (str/lower-case (str (key %))) (str/lower-case s)) m))

(defn- find-profile
  "returns the first matching path to a profile name string given"
  [name]
  (let [lot (->> (map str (fs/glob "." (str "**"  "{.edn}")))
                 (reduce #(assoc %1 (str/lower-case %2) %2) {}))
        match (first (lower-case-str-map-key-compare lot name))]
    (if match (val match) (println "Nothing found!"))))

(defn read-profile
  "reads profile.edn file in"
  [name]
  (let [path-to-profile (find-profile name)]
    (if (nil? path-to-profile)
      nil
      (-> path-to-profile (slurp) (edn/read-string)))))

;; entrypoint
(if (> (count *command-line-args*) 0)
  (let [profile-name (first *command-line-args*)]
    (read-profile profile-name))
  (println "Please pass one arg being the profile name only"))