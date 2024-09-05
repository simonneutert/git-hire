FROM babashka/babashka:1.3.191

ENV WORKDIR /app
WORKDIR ${WORKDIR}

ARG github_hire_token=none
ENV GITHUB_HIRE_TOKEN=${github_hire_token}

COPY .tool-versions bb.edn read-profile.clj test-runner.clj ${WORKDIR}/
COPY src src
COPY test test

CMD bash