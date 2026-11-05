#!/usr/bin/env bash
# Put a pushed branch on the dev stand: detach the main checkout onto it and
# wait for the watch to rebuild. Run by learning-app-stand@<branch>.service, so
# a background session, which may not touch the main checkout itself, can do
# it when the owner asks.
#
# Installed outside the repository on purpose: a branch older than this script
# does not carry it, and the next switch would find nothing to run.
#
# Usage: switch-stand.sh <branch>    (working directory: the main checkout)
set -euo pipefail

branch="${1:?usage: switch-stand.sh <branch>}"

if [ -n "$(git status --porcelain --untracked-files=no)" ]; then
    echo "the main checkout has uncommitted changes; the stand stays where it is" >&2
    git status --short --untracked-files=no >&2
    exit 1
fi

git fetch --quiet origin "${branch}"

before="$(git rev-parse HEAD)"
started="$(date +%s)"
git switch --quiet --detach FETCH_HEAD
echo "stand: ${branch} at $(git rev-parse --short HEAD)"

if ! git diff --quiet "${before}" HEAD -- src/backend src/shared resources dev deps.edn; then
    echo "server code changed; restarting the backend"
    systemctl --user restart learning-app-dev-backend.service
fi

# The watch rebuilds on its own, and only when its sources changed; the page
# is worth loading only after it has.
if git diff --quiet "${before}" HEAD -- lib/db/src src/shared src/client dev/cljs dev/clj test shadow-cljs.edn; then
    exit 0
fi

for _ in $(seq 180); do
    result="$(journalctl --user -u learning-app-dev-watch.service --since "@${started}" -o cat --no-pager \
        | grep -E -m1 'Build (completed|failure)' || true)"
    case "${result}" in
        *completed*) echo "${result}"; exit 0 ;;
        *failure*)   echo "${result}" >&2; exit 1 ;;
    esac
    sleep 1
done

echo "the watch did not report a build within 3 minutes" >&2
exit 1
