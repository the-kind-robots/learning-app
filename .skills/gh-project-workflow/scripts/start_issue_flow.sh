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
  --issue <number>              Reuse existing issue number instead of creating one
  --area <option>               Area field option to set
  --category <option>           Deprecated alias for --area
  --priority <option>           Priority to set on the issue: Urgent|High|Medium|Low
                                (native org issue field, not a project field)
  --size <option>               Size field option to set
  --status <option>             Status field option to set
  --mode <issue|draft-convert>  Default: issue
  --owner <login>               Project owner (default: GHWF_OWNER)
  --repo <owner/repo>           Repo (default: GHWF_REPO)
  --project-number <number>     Project number (default: GHWF_PROJECT_NUMBER)
  --area-field <name>           Field name (default: GHWF_AREA_FIELD or Area)
  --category-field <name>       Deprecated alias for --area-field
  --priority-field <name>       Issue field name (default: GHWF_PRIORITY_FIELD or Priority)
  --size-field <name>           Field name (default: GHWF_SIZE_FIELD or Size)
  --status-field <name>         Field name (default: GHWF_STATUS_FIELD or Status)
  --base <branch>               Base branch for dev branch (default: GHWF_DEFAULT_BASE or master)
  --branch-name <name>          Override branch name
  --no-reuse-existing           Do not reuse exact-title project item or issue
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

graphql_remaining() {
  gh api rate_limit --jq '.resources.graphql.remaining' 2>/dev/null || true
}

# gh 2.98.0 resolves a project field and its option by name, so the id-lookup layer this
# script used to carry is gone. The project number is POSITIONAL here; `--number` means a
# numeric field value.
apply_project_field() {
  local field_name="$1"
  local option_name="$2"
  local required="${3:-true}"

  [[ -n "$option_name" ]] || return 0

  local output
  if output="$(gh project item-edit "$project_number" \
                 --owner "$owner" \
                 --url "$issue_url" \
                 --field "$field_name" \
                 --value "$option_name" 2>&1)"; then
    return 0
  fi

  [[ "$required" != "true" ]] || die "Failed to set '$field_name' to '$option_name': $output"
  echo "Warning: Failed to set '$field_name' to '$option_name', skipping: $output" >&2
}

# The old five-value scale died with the personal board. Name the replacement, because
# "not found" reads as a broken script when it is really a retired vocabulary.
retired_priority_replacement() {
  case "$1" in
    Blocker|Critical) printf 'Urgent' ;;
    Major)            printf 'High' ;;
    Minor)            printf 'Medium' ;;
    Trivial)          printf 'Low' ;;
  esac
}

# Priority is no longer a project field. It is the organization's native issue field, and
# `updateProjectV2ItemFieldValue` refuses to write it — the board's `Priority` column is fed
# by the issue, so it must be set on the issue with `setIssueFieldValue`. The project still
# exposes a `Priority` field with no options, which is why the old lookup died with
# "Option 'High' not found in field 'Priority'".
apply_issue_priority() {
  local field_name="$1"
  local option_name="$2"

  [[ -n "$option_name" ]] || return 0

  local org="${repo%%/*}"
  local field_query='
    query($login: String!) {
      organization(login: $login) {
        issueFields(first: 50) {
          nodes {
            ... on IssueFieldSingleSelect { id name options { id name } }
          }
        }
      }
    }'

  local field_json
  field_json="$(gh api graphql -f query="$field_query" -F login="$org" \
                 | jq -c --arg field_name "$field_name" \
                     '[.data.organization.issueFields.nodes[] | select(.name == $field_name)] | .[0] // {}')"

  local field_id
  field_id="$(jq -r '.id // empty' <<<"$field_json")"
  if [[ -z "$field_id" ]]; then
    echo "Warning: Issue field '$field_name' not found in organization '$org', skipping requested option '$option_name'" >&2
    return 0
  fi

  local option_id
  option_id="$(jq -r --arg option_name "$option_name" '.options[]? | select(.name == $option_name) | .id' <<<"$field_json" | head -n1)"
  if [[ -z "$option_id" ]]; then
    local available
    available="$(jq -r '[.options[]?.name] | join("/")' <<<"$field_json")"
    die "Option '$option_name' not found in issue field '$field_name'. Available: ${available:-none}"
  fi

  local issue_id
  issue_id="$(gh issue view "$issue_number" -R "$repo" --json id --jq '.id')"
  [[ -n "$issue_id" ]] || die "Unable to resolve node id for issue #$issue_number"

  local set_query='
    mutation($issueId: ID!, $fieldId: ID!, $optionId: ID!) {
      setIssueFieldValue(input: {issueId: $issueId,
                                 issueFields: [{fieldId: $fieldId, singleSelectOptionId: $optionId}]}) {
        clientMutationId
      }
    }'
  gh api graphql -f query="$set_query" \
    -F issueId="$issue_id" -F fieldId="$field_id" -F optionId="$option_id" >/dev/null
}

