FROM babashka/babashka:1.3.191

WORKDIR /app

ARG github_hire_token=none
ENV GITHUB_HIRE_TOKEN=${github_hire_token}

COPY bb.edn .
COPY git-hire.clj read-profile.clj Readme.md .tool-versions ./

CMD bash