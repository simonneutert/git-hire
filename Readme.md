# git hire! hire on 🔥!<!-- omit from toc -->

Ever wondered who is coding what in your city and how to keep track of it, maybe `grep` projects by keyword?

This is your tool! (babashka >= 1.0.171 mandatory)

## What is .edn? <!-- omit from toc -->

EDN is a data format, like JSON, but more like Clojure's native data format.

A result can look like this:

```clojure
{:name "Simon Neutert"
 :hireable true
 :languages ["HTML"]
 :bio "I'm an HTML hacker."
 :location "Area 50++"
 :public-repos 123
 :repos-url "https://api.github.com/users/simonneutert/repos"
 :type "User"}
```

### Need JSON? <!-- omit from toc -->

I can highly suggest [jet](https://github.com/borkdude/jet) for that.

---

![this is fine dog](https://i.kym-cdn.com/entries/icons/mobile/000/018/012/this_is_fine.jpg)  
https://knowyourmeme.com/memes/this-is-fine

---

- [Features](#features)
- [planned features](#planned-features)
- [Prerequisities](#prerequisities)
- [Run](#run)
  - [Configuration](#configuration)
    - [Sleep time](#sleep-time)
  - [Run in Docker](#run-in-docker)
  - [Run locally](#run-locally)
    - [examples](#examples)
  - [Search in result files (saved profiles)](#search-in-result-files-saved-profiles)
    - [examples](#examples-1)
  - [Inspect Profiles (with examples! 🤯)](#inspect-profiles-with-examples-)
  - [Find juniors/new-joiners](#find-juniorsnew-joiners)
- [FAQ](#faq)
  - [Errors](#errors)
  - [CookBook Babashka](#cookbook-babashka)
  - [How to Clojure in VS Code](#how-to-clojure-in-vs-code)
  - ["github-username.edn" what am I supposed to do with that? JSON would be much nicer!](#github-usernameedn-what-am-i-supposed-to-do-with-that-json-would-be-much-nicer)
    - [transform to JSON](#transform-to-json)


## Features

- [x] up to 1000 users per city + language combination (sorted by "users' public repositories count")
- [x] if less than 1000 users in a city total, you can download by location only
- [x] concurrency built-in 🚀

## planned features

- [ ] get all users (not just 1000)
  - implement automatic bucketing, sliding through the limits
  - PROBLEM: GitHub sets the limit here 🥴
- [ ] tests?! 🧌
- [ ] sort by active last week? OR created in year?
- [x] speed isn't crucial, but utilizing some of `clojure.core.async` magic could speed things up 10x maybe :thinking: `pmap` ftw 🎉

## Prerequisities

- [babashka](https://www.babashka.org) **latest supported version for this code is currently 1.0.171**
- GitHub API Token ([Personal Access Tokens](https://docs.github.com/en/rest/guides/getting-started-with-the-rest-api#using-personal-access-tokens))
- Java doesn't hurt, too

make sure your ENV has the `GITHUB_HIRE_TOKEN` at hand.  
I do it like this:  
in a terminal enter `$ export GITHUB_HIRE_TOKEN="<my-token-here>"`  
then, from that terminal open your IDE of choice, like  
`$ code .`

or have it in your `.zshrc` 🤗 or whatever your shell loads at start

🥳 happy times in the REPL

## Run

Here's what you need to get the thing running.

- [babashka](https://www.babashka.org) or Docker/Podman
- Project Configuration (optional)

### Configuration

Currently, the only configuration you can do is setting sleep time between request cycles.

#### Sleep time

**DEFAULT** sleep time is 30 seconds.

Increase the sleep time to avoid hitting the GitHub API rate limit.

You can customise the sleep time between cycles by setting the `SLEEP_TIME_SECONDS` environment variable.

```bash
$ SLEEP_TIME_SECONDS=15 bb scrape <location-like-city-or-country> <language>
```

### Run in Docker

All of the following should work in Docker, too.

The simplest way for you is to use the given Dockerfile.

```bash
$ docker build --build-arg github_hire_token=${GITHUB_HIRE_TOKEN} -t git-hire .
$ docker run -it --rm git-hire
```

If you need to store the profiles, you can mount a docker volume, but this goes beyond the scope of this README.

### Run locally

```bash
$ bb scrape <location-like-city-or-country>
```

Will save the github profiles as `.edn` into the `profiles` directory,  
**but** as GitHub support let me know:  
> When using the language qualifier when searching for users, it will only return users where the majority of their repositories use the specified language. (please, see [documentation](https://docs.github.com/en/search-github/searching-on-github/searching-users#search-by-repository-language))

Specify further adding a language:

```bash
$ bb scrape <location-like-city-or-country> <language>
```

**Be warned!** This might not find a PHP dev who switched to Rust recently, as described by GitHub's Support.

Or if the city is too crowded, try loading mainstream languages for a given city.  
**Watch your rate limits ⚠️**

After having built a pool of profiles, use  
`$ bb search-keyword "rust"` and/or see examples given below.

#### examples

`$ bb scrape mainz`  
`$ bb scrape "Bad Kreuznach"`  
`$ bb scrape wiesbaden java`  
`$ bb scrape wiesbaden php`  
`$ bb scrape mainz javascript`

### Search in result files (saved profiles)

`$ bb search-keyword <search term skill framework else>`

#### examples

`$ bb search-keyword android`  
`$ bb search-keyword "ruby on rails"`  
`$ bb search-keyword nuxt`

you might go further, by piping to bb again, unimaginable possibilities...

```bash
$ mkdir rails; cp $(grep -Zril rails profiles) rails
```

and then:

```bash
$ bb search-keyword "ios" | bb -e '(map #(str/upper-case %) *input*)'
```

### Inspect Profiles (with examples! 🤯)

```bash
$ bb read-profile.clj simonneutert
```

go further, by piping:

```bash
$ bb read-profile.clj simonneutert | bb -e '(:languages *input*)'
```

then read many profiles

```bash
$ bb search-keyword ruby | bb -e '(mapv #(edn/read-string (slurp %)) *input*)'
```

map out `name` and `bio`, where `bio` is provided

```
$ bb search-keyword ruby |\
    bb -e '(mapv #(edn/read-string (slurp %)) *input*)' |\
    bb -e '(mapv #(select-keys % [:name :bio]) *input*)' |\
    bb -e '(remove #(nil? (:bio %)) *input*)'
```

map out `name` and `bio`, where `bio` is provided, filter by bio containing "apple"


```bash
$ bb search-keyword ruby |\
    bb -e '(mapv #(edn/read-string (slurp %)) *input*)' |\
    bb -e '(mapv #(select-keys % [:name :bio]) *input*)' |\
    bb -e '(remove #(nil? (:bio %)) *input*)' |\
    bb -e '(filter #(clojure.string/includes? (clojure.string/lower-case (:bio %)) "apple") *input*)' |\
    bb -e '(clojure.pprint/pprint *input*)'
```

what you came here for 🔥 find all hireable

*search-keyword git* is sort of a hack returning all profiles you downloaded at this point

```bash
$ bb search-keyword git |\
    bb -e '(mapv #(edn/read-string (slurp %)) *input*)' |\
    bb -e '(remove #(nil? (:hireable %)) *input*)'
```

### Find juniors/new-joiners

```bash
# using httpie
GITHUB_HIRE_SINCE_YEAR=2019;
GITHUB_HIRE_LOCATION=wiesbaden;
https -A bearer -a ${GITHUB_HIRE_TOKEN} \
  "https://api.github.com/search/users?q=created%3A%3E${GITHUB_HIRE_SINCE_YEAR}-01-01+location%3A${GITHUB_HIRE_LOCATION}+repos%3A%3E1&type=Users" \
  "Accept":"application/vnd.github.v3+json"
```

```bash
# using httpie and jq
GITHUB_HIRE_SINCE_YEAR=2019;
GITHUB_HIRE_LOCATION=wiesbaden;
https -A bearer -a ${GITHUB_HIRE_TOKEN} \
  "https://api.github.com/search/users?q=created%3A%3E${GITHUB_HIRE_SINCE_YEAR}-01-01+location%3A${GITHUB_HIRE_LOCATION}+repos%3A%3E1&type=Users" \
  "Accept":"application/vnd.github.v3+json" |\
  jq '.items | map(select(.type == "User")) | .[] |.repos_url'
```


## FAQ

Some stuff you would want to know/read as a beginner.

### Errors

- REPL fails and outputs  
  `; : Can't set!: *current-length* from non-binding thread user `

`pmap` and `curl` don't play well with each other in the shell (I guess).  
Don't worry, run the tool from the shell:  
`bb scrape berlin ruby`  
it will fire up some threads 🔥

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
