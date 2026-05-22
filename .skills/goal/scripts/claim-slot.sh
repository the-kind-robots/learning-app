#!/usr/bin/env bash
# Atomically claim a port slot for a git worktree.
# Usage: claim-slot.sh [worktree-path]
# Outputs one line: the slot number (integer ≥ 1).
# Slot 0 is the main repo — never claimed here.
# Ports per slot N: backend=8083+N*100, nrepl=4444+N*100, shadow=9630+N*100

set -euo pipefail

WORKTREE_PATH="${1:-$(pwd)}"
REPO_ROOT=$(git -C "$WORKTREE_PATH" rev-parse --show-toplevel 2>/dev/null || git rev-parse --show-toplevel)
SLOTS_DIR="${REPO_ROOT}/.git/worktree-slots"
LOCK_FILE="${REPO_ROOT}/.git/worktree-slots.lock"

mkdir -p "$SLOTS_DIR"

_is_worktree_live() {
  git -C "$REPO_ROOT" worktree list --porcelain 2>/dev/null | grep -q "^worktree ${1}$"
}

(
  flock 200

  # Return existing slot if this worktree already claimed one.
  for slot_dir in "$SLOTS_DIR"/*/; do
    [[ -d "$slot_dir" ]] || continue
    wt_file="${slot_dir}worktree"
    [[ -f "$wt_file" ]] || continue
    claimed_wt=$(cat "$wt_file")
    if [[ "$claimed_wt" == "$WORKTREE_PATH" ]]; then
      echo "$(basename "$slot_dir")"
      exit 0
    fi
  done

  # Remove stale slots (worktree no longer registered in git).
  for slot_dir in "$SLOTS_DIR"/*/; do
    [[ -d "$slot_dir" ]] || continue
    wt_file="${slot_dir}worktree"
    [[ -f "$wt_file" ]] || continue
    wt=$(cat "$wt_file")
    if ! _is_worktree_live "$wt"; then
      rm -rf "$slot_dir"
    fi
  done

  # Find the lowest free slot ≥ 1.
  slot=1
  while [[ -d "${SLOTS_DIR}/${slot}" ]]; do
    slot=$((slot + 1))
  done

  mkdir -p "${SLOTS_DIR}/${slot}"
  echo "$WORKTREE_PATH" > "${SLOTS_DIR}/${slot}/worktree"

  echo "$slot"
) 200>"$LOCK_FILE"
