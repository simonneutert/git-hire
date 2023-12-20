(ns git-hire.test-main)
(require '[clojure.test :as t]
         '[babashka.classpath :as cp]
         '[git-hire.main :as main])

(t/deftest test-definitions
  (t/is (= main/base-url
           "https://api.github.com"))
  (t/is (= main/user-search-path
           "/search/users"))
  (t/is (= true
           (map? main/auth)))
  (t/is (= true
           (map? (:headers main/auth))))
  (t/is (= ["Accept" "Authorization"]
           (keys (:headers main/auth)))))

(t/deftest utf8-conversion
  (t/is (= "foo+bar"
           (main/->utf8 "foo bar"))))

(t/deftest add-outer-quotes
  (t/is (= "\"foo\""
           (main/add-outer-quotes "foo"))))

(t/deftest sanitize-user-input
  (t/is (= "foo+bar"
           (main/sanitize-user-input "  Foo Bar "))))

(t/deftest per-page->runs
  (t/is (= 1
           (main/per-page->runs 10 10)))
  (t/is (= 2
           (main/per-page->runs 10 5)))
  (t/is (= 4
           (main/per-page->runs 11 3)))
  (t/is (= 5
           (main/per-page->runs 101 25))))

(t/deftest repos-slim
  "This test checks if the function repos-slim
   returns a vector of maps with the correct keys"
  (t/is (= [{:html_url "bar"
             :name "foo"
             :description "baz"
             :homepage "www.foo.bar"
             :topics ["foo" "bar"]
             :language "clojure"
             :updated_at "2020-01-01T00:00:00Z"}]
           (main/repos-slim [{:name "foo"
                              :html_url "bar"
                              :description "baz"
                              :homepage "www.foo.bar"
                              :topics ["foo" "bar"]
                              :language "clojure"
                              :updated_at "2020-01-01T00:00:00Z"
                              :stargazers_count 10
                              :forks_count 5
                              :open_issues_count 2
                              :license "MIT"}]))))

(t/deftest repo-slim
  "This test checks if the function repo-slim
   returns a map with the whitelisted/pre-defined keys"
  (t/is (= {:html_url "bar"
            :name "foo"
            :description "baz"
            :homepage "www.foo.bar"
            :topics ["foo" "bar"]
            :language "clojure"
            :updated_at "2020-01-01T00:00:00Z"}
           (main/repo-slim {:name "foo"
                            :html_url "bar"
                            :description "baz"
                            :homepage "www.foo.bar"
                            :topics ["foo" "bar"]
                            :language "clojure"
                            :updated_at "2020-01-01T00:00:00Z"
                            :stargazers_count 10
                            :forks_count 5
                            :open_issues_count 2
                            :license "MIT"}))))

(t/deftest user-languages
  "This test checks if the function user-languages
   returns a vector of maps with the users used
   languages as a set of strings.
   Empty languages are removed."
  (t/is (= #{"clojure" "ruby"}
           (main/user-languages [{:name "foo"
                                  :html_url "bar"
                                  :language "clojure"
                                  :open_issues_count 2
                                  :license "MIT"}
                                 {:name "foo"
                                  :html_url "bar"
                                  :language "ruby"
                                  :open_issues_count 2
                                  :license "MIT"}
                                 {:name "foo"
                                  :pizza "turtles"}])))
  (t/is (= true
           (set? (main/user-languages
                  [{:name "foo"
                    :html_url "bar"
                    :language "clojure"
                    :open_issues_count 2
                    :license "MIT"}
                   {:name "foo"
                    :html_url "bar"
                    :language "ruby"
                    :open_issues_count 2
                    :license "MIT"}
                   {:name "foo"
                    :pizza "turtles"}])))))

(t/deftest user-location-search-params-location
  (t/is (= {:query-params {"per_page" 10, "q" "location:\"bad+kissingen\"+repos:>=0"}}
           (main/user-location-search-params-location 10 0 "Bad Kissingen")))
  (t/is (= {:query-params {"per_page" 20, "q" "location:\"mainz\"+repos:>=0"}}
           (main/user-location-search-params-location 20 0 "Mainz"))))

(t/deftest file-path-location-all
  (t/is (= "./profiles/mainz/all/"
           (main/file-path-location-all "Mainz")))
  (t/is (= "./profiles/bad kissingen/all/"
           (main/file-path-location-all "Bad Kissingen"))))

(t/deftest user-location-search-params-location-lang
  (t/is (= {:query-params {"per_page" 10,
                           "q" "location:\"bad+kissingen\"+repos:>=0+language:\"clojure\""}}
           (main/user-location-search-params-location-lang 10 0 "Bad Kissingen" "clojure"))))