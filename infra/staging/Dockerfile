# Staging rung 2 (ADR-0010): the production shape in a systemd container.
#
# Base: ubuntu:24.04 — not pure Debian. Production is an Ubuntu Hetzner box
# (admin-setup.sh --bootstrap uses `add-apt-repository universe`), and noble is
# the release that carries openjdk-21 and has CouchDB packages in the Apache
# apt repository. This is the closest image to what the deb actually lands on.
FROM ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive container=docker

# A hung mirror connection must fail fast and retry, not stall the build.
RUN printf '%s\n' \
        'Acquire::Retries "3";' \
        'Acquire::http::Timeout "30";' \
        'Acquire::https::Timeout "30";' \
    > /etc/apt/apt.conf.d/80-staging-timeouts

# systemd as PID 1 plus tooling for the repo setup below. netcat-openbsd is for
# the smoke alone: it stands in for an upstream so an assertion can read the
# request nginx actually forwards. It never enters the production deb.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        systemd systemd-sysv dbus \
        curl ca-certificates gnupg apt-transport-https openssl \
        netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# CouchDB apt repository — the same source admin-setup.sh --bootstrap adds.
RUN curl -fsSL https://couchdb.apache.org/repo/keys.asc \
        | gpg --dearmor -o /usr/share/keyrings/couchdb-archive-keyring.gpg \
    && . /etc/os-release \
    && echo "deb [signed-by=/usr/share/keyrings/couchdb-archive-keyring.gpg] https://apache.jfrog.io/artifactory/couchdb-deb/ ${VERSION_CODENAME} main" \
        > /etc/apt/sources.list.d/couchdb.list

# Preseed CouchDB: single node on localhost with a known staging admin
# password (plaintext by design — this container never holds real secrets).
# On the real server the operator answers these prompts during bootstrap.
# The password is the one the app itself carries (lib/db/src/db.cljc `conn`,
# hardcoded): with any other value the app cannot create userdbs or run the
# boot reconciliation report, and the restore drill cannot seed through the
# real provisioning endpoint. Must match COUCH_PASS in run.sh.
RUN printf '%s\n' \
        'couchdb couchdb/mode select standalone' \
        'couchdb couchdb/bindaddress string 127.0.0.1' \
        'couchdb couchdb/cookie string learning-app-staging' \
        'couchdb couchdb/adminpass password 3434' \
        'couchdb couchdb/adminpass_again password 3434' \
    | debconf-set-selections

# Everything the infra deb Depends on, baked at image build so installing the
# deb inside the booted container needs no network.
RUN apt-get update \
    && apt-get install -y \
        nginx borgbackup certbot python3-certbot-nginx couchdb \
        openjdk-21-jre-headless sqlite3 \
    && rm -rf /var/lib/apt/lists/*

# Self-signed certificate where postinst expects the real one; without it
# `nginx -t` fails the moment tmpfiles links the site in. Certbot stays
# dry below production (ADR-0010).
RUN mkdir -p /etc/nginx/ssl/sprecha.de \
    && openssl req -x509 -newkey rsa:2048 -nodes -days 30 \
        -subj "/CN=sprecha.de" \
        -addext "subjectAltName=DNS:sprecha.de,DNS:www.sprecha.de" \
        -keyout /etc/nginx/ssl/sprecha.de/privkey.pem \
        -out /etc/nginx/ssl/sprecha.de/fullchain.pem \
    && chmod 700 /etc/nginx/ssl/sprecha.de

# Units that cannot work under docker (no udev, no kernel mounts) or that
# docker already covers (resolv.conf is bind-mounted by the daemon).
RUN systemctl mask \
        systemd-resolved.service \
        systemd-udevd.service \
        systemd-udevd-kernel.socket \
        systemd-udevd-control.socket \
        systemd-modules-load.service \
        sys-kernel-config.mount \
        sys-kernel-debug.mount

STOPSIGNAL SIGRTMIN+3
CMD ["/lib/systemd/systemd"]
