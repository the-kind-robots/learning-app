#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: finish_issue_flow.sh [options]

Commit current work, push branch, open PR, and merge it.

Options:
  --repo <owner/repo>           Repo (default: GHWF_REPO, then inferred from origin)
  --base <branch>               Base branch for PR (default: GHWF_DEFAULT_BASE or master)
  --issue <number>              Issue number for title/body generation
  --commit-message <text>       Commit message override
  --pr-title <text>             PR title override
  --pr-body <text>              PR body override
  --pr-body-file <path>         PR body file override
  --merge-method <squash|merge|rebase>  Default: squash
  --type <feat|fix|chore|docs|refactor|test>  Commit type when auto-generating
  --owner <login>               Project owner (default: GHWF_OWNER)
  --project-number <number>     Project number (default: GHWF_PROJECT_NUMBER)
  --status-field <name>         Field name (default: GHWF_STATUS_FIELD or Status)
  --done-status <option>        Status option to set after merge (default: GHWF_DONE_STATUS or Done)
  --no-push                     Skip push
  --no-pr                       Skip PR creation
  --no-merge                    Skip merge
  --keep-branch                 Do not delete branch on merge
  --help                        Show this help
USAGE
}

die() {
  echo "Error: $*" >&2
  exit 1
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

normalize_fields_json() {
  local raw_json="$1"
  jq -c 'if type == "array" then . else (.fields // []) end' <<<"$raw_json"
}

find_field_id() {
  local fields_json="$1"
  local field_name="$2"
  jq -r --arg field_name "$field_name" '.[] | select(.name == $field_name) | .id' <<<"$fields_json" | head -n1
}

find_option_id() {
  local fields_json="$1"
  local field_name="$2"
  local option_name="$3"
  jq -r --arg field_name "$field_name" --arg option_name "$option_name" '.[] | select(.name == $field_name) | .options[]? | select(.name == $option_name) | .id' <<<"$fields_json" | head -n1
}

apply_single_select_field() {
  local item_id="$1"
  local project_id="$2"
  local fields_json="$3"
  local field_name="$4"
  local option_name="$5"
  local required="${6:-true}"

  [[ -n "$option_name" ]] || return 0

  local field_id
  field_id="$(find_field_id "$fields_json" "$field_name")"
  if [[ -z "$field_id" ]]; then
    if [[ "$required" == "true" ]]; then
      die "Field '$field_name' not found in project"
    else
      echo "Warning: Field '$field_name' not found in project, skipping requested option '$option_name'" >&2
      return 0
    fi
  fi

  local option_id
  option_id="$(find_option_id "$fields_json" "$field_name" "$option_name")"
  [[ -n "$option_id" ]] || die "Option '$option_name' not found in field '$field_name'"

  gh project item-edit \
    --id "$item_id" \
    --project-id "$project_id" \
    --field-id "$field_id" \
    --single-select-option-id "$option_id" >/dev/null
}

mark_issue_done_in_project() {
  local owner="$1"
  local project_number="$2"
  local issue_number="$3"
  local status_field="$4"
  local done_status="$5"

  [[ -n "$owner" && -n "$project_number" && -n "$issue_number" ]] || return 0

  local project_id
  project_id="$(gh project view "$project_number" --owner "$owner" --format json --jq '.id' 2>/dev/null || true)"
  [[ -n "$project_id" ]] || {
    echo "Warning: Unable to resolve project id, skipping status update" >&2
    return 0
  }

  local fields_json
  fields_json="$(normalize_fields_json "$(gh project field-list "$project_number" --owner "$owner" --format json)")"

  local item_json item_id
  item_json="$(gh project item-list "$project_number" --owner "$owner" --limit 100 --format json)"
  item_id="$(jq -r --argjson issue_number "$issue_number" '.items[] | select(.content.number? == $issue_number) | .id' <<<"$item_json" | head -n1)"

  [[ -n "$item_id" ]] || {
    echo "Warning: Project item for issue #$issue_number not found, skipping status update" >&2
    return 0
  }

  apply_single_select_field "$item_id" "$project_id" "$fields_json" "$status_field" "$done_status" true
}

infer_repo_from_origin() {
  local origin_url
  origin_url="$(git remote get-url origin 2>/dev/null || true)"

  if [[ "$origin_url" =~ github.com[:/]([^/]+/[^/.]+)(\.git)?$ ]]; then
    printf '%s' "${BASH_REMATCH[1]}"
    return 0
  fi

  return 1
}

repo="${GHWF_REPO:-}"
owner="${GHWF_OWNER:-}"
project_number="${GHWF_PROJECT_NUMBER:-}"
status_field="${GHWF_STATUS_FIELD:-Status}"
done_status="${GHWF_DONE_STATUS:-Done}"
base_branch="${GHWF_DEFAULT_BASE:-master}"
issue_number=""
commit_message=""
pr_title=""
pr_body=""
pr_body_file=""
merge_method="squash"
commit_type="chore"
skip_push=0
skip_pr=0
skip_merge=0
delete_branch=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)
      repo="$2"
      shift 2
      ;;
    --owner)
      owner="$2"
      shift 2
      ;;
    --project-number)
      project_number="$2"
      shift 2
      ;;
    --base)
      base_branch="$2"
      shift 2
      ;;
    --issue)
      issue_number="$2"
      shift 2
      ;;
    --commit-message)
      commit_message="$2"
      shift 2
      ;;
    --pr-title)
      pr_title="$2"
      shift 2
      ;;
    --pr-body)
      pr_body="$2"
      shift 2
      ;;
    --pr-body-file)
      pr_body_file="$2"
      shift 2
      ;;
    --merge-method)
      merge_method="$2"
      shift 2
      ;;
    --type)
      commit_type="$2"
      shift 2
      ;;
    --status-field)
      status_field="$2"
      shift 2
      ;;
    --done-status)
      done_status="$2"
      shift 2
      ;;
    --no-push)
      skip_push=1
      shift
      ;;
    --no-pr)
      skip_pr=1
      shift
      ;;
    --no-merge)
      skip_merge=1
      shift
      ;;
    --keep-branch)
      delete_branch=0
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

