#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: prune_worktrees.sh [--base <branch>] [--dry-run]

Remove worktrees under <repo>/.claude/worktrees/ whose work is already in the base:
branch has a merged PR or is contained in origin/<base>, or HEAD is detached at a
commit contained in origin/<base>. Also deletes that local branch.
Kept: locked, current, tracked changes, open PR, unmerged commits.
Untracked files (node_modules, build output) do not block removal.

Options:
  --base <branch>   Default: GHWF_DEFAULT_BASE or master
  --dry-run         Report only
USAGE
}

base="${GHWF_DEFAULT_BASE:-master}"
dry=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --base) base="$2"; shift 2 ;;
    --dry-run) dry=1; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
  esac
done

common="$(git rev-parse --path-format=absolute --git-common-dir)"
main="$(dirname "$common")"
here="$(git rev-parse --show-toplevel)"
root="$main/.claude/worktrees/"

git -C "$main" fetch --quiet origin "$base" || echo "warn: fetch origin $base failed; using local refs" >&2
base_ref="origin/$base"

say() { printf '%s %s (%s)\n' "$1" "$2" "$3"; }

remove() { # path branch reason
  say "$([[ $dry -eq 1 ]] && echo WOULD-REMOVE || echo REMOVE)" "$1" "$3"
  [[ $dry -eq 1 ]] && return 0
  git -C "$main" worktree remove --force "$1"
  if [[ -n "$2" ]]; then git -C "$main" branch -D "$2" >/dev/null; fi
}

consider() { # path branch(or empty) locked
  local path="$1" branch="$2" locked="$3" head open merged
  [[ "$path" == "$root"* ]] || return 0
  if [[ -n "$locked" ]]; then say KEEP "$path" "locked"; return 0; fi
  if [[ "$path" == "$here" ]]; then say KEEP "$path" "current worktree"; return 0; fi
  if [[ -n "$(git -C "$path" status --porcelain --untracked-files=no)" ]]; then
    say KEEP "$path" "tracked changes"; return 0
  fi
  head="$(git -C "$path" rev-parse HEAD)"
  if [[ -z "$branch" ]]; then
    if git -C "$main" merge-base --is-ancestor "$head" "$base_ref"; then
      remove "$path" "" "detached, contained in $base_ref"
    else
      say KEEP "$path" "detached, commits not in $base_ref"
    fi
    return 0
  fi
  open="$(gh pr list --head "$branch" --state open --json number --jq 'length')"
  if [[ "$open" != "0" ]]; then say KEEP "$path" "$branch: open PR"; return 0; fi
  merged="$(gh pr list --head "$branch" --state merged --json number --jq 'length')"
  if [[ "$merged" != "0" ]]; then
    remove "$path" "$branch" "$branch: PR merged"
  elif git -C "$main" merge-base --is-ancestor "$head" "$base_ref"; then
    remove "$path" "$branch" "$branch: contained in $base_ref"
  else
    say KEEP "$path" "$branch: no merged PR, commits not in $base_ref"
  fi
}

path="" branch="" locked="" have=0
while IFS= read -r line || [[ -n "$line" ]]; do
  case "$line" in
    "worktree "*) path="${line#worktree }"; branch=""; locked=""; have=1 ;;
    "branch "*) branch="${line#branch refs/heads/}" ;;
    "locked"*) locked=1 ;;
    "") if [[ $have -eq 1 ]]; then consider "$path" "$branch" "$locked"; have=0; fi ;;
  esac
done < <(git -C "$main" worktree list --porcelain; echo)

[[ $dry -eq 1 ]] || git -C "$main" worktree prune
