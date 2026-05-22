#!/usr/bin/env bash
# Release a port slot when a worktree task finishes.
# Usage: release-slot.sh <slot-number>

set -euo pipefail

SLOT="${1:?slot number required}"
REPO_ROOT=$(git rev-parse --show-toplevel)
SLOTS_DIR="${REPO_ROOT}/.git/worktree-slots"
LOCK_FILE="${REPO_ROOT}/.git/worktree-slots.lock"

(
  flock 200
  rm -rf "${SLOTS_DIR}/${SLOT}"
) 200>"$LOCK_FILE"

echo "Released slot ${SLOT}."
