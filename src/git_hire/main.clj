(ns git-hire.main
  (:require [clojure.string :as str]
            [babashka.http-client :as http]
            [cheshire.core :as json]
            [babashka.fs :as fs]))

(import [java.net URLEncoder])

(defn pretty-spit
  [file content]
  (prn file)
  (prn content)
  (spit file (with-out-str (clojure.pprint/pprint content))))

(def auth
  "Sets up the headers for requests
   depending on a set ENV"
  (let [token (System/getenv "GITHUB_HIRE_TOKEN")
        bearer (str "Bearer " token)]
    {:headers {"Accept" "application/vnd.github.text-match+json"
               "X-GitHub-Api-Version" "2022-11-28"
               "Authorization" bearer}}))

(def base-url
  "https://api.github.com")

(def user-search-path
  "/search/users")

(def default-sleep-time "30")

(def sleep-time
  (let [sleep-time (or
                    (System/getenv "SLEEP_TIME_SECONDS")
                    default-sleep-time)]
    (* (Integer/parseInt sleep-time) 1000)))

(defn ->utf8
  [s]
  (URLEncoder/encode s "UTF-8"))

(defn get-json-with-params
  "Searches for users in a given location"
  [path query-params]
  (let [url (str base-url path)
        request-spec (merge auth query-params)]
    (-> (http/get url request-spec)
        :body
        (json/parse-string true))))

(defn sanitize-user-input
  "Sanitizes user input"
  [user-input]
  (-> user-input
      str/trim
      str/lower-case
      ->utf8))

(defn add-outer-quotes
  [s]
  (str "\"" s "\""))

(defn user-location-search-params-location
  "hammers out the query params for user search location
   the location needs to be wrapped in quotes"
  [per-page more-repos-than location]
  (let [location-str (add-outer-quotes (sanitize-user-input location))]
    {:query-params {"per_page" per-page
                    "q" (str "location:" location-str
                             " "
                             "repos:" ">=" more-repos-than)}}))

(defn user-location-search-params-location-lang
  "hammers out the query params for user search location
   the location needs to be wrapped in quotes"
  [per-page more-repos-than location lang]
  (let [location-str (add-outer-quotes (sanitize-user-input location))
        lang-str (add-outer-quotes (sanitize-user-input lang))]
    {:query-params {"per_page" per-page
                    "q" (str "location:" location-str
                             " "
                             "repos:" ">=" more-repos-than
                             " "
                             "language:" lang-str)}}))

(def file-path-profiles
  (str/lower-case "./profiles/"))

(defn file-path-location
  [location]
  (str file-path-profiles (str/lower-case location)))

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
  [query-params runs existing-results]
  (loop [run 2
         results existing-results]
    (if (> run runs)
      results
      (let [res (get-json-with-params user-search-path query-params)
            new-results (:items res)]
        (recur (inc run) (concat results new-results))))))

(defn per-page->runs
  [total divisor]
  (int (Math/ceil (double (/ total divisor)))))

(defn search-users-by-location-lang-rich
  "1000 total results is the current user limit"
  [location lang more-repos-than]
  (loop
   [location location
    lang lang
    more-repos-than more-repos-than]
    (let [per-page 50
          q (user-location-search-params-location-lang per-page more-repos-than location lang)
          res (get-json-with-params user-search-path q)
          total-user-count (:total_count res)
          runs (per-page->runs total-user-count per-page)
          users (:items res)]
      (if (> total-user-count 1000)
        (do (Thread/sleep (* sleep-time 1000))
            (recur location lang (+ 1 more-repos-than)))
        (if (> runs 1)
          (do (prn "getting users with more than " more-repos-than " repos")
              (recursive-user-search q runs users))
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
          q (user-location-search-params-location per-page more-repos-than location)
          res (get-json-with-params user-search-path q)
          total-user-count (:total_count res)
          runs (per-page->runs total-user-count per-page)
          users (:items res)]
      (if (> total-user-count 1000)
        (do (Thread/sleep (* sleep-time 1000))
            (recur location (+ 1 more-repos-than)))
        (do (file-path-location-all location)
            (if (> runs 1)
              (do (prn "getting users with more than " more-repos-than " repos")
                  (recursive-user-search q runs users))
              users))))))

(defn search-users-by-location
  [location]
  (search-users-by-location-rich location 1))

(defn repo-slim
  [user-repo]
  (select-keys user-repo [:html_url :name :description :homepage :topics :language :stargazers_count :updated_at]))

(defn repos-slim
  [user-repos]
  (mapv repo-slim user-repos))

(defn user-languages
  [user-repos]
  (->> (pmap #(:language %) user-repos)
       (remove nil?)
       (set)))

(defn user-with-clean-repos
  [user-repos]
  (let [first-repo (first user-repos)
        cleaned-repos (repos-slim user-repos)]
    {:name (get-in first-repo [:owner :login])
     :owner_url (get-in first-repo [:owner :html_url])
     :languages (user-languages cleaned-repos)
     :total-stars (reduce + (map :stargazers_count cleaned-repos))
     :repositories cleaned-repos}))

(defn recursive-curl
  [url]
  (let [path (last (str/split url #"api.github.com"))
        per-page 25]
    (loop [run 1
           results []]
      (let [res (get-json-with-params path
                                      {:query-params {"page" run
                                                      "per_page" per-page
                                                      "sort" "updated"
                                                      "direction" "desc"}})]
        (if (zero? (count res))
          results
          (recur (inc run) (concat results res)))))))

(defn get-user-repos
  [user]
  (let [{:keys [repos_url]} user
        url repos_url]
    (if url
      (recursive-curl url)
      nil)))

(defn get-users-repos
  [users]
  (map get-user-repos users))

(defn get-users-repos-without-forks
  [users]
  (map remove-forks (get-users-repos users)))

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
  (mapv (fn [user]
          (let [user-data (get-json-with-params (str "/users/" (:name user)) {})
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
       (mapv #(user-with-clean-repos %))
       (remove #(nil? (:name %)))
       (enrich-user-data file-path)))

(defn print-profile-count-for-location
  [profiles location]
  (let [count (reduce + (map count profiles))]
    (prn (str "Found " count " users in " location))))

(defn save-profiles-location
  "this will output the profiles matching into the `profiles` dir as formatted edn data"
  [location]
  (let [file-path (file-path-location-all location)
        users (search-users-by-location location)
        res (map #(prepare-user-data file-path %) (partition 10 10 nil users))]
    (print-profile-count-for-location res location)
    res))

(defn save-profiles-location-lang
  "this will output the profiles matching into the `profiles` dir as formatted edn data"
  [location lang]
  (let [file-path (file-path-location-lang location lang)
        users (search-users-by-location-lang location lang)]
    (mapv #(prepare-user-data file-path %) (partition 100 100 nil users))))

(defn -main
  [& args]
  (let [search-term-location (first args)
        search-term-lang (second args)]
    (case (count args)
      1 (save-profiles-location search-term-location)
      2 (save-profiles-location-lang search-term-location search-term-lang)
      :done)))