# `gh project item-list` asks for every field value of every item — 203 points of the 5000
# hourly GraphQL budget on a board this size. Matching an item needs four fields, so ask for
# those. Output keeps the shape the CLI produced, so the jq matchers below are unchanged.
project_items_json() {
  local query='
    query($owner: String!, $number: Int!, $after: String) {
      owner: repositoryOwner(login: $owner) {
        ... on ProjectV2Owner {
          projectV2(number: $number) {
            items(first: 100, after: $after) {
              pageInfo { hasNextPage endCursor }
              nodes {
                id
                isArchived
                content {
                  __typename
                  ... on Issue { number url title }
                  ... on PullRequest { number url title }
                  ... on DraftIssue { title }
                }
              }
            }
          }
        }
      }
    }'

  local after="null" page nodes all="[]" has_next
  while :; do
    if [[ "$after" == "null" ]]; then
      page="$(gh api graphql -f query="$query" -F owner="$owner" -F number="$project_number")"
    else
      page="$(gh api graphql -f query="$query" -F owner="$owner" -F number="$project_number" -F after="$after")"
    fi

    # Archived items are out of the CLI's listing too; reusing one would resurrect
    # work the owner had put away.
    nodes="$(jq -c '[.data.owner.projectV2.items.nodes[]
                     | select(.isArchived | not)
                     | {id: .id,
                        title: (.content.title // ""),
                        content: {type: .content.__typename,
                                  number: .content.number,
                                  url: .content.url}}]' <<<"$page")"
    all="$(jq -c --argjson nodes "$nodes" '. + $nodes' <<<"$all")"

    has_next="$(jq -r '.data.owner.projectV2.items.pageInfo.hasNextPage' <<<"$page")"
    [[ "$has_next" == "true" ]] || break
    after="$(jq -r '.data.owner.projectV2.items.pageInfo.endCursor' <<<"$page")"
  done

  jq -c --argjson items "$all" -n '{items: $items}'
}

find_project_item_by_title() {
  local items_json="$1"
  local item_title="$2"
  jq -c --arg item_title "$item_title" '.items[] | select(.title == $item_title)' <<<"$items_json" | head -n1
}

find_project_item_by_issue_number() {
  local items_json="$1"
  local number="$2"
  jq -c --argjson number "$number" '.items[] | select(.content.number? == $number)' <<<"$items_json" | head -n1
}

find_issue_by_title() {
  local issue_title="$1"
  gh issue list -R "$repo" --state all --search "$issue_title in:title" --limit 20 --json number,title,url \
    | jq -c --arg issue_title "$issue_title" '.[] | select(.title == $issue_title)' \
    | head -n1
}

label_exists() {
  local label="$1"

  if [[ -z "${labels_json:-}" ]]; then
    labels_json="$(gh label list -R "$repo" --limit 200 --json name 2>/dev/null || true)"
  fi

  [[ -n "$labels_json" ]] || return 1
  jq -e --arg label "$label" '.[] | select(.name == $label)' <<<"$labels_json" >/dev/null
}

append_existing_labels() {
  local -n args_ref=$1
  local csv="$2"
  local flag="${3:---label}"

  [[ -n "$csv" ]] || return 0

  IFS=',' read -r -a labels <<<"$csv"
  for label in "${labels[@]}"; do
    label="$(trim "$label")"
    [[ -n "$label" ]] || continue
    if label_exists "$label"; then
      args_ref+=("$flag" "$label")
    else
      echo "Warning: Label '$label' not found in repo '$repo', skipping" >&2
    fi
  done
}

