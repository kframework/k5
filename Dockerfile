FROM ubuntu:bionic

ARG USER_ID=1000
ARG GROUP_ID=1000

RUN    apt-get update        \
    && apt-get install --yes \
        bison                \
        clang-8              \
        cmake                \
        curl                 \
        debhelper            \
        docker.io            \
        flex                 \
        gcc                  \
        git                  \
        libboost-test-dev    \
        libgmp-dev           \
        libjemalloc-dev      \
        libmpfr-dev          \
        libyaml-dev          \
        libz3-dev            \
        lld-8                \
        llvm-8-tools         \
        maven                \
        sudo                 \
        opam                 \
        openjdk-11-jdk       \
        pkg-config           \
        python3              \
        python3-graphviz     \
        z3                   \
        zlib1g-dev

RUN curl -sSL https://get.haskellstack.org/ | sh

RUN echo '%sudo ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers

RUN    groupadd -g $GROUP_ID user                             \
    && useradd -m -u $USER_ID -s /bin/sh -g user -G sudo user

USER user:user

RUN mkdir -p /home/user/.ssh
ADD --chown=user:user package/ssh/config /home/user/.ssh/
RUN    chmod go-rwx -R /home/user/.ssh                                \
    && git config --global user.email "admin@runtimeverification.com" \
    && git config --global user.name  "RV Jenkins"

RUN curl -L https://github.com/github/hub/releases/download/v2.14.0/hub-linux-amd64-2.14.0.tgz -o /home/user/hub.tgz
RUN cd /home/user && tar xzf hub.tgz

ENV LD_LIBRARY_PATH=/usr/local/lib
ENV PATH=/home/user/hub-linux-amd64-2.14.0/bin:$PATH

ADD k-distribution/src/main/scripts/bin/k-configure-opam-dev k-distribution/src/main/scripts/bin/k-configure-opam-common /home/user/.tmp-opam/bin/
ADD k-distribution/src/main/scripts/lib/opam                                                                             /home/user/.tmp-opam/lib/opam/
RUN    cd /home/user                        \
    && ./.tmp-opam/bin/k-configure-opam-dev

ENV LC_ALL=C.UTF-8
ADD --chown=user:user haskell-backend/src/main/native/haskell-backend/stack.yaml        /home/user/.tmp-haskell/
ADD --chown=user:user haskell-backend/src/main/native/haskell-backend/kore/package.yaml /home/user/.tmp-haskell/kore/
RUN    cd /home/user/.tmp-haskell  \
    && stack build --only-snapshot

ADD pom.xml                                                    /home/user/.tmp-maven/
ADD ktree/pom.xml                                              /home/user/.tmp-maven/ktree/
ADD llvm-backend/pom.xml                                       /home/user/.tmp-maven/llvm-backend/
ADD llvm-backend/src/main/native/llvm-backend/matching/pom.xml /home/user/.tmp-maven/llvm-backend/src/main/native/llvm-backend/matching/
ADD haskell-backend/pom.xml                                    /home/user/.tmp-maven/haskell-backend/
ADD ocaml-backend/pom.xml                                      /home/user/.tmp-maven/ocaml-backend/
ADD kernel/pom.xml                                             /home/user/.tmp-maven/kernel/
ADD java-backend/pom.xml                                       /home/user/.tmp-maven/java-backend/
ADD k-distribution/pom.xml                                     /home/user/.tmp-maven/k-distribution/
ADD kore/pom.xml                                               /home/user/.tmp-maven/kore/
RUN    cd /home/user/.tmp-maven               \
    && mvn --batch-mode dependency:go-offline
