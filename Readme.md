# git hire! hire on 🔥!

ever wondered who is coding what in your city and how to keep track of it, maybe `grep` projects by keyword?

this is your tool!

![this is fine dog](https://i.kym-cdn.com/entries/icons/mobile/000/018/012/this_is_fine.jpg)  
https://knowyourmeme.com/memes/this-is-fine

## Features

- [x] up to 1000 users per city + language combination (sorted by most repositories)
- [x] if less than 1000 users in a city total, you can download by location only
- [x] concurrency built-in 🚀

## planned features

- [ ] get up to 3000 users (not just 1000) 
  - implement automatic bucketing, sliding through the limits
- [ ] tests?! 🧌

## Prerequisities

- [babashka](https://www.babashka.org)
- GitHub API Token ([Personal Access Tokens](https://docs.github.com/en/rest/guides/getting-started-with-the-rest-api#using-personal-access-tokens))
- Java doesn't hurt, too

make sure your ENV has the `GITHUB_TOKEN` at hand.  
I do it like this:  
in a terminal I `$ export GITHUB_TOKEN="<my-token-here>"`  
then, from that terminal open your IDE of choice, like  
`$ code .`

or have in your `.zshrc` 🤗 or whatever your shell loads at start

🥳 happy times in the REPL

## Run

### Download profiles

`$ bb git-hire.clj <location-like-city-or-country>`

Will save the github profiles as `.edn` into the `profiles` directory.

Or specify further adding a language, **but** as GitHub support let me know:

> When using the language qualifier when searching for users, it will only return users where the majority of their repositories use the specified language. (please, see [documentation](https://docs.github.com/en/search-github/searching-on-github/searching-users#search-by-repository-language))

So this might not find a PHP dev who switched to Rust recently!  
Better search by location(s) and then use `$ bb search-keyword "rust"`  
Search multiple languages in a given location.

`$ bb git-hire.clj <location-like-city-or-country>`

#### examples

`$ bb git-hire.clj mainz`  
`$ bb git-hire.clj "Bad Schwalbach"`

### Search in result files (saved profiles)

`$ bb search-keyword <search term skill framework else>`

#### examples

`$ bb search-keyword android`  
`$ bb search-keyword "ruby on rails"`  
`$ bb search-keyword nuxt`

you might go further, by piping to bb again, unimaginable possibilities...

`$ bb search-keyword "ios" | bb -e '(map #(str/upper-case %) *input*)'`

### Inspect Profiles (with examples! 🤯)

`$ bb read-profile.clj simonneutert`

go further, by piping

`$ bb read-profile.clj simonneutert | bb -e '(:languages *input*)'`

read many profiles

```bash
$ bb search-keyword ruby | bb -e '(mapv #(edn/read-string (slurp %)) *input*)'
```

map out `name` and `bio` and filter

```bash
$ bb search-keyword ruby |\
    bb -e '(mapv #(edn/read-string (slurp %)) *input*)' |\
    bb -e '(mapv #(select-keys % [:name :bio]) *input*)' |\
    bb -e '(remove #(nil? (:bio %)) *input*)' |\
    bb -e '(filter #(clojure.string/includes? (clojure.string/lower-case (:bio %)) "apple") *input*)' |\
    bb -e '(clojure.pprint/pprint *input*)'
```

## FAQ

Some stuff you would want to know/read as a beginner.

### Errors

- REPL fails and outputs `; : Can't set!: *current-length* from non-binding thread user `

`pmap` and `curl` don't play well with each other, don't worry, run the tool from the shell:  
`bb git-hire.clj berlin ruby`  and off you go

### CookBook Babashka

https://book.babashka.org/

### How to Clojure in VS Code

https://clojure.org/guides/editors#_vs_code_rapidly_evolving_beginner_friendly

### "github-username.edn" what am I supposed to do with that? JSON would be much nicer!

CLI to transform between JSON, EDN and Transit, powered with a minimal query language.

https://github.com/borkdude/jet

#### transform to JSON

```bash 
$ bb search-keyword ruby |\
    bb -e '(mapv #(edn/read-string (slurp %)) *input*)' |\
    jet --to json
```