append_assignees() {
  local -n args_ref=$1
  local csv="$2"
  local flag="${3:---assignee}"

  [[ -n "$csv" ]] || return 0

  IFS=',' read -r -a assignees <<<"$csv"
  for assignee in "${assignees[@]}"; do
    assignee="$(trim "$assignee")"
    [[ -n "$assignee" ]] && args_ref+=("$flag" "$assignee")
  done
}

convert_project_draft_to_issue() {
  local draft_item_id="$1"

  local repo_id
  repo_id="$(gh repo view "$repo" --json id --jq '.id')"
  [[ -n "$repo_id" ]] || die "Failed to resolve repository id"

  local convert_query convert_json
  convert_query='mutation($projectItemId:ID!,$repositoryId:ID!){ convertProjectV2DraftIssueItemToIssue(input:{projectItemId:$projectItemId,repositoryId:$repositoryId}) { issue { number url } } }'
  convert_json="$(gh api graphql -f query="$convert_query" -F projectItemId="$draft_item_id" -F repositoryId="$repo_id")"

  issue_number="$(jq -r '.data.convertProjectV2DraftIssueItemToIssue.issue.number // empty' <<<"$convert_json")"
  issue_url="$(jq -r '.data.convertProjectV2DraftIssueItemToIssue.issue.url // empty' <<<"$convert_json")"
  [[ -n "$issue_number" && -n "$issue_url" ]] || die "Draft item conversion to issue failed"
}

use_project_item() {
  local item="$1"

  item_id="$(jq -r '.id // empty' <<<"$item")"
  [[ -n "$item_id" ]] || die "Existing project item has no id"

  local content_type
  content_type="$(jq -r '.content.type // empty' <<<"$item")"

  case "$content_type" in
    Issue)
      issue_number="$(jq -r '.content.number // empty' <<<"$item")"
      issue_url="$(jq -r '.content.url // empty' <<<"$item")"
      [[ -n "$issue_number" && -n "$issue_url" ]] || die "Existing issue item is missing issue data"
      ;;
    DraftIssue)
      convert_project_draft_to_issue "$item_id"
      ;;
    *)
      die "Existing project item '$title' is not an issue or draft issue"
      ;;
  esac
}

owner="${GHWF_OWNER:-}"
repo="${GHWF_REPO:-}"
project_number="${GHWF_PROJECT_NUMBER:-}"
area_field="${GHWF_AREA_FIELD:-${GHWF_CATEGORY_FIELD:-Area}}"
priority_field="${GHWF_PRIORITY_FIELD:-Priority}"
size_field="${GHWF_SIZE_FIELD:-Size}"
status_field="${GHWF_STATUS_FIELD:-Status}"
base_branch="${GHWF_DEFAULT_BASE:-master}"

title=""
body=""
body_file=""
labels_csv=""
assignees_csv="${GHWF_DEFAULT_ASSIGNEES:-@me}"
issue_number_override=""
area_option="${GHWF_DEFAULT_AREA:-}"
priority_option="${GHWF_DEFAULT_PRIORITY:-}"
size_option="${GHWF_DEFAULT_SIZE:-}"
status_option="${GHWF_DEFAULT_STATUS:-Backlog}"
mode="issue"
branch_name=""
reuse_existing=1
skip_branch=0
labels_json=""

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
    --issue)
      issue_number_override="$2"
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
    --priority)
      priority_option="$2"
      shift 2
      ;;
    --size)
      size_option="$2"
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
    --priority-field)
      priority_field="$2"
      shift 2
      ;;
    --size-field)
      size_field="$2"
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
    --no-reuse-existing)
      reuse_existing=0
      shift
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

# Checked before anything is created: a run that files the issue and then dies on the
# priority leaves half-tracked work behind.
retired_priority="$(retired_priority_replacement "$priority_option")"
[[ -z "$retired_priority" ]] || die "Priority '$priority_option' was retired with the old board; the scale is now Urgent/High/Medium/Low. Use '$retired_priority'."

graphql_budget_before="$(graphql_remaining)"

issue_url=""
issue_number=""
item_id=""

