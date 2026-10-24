#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: deploy.sh [options]

Run a deployment command.

Options:
  --cmd <command>      Deploy command (required)
  --env-file <path>    Source env file before running deploy command
  --dry-run            Print command without executing
  --help               Show this help
USAGE
}

die() {
  echo "Error: $*" >&2
  exit 1
}

deploy_cmd=""
env_file=""
dry_run=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --cmd)
      deploy_cmd="$2"
      shift 2
      ;;
    --env-file)
      env_file="$2"
      shift 2
      ;;
    --dry-run)
      dry_run=1
      shift
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

if [[ -n "$env_file" ]]; then
  [[ -f "$env_file" ]] || die "Env file not found: $env_file"
  # shellcheck disable=SC1090
  source "$env_file"
fi

[[ -n "$deploy_cmd" ]] || die "No deploy command configured. Pass --cmd"

echo "Deploy command: $deploy_cmd"

if [[ "$dry_run" -eq 1 ]]; then
  exit 0
fi

bash -lc "$deploy_cmd"
