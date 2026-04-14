#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage: start_issue_flow.sh --title <title> [options]

Create work in GitHub Project, assign fields, ensure issue exists, and create/checkout dev branch.

Options:
  --title <text>                Required issue or draft title
  --body <text>                 Body text
  --body-file <path>            Body file path
  --labels <csv>                Comma-separated labels
  --assignees <csv>             Comma-separated assignees (supports @me)
  --area <option>               Area field option to set
  --category <option>           Deprecated alias for --area
  --status <option>             Status field option to set
  --mode <issue|draft-convert>  Default: issue
  --owner <login>               Project owner (default: GHWF_OWNER)
  --repo <owner/repo>           Repo (default: GHWF_REPO)
  --project-number <number>     Project number (default: GHWF_PROJECT_NUMBER)
  --area-field <name>           Field name (default: GHWF_AREA_FIELD or Area)
  --category-field <name>       Deprecated alias for --area-field
  --status-field <name>         Field name (default: GHWF_STATUS_FIELD or Status)
  --base <branch>               Base branch for dev branch (default: GHWF_DEFAULT_BASE or master)
  --branch-name <name>          Override branch name
  --skip-branch                 Do not create/checkout development branch
  --help                        Show this help
USAGE
}

trim() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

die() {
  echo "Error: $*" >&2
  exit 1
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

owner="${GHWF_OWNER:-}"
repo="${GHWF_REPO:-}"
project_number="${GHWF_PROJECT_NUMBER:-}"
area_field="${GHWF_AREA_FIELD:-${GHWF_CATEGORY_FIELD:-Area}}"
status_field="${GHWF_STATUS_FIELD:-Status}"
base_branch="${GHWF_DEFAULT_BASE:-master}"

title=""
body=""
body_file=""
labels_csv=""
assignees_csv="${GHWF_DEFAULT_ASSIGNEES:-@me}"
area_option="${GHWF_DEFAULT_AREA:-}"
status_option="${GHWF_DEFAULT_STATUS:-Todo}"
mode="issue"
branch_name=""
skip_branch=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --title)
      title="$2"
      shift 2
      ;;
    --body)
      body="$2"
      shift 2
      ;;
    --body-file)
      body_file="$2"
      shift 2
      ;;
    --labels)
      labels_csv="$2"
      shift 2
      ;;
    --assignees)
      assignees_csv="$2"
      shift 2
      ;;
    --area)
      area_option="$2"
      shift 2
      ;;
    --category)
      area_option="$2"
      shift 2
      ;;
    --status)
      status_option="$2"
      shift 2
      ;;
    --mode)
      mode="$2"
      shift 2
      ;;
    --owner)
      owner="$2"
      shift 2
      ;;
    --repo)
      repo="$2"
      shift 2
      ;;
    --project-number)
      project_number="$2"
      shift 2
      ;;
    --area-field)
      area_field="$2"
      shift 2
      ;;
    --category-field)
      area_field="$2"
      shift 2
      ;;
    --status-field)
      status_field="$2"
      shift 2
      ;;
    --base)
      base_branch="$2"
      shift 2
      ;;
    --branch-name)
      branch_name="$2"
      shift 2
      ;;
    --skip-branch)
      skip_branch=1
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

[[ -n "$title" ]] || die "--title is required"
[[ -n "$owner" ]] || die "--owner or GHWF_OWNER is required"
[[ -n "$repo" ]] || die "--repo or GHWF_REPO is required"
[[ -n "$project_number" ]] || die "--project-number or GHWF_PROJECT_NUMBER is required"
[[ "$mode" == "issue" || "$mode" == "draft-convert" ]] || die "--mode must be 'issue' or 'draft-convert'"

project_id="$(gh project view "$project_number" --owner "$owner" --format json --jq '.id')"
[[ -n "$project_id" ]] || die "Unable to resolve project id"
fields_json="$(normalize_fields_json "$(gh project field-list "$project_number" --owner "$owner" --format json)")"

issue_url=""
issue_number=""
item_id=""

if [[ "$mode" == "issue" ]]; then
  issue_args=(issue create -R "$repo" --title "$title")

  if [[ -n "$body_file" ]]; then
    issue_args+=(--body-file "$body_file")
  elif [[ -n "$body" ]]; then
    issue_args+=(--body "$body")
  fi

  if [[ -n "$labels_csv" ]]; then
    IFS=',' read -r -a labels <<<"$labels_csv"
    for label in "${labels[@]}"; do
      label="$(trim "$label")"
      [[ -n "$label" ]] && issue_args+=(--label "$label")
    done
  fi

  if [[ -n "$assignees_csv" ]]; then
    IFS=',' read -r -a assignees <<<"$assignees_csv"
    for assignee in "${assignees[@]}"; do
      assignee="$(trim "$assignee")"
      [[ -n "$assignee" ]] && issue_args+=(--assignee "$assignee")
    done
  fi

  issue_url="$(gh "${issue_args[@]}" | tail -n1 | tr -d '\r')"
  [[ -n "$issue_url" ]] || die "Issue creation failed"

  item_json="$(gh project item-add "$project_number" --owner "$owner" --url "$issue_url" --format json)"
  item_id="$(jq -r '.id // empty' <<<"$item_json")"
  [[ -n "$item_id" ]] || die "Failed to add issue to project"

  issue_number="${issue_url##*/}"
else
  draft_args=(project item-create "$project_number" --owner "$owner" --title "$title" --format json)

  if [[ -n "$body_file" ]]; then
    draft_args+=(--body "$(cat "$body_file")")
  elif [[ -n "$body" ]]; then
    draft_args+=(--body "$body")
  fi

  item_json="$(gh "${draft_args[@]}")"
  item_id="$(jq -r '.id // empty' <<<"$item_json")"
  [[ -n "$item_id" ]] || die "Draft item creation failed"

  repo_id="$(gh repo view "$repo" --json id --jq '.id')"
  [[ -n "$repo_id" ]] || die "Failed to resolve repository id"

  convert_query='mutation($projectItemId:ID!,$repositoryId:ID!){ convertProjectV2DraftIssueItemToIssue(input:{projectItemId:$projectItemId,repositoryId:$repositoryId}) { issue { number url } } }'
  convert_json="$(gh api graphql -f query="$convert_query" -F projectItemId="$item_id" -F repositoryId="$repo_id")"

  issue_number="$(jq -r '.data.convertProjectV2DraftIssueItemToIssue.issue.number // empty' <<<"$convert_json")"
  issue_url="$(jq -r '.data.convertProjectV2DraftIssueItemToIssue.issue.url // empty' <<<"$convert_json")"
  [[ -n "$issue_number" && -n "$issue_url" ]] || die "Draft item conversion to issue failed"
fi

apply_single_select_field "$item_id" "$project_id" "$fields_json" "$area_field" "$area_option" false
apply_single_select_field "$item_id" "$project_id" "$fields_json" "$status_field" "$status_option" true

checked_out_branch=""
if [[ "$skip_branch" -eq 0 ]]; then
  develop_args=(issue develop "$issue_number" -R "$repo" --checkout)
  [[ -n "$base_branch" ]] && develop_args+=(--base "$base_branch")
  [[ -n "$branch_name" ]] && develop_args+=(--name "$branch_name")

  gh "${develop_args[@]}" >/dev/null
  checked_out_branch="$(git branch --show-current || true)"
fi

echo "Issue URL: $issue_url"
echo "Issue Number: $issue_number"
echo "Project Item ID: $item_id"
if [[ -n "$checked_out_branch" ]]; then
  echo "Checked Out Branch: $checked_out_branch"
fi
