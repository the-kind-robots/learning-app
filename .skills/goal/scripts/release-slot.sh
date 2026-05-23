#!/usr/bin/env bash
# Release a port slot when a worktree task finishes.
# Usage: release-slot.sh <slot-number>

set -euo pipefail

SLOT="${1:?slot number required}"
GIT_COMMON_DIR=$(git rev-parse --git-common-dir)
SLOTS_DIR="${GIT_COMMON_DIR}/worktree-slots"
LOCK_FILE="${GIT_COMMON_DIR}/worktree-slots.lock"

(
  flock 200
  rm -rf "${SLOTS_DIR}/${SLOT}"
) 200>"$LOCK_FILE"

echo "Released slot ${SLOT}."
