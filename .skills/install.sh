#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: .skills/install.sh [--force] [--agents codex claude]

Creates project-local skill links:
  .codex/skills  -> ../.skills
  .claude/skills -> ../.skills

Agents:
  codex   install .codex/skills
  claude  install .claude/skills

Default agents: codex claude.
Default is safe: refuse to replace real files/directories.
Use --force to replace existing skill directories or wrong symlinks.
USAGE
}

force=0
agents=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) force=1 ;;
    --agents)
      shift
      if [[ $# -eq 0 || "${1:-}" == --* ]]; then
        echo "missing value after --agents" >&2
        usage >&2
        exit 2
      fi
      while [[ $# -gt 0 && "${1:-}" != --* ]]; do
        agents+=("$1")
        shift
      done
      continue
      ;;
    --help|-h) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

if [[ "${#agents[@]}" -eq 0 ]]; then
  agents=(codex claude)
fi

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
link_target="../.skills"

install_agent() {
  local agent="$1"
  local tool_dir=""

  case "$agent" in
    codex) tool_dir=".codex" ;;
    claude) tool_dir=".claude" ;;
    *)
      echo "unknown agent: $agent" >&2
      return 2
      ;;
  esac

  local link_path="$repo_root/$tool_dir/skills"
  local current=""

  mkdir -p "$repo_root/$tool_dir"

  if [[ -L "$link_path" ]]; then
    current="$(readlink "$link_path")"
    if [[ "$current" == "$link_target" ]]; then
      echo "ok  $tool_dir/skills -> $link_target"
      return 0
    fi
    if [[ "$force" -ne 1 ]]; then
      echo "fail $tool_dir/skills is symlink to $current; rerun with --force to replace" >&2
      return 1
    fi
    rm "$link_path"
  elif [[ -e "$link_path" ]]; then
    if [[ "$force" -ne 1 ]]; then
      echo "fail $tool_dir/skills exists and is not a symlink; rerun with --force to replace" >&2
      return 1
    fi
    rm -rf "$link_path"
  fi

  ln -s "$link_target" "$link_path"
  echo "ok  $tool_dir/skills -> $link_target"
}

for agent in "${agents[@]}"; do
  install_agent "$agent"
done