[[ "$merge_method" == "squash" || "$merge_method" == "merge" || "$merge_method" == "rebase" ]] || die "Invalid --merge-method"

if [[ -z "$repo" ]]; then
  repo="$(infer_repo_from_origin || true)"
fi

branch_name="$(git rev-parse --abbrev-ref HEAD)"
[[ "$branch_name" != "HEAD" ]] || die "Detached HEAD is not supported"

if [[ -z "$issue_number" && "$branch_name" =~ ^([0-9]+)- ]]; then
  issue_number="${BASH_REMATCH[1]}"
fi

issue_title=""
if [[ -n "$issue_number" && -n "$repo" ]]; then
  issue_title="$(gh issue view "$issue_number" -R "$repo" --json title --jq '.title' 2>/dev/null || true)"
fi

git add -A

git diff --cached --quiet && die "No changes to commit"

if [[ -z "$commit_message" ]]; then
  if [[ -n "$issue_title" && -n "$issue_number" ]]; then
    commit_message="$commit_type: $issue_title (#$issue_number)"
  elif [[ -n "$issue_number" ]]; then
    commit_message="$commit_type: update for issue #$issue_number"
  else
    first_file="$(git diff --cached --name-only | head -n1)"
    if [[ -n "$first_file" ]]; then
      commit_message="$commit_type: update $first_file"
    else
      commit_message="$commit_type: update project files"
    fi
  fi
fi

git commit -m "$commit_message"

if [[ "$skip_push" -eq 0 ]]; then
  git push -u origin "$branch_name"
fi

if [[ "$skip_pr" -eq 1 ]]; then
  echo "Commit Message: $commit_message"
  echo "Branch: $branch_name"
  exit 0
fi

[[ -n "$repo" ]] || die "--repo is required for PR operations"

if [[ -z "$pr_title" ]]; then
  if [[ -n "$issue_title" && -n "$issue_number" ]]; then
    pr_title="Resolve #$issue_number: $issue_title"
  else
    pr_title="$commit_message"
  fi
fi

if [[ -z "$pr_body" && -z "$pr_body_file" ]]; then
  if [[ -n "$issue_number" ]]; then
    pr_body=$(cat <<BODY
## Summary
- $commit_message

Closes #$issue_number
BODY
)
  else
    pr_body=$(cat <<BODY
## Summary
- $commit_message
BODY
)
  fi
fi

pr_url="$(gh pr view --head "$branch_name" -R "$repo" --json url --jq '.url' 2>/dev/null || true)"

if [[ -z "$pr_url" ]]; then
  pr_create_args=(pr create -R "$repo" --base "$base_branch" --head "$branch_name" --title "$pr_title")
  if [[ -n "$pr_body_file" ]]; then
    pr_create_args+=(--body-file "$pr_body_file")
  else
    pr_create_args+=(--body "$pr_body")
  fi

  pr_url="$(gh "${pr_create_args[@]}" | tail -n1 | tr -d '\r')"
fi

if [[ "$skip_merge" -eq 1 ]]; then
  echo "Commit Message: $commit_message"
  echo "PR URL: $pr_url"
  exit 0
fi

merge_flag="--squash"
if [[ "$merge_method" == "merge" ]]; then
  merge_flag="--merge"
elif [[ "$merge_method" == "rebase" ]]; then
  merge_flag="--rebase"
fi

merge_args=(pr merge "$pr_url" "$merge_flag")
if [[ "$delete_branch" -eq 1 ]]; then
  merge_args+=(--delete-branch)
fi

if ! gh "${merge_args[@]}"; then
  gh "${merge_args[@]}" --auto
fi

pr_state="$(gh pr view "$pr_url" -R "$repo" --json state --jq '.state' 2>/dev/null || true)"
if [[ "$pr_state" == "MERGED" ]]; then
  mark_issue_done_in_project "$owner" "$project_number" "$issue_number" "$status_field" "$done_status"
fi

echo "Commit Message: $commit_message"
echo "PR URL: $pr_url"