if [[ "$reuse_existing" -eq 1 ]]; then
  items_json="$(project_items_json)"
  existing_item=""

  if [[ -n "$issue_number_override" ]]; then
    existing_item="$(find_project_item_by_issue_number "$items_json" "$issue_number_override")"
  fi

  if [[ -z "$existing_item" ]]; then
    existing_item="$(find_project_item_by_title "$items_json" "$title")"
  fi

  if [[ -n "$existing_item" ]]; then
    use_project_item "$existing_item"
  elif [[ -n "$issue_number_override" ]]; then
    issue_number="$issue_number_override"
    issue_url="$(gh issue view "$issue_number" -R "$repo" --json url --jq '.url')"
    [[ -n "$issue_url" ]] || die "Issue #$issue_number not found"

    item_json="$(gh project item-add "$project_number" --owner "$owner" --url "$issue_url" --format json)"
    item_id="$(jq -r '.id // empty' <<<"$item_json")"
    [[ -n "$item_id" ]] || die "Failed to add issue to project"
  else
    existing_issue="$(find_issue_by_title "$title")"
    if [[ -n "$existing_issue" ]]; then
      issue_number="$(jq -r '.number // empty' <<<"$existing_issue")"
      issue_url="$(jq -r '.url // empty' <<<"$existing_issue")"
      [[ -n "$issue_number" && -n "$issue_url" ]] || die "Existing issue is missing issue data"

      item_json="$(gh project item-add "$project_number" --owner "$owner" --url "$issue_url" --format json)"
      item_id="$(jq -r '.id // empty' <<<"$item_json")"
      [[ -n "$item_id" ]] || die "Failed to add existing issue to project"
    fi
  fi
fi

if [[ -z "$issue_number" && "$mode" == "issue" ]]; then
  issue_args=(issue create -R "$repo" --title "$title")

  if [[ -n "$body_file" ]]; then
    issue_args+=(--body-file "$body_file")
  elif [[ -n "$body" ]]; then
    issue_args+=(--body "$body")
  fi

  append_existing_labels issue_args "$labels_csv"
  append_assignees issue_args "$assignees_csv"

  issue_url="$(gh "${issue_args[@]}" | tail -n1 | tr -d '\r')"
  [[ -n "$issue_url" ]] || die "Issue creation failed"

  item_json="$(gh project item-add "$project_number" --owner "$owner" --url "$issue_url" --format json)"
  item_id="$(jq -r '.id // empty' <<<"$item_json")"
  [[ -n "$item_id" ]] || die "Failed to add issue to project"

  issue_number="${issue_url##*/}"
elif [[ -z "$issue_number" ]]; then
  draft_args=(project item-create "$project_number" --owner "$owner" --title "$title" --format json)

  if [[ -n "$body_file" ]]; then
    draft_args+=(--body "$(cat "$body_file")")
  elif [[ -n "$body" ]]; then
    draft_args+=(--body "$body")
  fi

  item_json="$(gh "${draft_args[@]}")"
  item_id="$(jq -r '.id // empty' <<<"$item_json")"
  [[ -n "$item_id" ]] || die "Draft item creation failed"

  convert_project_draft_to_issue "$item_id"
fi

if [[ -n "$issue_number" ]]; then
  edit_args=(issue edit "$issue_number" -R "$repo")
  append_existing_labels edit_args "$labels_csv" "--add-label"
  append_assignees edit_args "$assignees_csv" "--add-assignee"
  if [[ "${#edit_args[@]}" -gt 5 ]]; then
    gh "${edit_args[@]}" >/dev/null
  fi
fi

apply_project_field "$area_field" "$area_option" false
apply_issue_priority "$priority_field" "$priority_option"
apply_project_field "$size_field" "$size_option" false
apply_project_field "$status_field" "$status_option" true

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

# A run that quietly eats a tenth of the hourly budget is only noticed an hour later, when
# the next run is refused. Print the price.
if [[ -n "$graphql_budget_before" ]]; then
  graphql_budget_after="$(graphql_remaining)"
  if [[ -n "$graphql_budget_after" ]]; then
    echo "GraphQL Cost: $(( graphql_budget_before - graphql_budget_after )) points (${graphql_budget_after} left this hour)"
  fi
fi
