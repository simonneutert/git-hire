(import [java.net URLEncoder])

(def auth
  "Sets up the headers for requests
   depending on a set ENV"
  (let [token (System/getenv "GITHUB_HIRE_TOKEN")
        bearer (str "Bearer " token)]
    {:headers {"Accept" "application/vnd.github.v3+json"
               "Authorization" bearer}}))

(def base-url
  "https://api.github.com")

(defn base-url-search-users
  []
  (str base-url "/search/users"))

(defn get-json
  "returns json as edn (keywordized)"
  [url]
  (let [req (curl/get url auth)]
    (json/parse-string (:body req) true)))

(defn file-path-profiles
  []
  (str/lower-case "./profiles/"))

(defn file-path-location
  [location]
  (str (file-path-profiles) location))

(defn file-path-location-lang
  [location lang]
  (str (file-path-location location) "/" lang "/"))

(defn file-path-location-all
  [location]
  (str (file-path-location location) "/all/"))

(defn remove-forks
  [user-repos]
  (remove #(= true (:fork %)) user-repos))

(defn recursive-user-search
  "user results are kept in :items keyword
   so it seems reasonable to have this in a non-generalised function"
  [url runs existing-results]
  (loop [run 2
         results existing-results]
    (if (> run runs)
      results
      (let [new-url (str url "&page=" run)
            new-results (:items (get-json new-url))]
        (recur (inc run) (concat results new-results))))))

(defn user-location-search-url
  "hammers out the url for user search location"
  [per-page more-repos-than encoded-location]
  (str (base-url-search-users)
       "?per_page=" per-page
       "&q=location:" encoded-location
       "+repos:" ">" more-repos-than))

(defn user-location-language-search-url
  "hammers out the url for user search location and language"
  [per-page more-repos-than encoded-location encoded-lang]
  (str
   (user-location-search-url per-page more-repos-than encoded-location)
   "+language:" encoded-lang))

(defn per-page->runs
  [total divisor]
  (int (Math/ceil (double (/ total divisor)))))

(defn ->utf8
  [s]
  (URLEncoder/encode s "UTF-8"))

(defn search-users-by-location-lang-rich
  "1000 total results is the current user limit"
  [location lang more-repos-than]
  (loop
   [location location
    lang lang
    more-repos-than more-repos-than]
    (let [per-page 50
          more-repos-than more-repos-than
          encoded-location (->utf8 location)
          encoded-lang (->utf8 lang)
          url (user-location-language-search-url per-page more-repos-than encoded-location encoded-lang)
          res (get-json url)
          total-user-count (:total_count res)
          runs (per-page->runs total-user-count per-page)
          users (:items res)]
      (if (> total-user-count 1000)
        (do (Thread/sleep (* 4 1000))
            (recur location lang (+ 1 more-repos-than)))
        (if (> runs 1)
          (do (prn "getting users with more than " more-repos-than " repos")
              (recursive-user-search url runs users))
          users)))))

(defn search-users-by-location-lang
  [location lang]
  (search-users-by-location-lang-rich location lang 1))

(defn search-users-by-location-rich
  "1000 total results is the current user limit"
  [location more-repos-than]
  (loop [location location
         more-repos-than more-repos-than]
    (let [per-page 50
          more-repos-than more-repos-than
          encoded-location (->utf8 location)
          url (user-location-search-url per-page more-repos-than encoded-location)
          res (get-json url)
          total-user-count (:total_count res)
          runs (per-page->runs total-user-count per-page)
          users (:items res)]
      (if (> total-user-count 1000)
        (do (Thread/sleep (* 4 1000))
            (recur location (+ 1 more-repos-than)))
        (do (file-path-location-all location)
            (if (> runs 1)
              (do (prn "getting users with more than " more-repos-than " repos")
                  (recursive-user-search url runs users))
              users))))))

(defn search-users-by-location
  [location]
  (search-users-by-location-rich location 1))

(defn repo-slim
  [user-repos]
  (mapv #(select-keys % [:html_url :name :description :homepage :topics :language :updated_at]) user-repos))

(defn user-languages
  [user-repos]
  (->> (pmap #(:language %) user-repos)
       (remove nil?)
       (set)))

(defn user-with-clean-repos
  [user-repos]
  (let [first-repo (first user-repos)
        cleaned-repos (repo-slim user-repos)]
    {:name (get-in first-repo [:owner :login])
     :owner_url (get-in first-repo [:owner :html_url])
     :languages (user-languages cleaned-repos)
     :repositories cleaned-repos}))

(defn url-has-query?
  [url]
  (str/includes? url "?"))

(defn url-add-query-param
  [url k v]
  (let [sign (if (url-has-query? url) "&" "?")]
    (str url sign k "=" v)))

(defn recursive-curl
  [url]
  (loop [run 1
         results []]
    (let [page-url (url-add-query-param url "page" run)
          res (get-json page-url)]
      (if (= (count res) 0)
        results
        (recur (inc run) (concat results res))))))

(defn get-user-repos
  [user]
  (let [per-page 25
        {:keys [repos_url]} user
        url (-> repos_url
                (url-add-query-param  "sort" "updated")
                (url-add-query-param "per_page" per-page))]
    (if repos_url (recursive-curl url) nil)))

(defn get-users-repos
  [users]
  (pmap get-user-repos users))

(defn get-users-repos-without-forks
  [users]
  (pmap remove-forks (get-users-repos users)))

(defn pretty-spit
  [file content]
  (spit file (with-out-str (clojure.pprint/pprint content))))

(defn build-rich-user
  [user user-data]
  (assoc user
         :type (:type user-data)
         :location (:location user-data)
         :bio (:bio user-data)
         :email (:email user-data)
         :blog (:blog user-data)
         :twitter_username (:twitter_username user-data)
         :hireable (:hireable user-data)))

(defn enrich-user-data
  [file-path users]
  (pmap (fn [user]
          (let [user-url (str base-url "/users/" (:name user))
                user-data (get-json user-url)
                user-rich (build-rich-user user user-data)
                name (:name user-rich)
                file-name (str file-path name ".edn")]
            (fs/create-dirs file-path)
            (pretty-spit file-name user-rich)
            name))
        users))

(defn prepare-user-data
  [file-path users]
  (->> users
       (get-users-repos-without-forks)
       (pmap #(user-with-clean-repos %))
       (remove #(nil? (:name %)))
       (enrich-user-data file-path)))

(defn save-profiles-location
  "this will output the profiles matching into the `profiles` dir as formatted edn data"
  [location]
  (let [file-path (file-path-location-all location)
        users (search-users-by-location location)]
    (map #(prepare-user-data file-path %) (partition 100 100 nil users))))

(defn save-profiles-location-lang
  "this will output the profiles matching into the `profiles` dir as formatted edn data"
  [location lang]
  (let [file-path (file-path-location-lang location lang)
        users (search-users-by-location-lang location lang)]
    (map #(prepare-user-data file-path %) (partition 100 100 nil users))))

;; entrypoint
(let [search-term-location (first *command-line-args*)
      search-term-lang (second *command-line-args*)]
  (case (count *command-line-args*)
    1 (save-profiles-location search-term-location)
    2 (save-profiles-location-lang search-term-location search-term-lang)
    :done))
