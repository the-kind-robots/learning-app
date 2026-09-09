## 1. Priority on the issue field

- [x] 1.1 `start_issue_flow.sh`: set Priority with `setIssueFieldValue` on the org's native
      issue field, resolving the field and option by name at runtime
- [x] 1.2 `start_issue_flow.sh`: reject the retired scale with the replacement named
- [x] 1.3 `finish_issue_flow.sh`: check whether it touches Priority (it does not; Status only)

## 2. Drop the id-resolution layer

- [x] 2.1 Remove `find_field_id`, `find_option_id`, `apply_single_select_field` and
      `normalize_fields_json` from both scripts
- [x] 2.2 Remove the project field-and-option query and the project-id lookup that fed them
- [x] 2.3 Set Status, Area and Size with `gh project item-edit <number> --owner --url --field
      --value`
- [x] 2.4 Keep the paginated item query — `--field` cannot be combined with `--format json`,
      so it is not replaced, and the title dedupe it feeds has no CLI equivalent

## 3. Retarget the board

- [x] 3.1 `references/config.env.example`: org owner, org repo, project 11
- [x] 3.2 `.skills/gh-project-workflow/SKILL.md` and `.skills/repo-task-delivery/SKILL.md`
- [x] 3.3 `AGENTS.md` and `.claude/rules/repo-delivery.md`
- [x] 3.4 `.claude/hooks/delivery-guard.sh` and `.claude/hooks/coordinator-guard.sh`:
      refusal text only, no change to guard logic

## 4. Verify

- [x] 4.1 `bash -n` on both scripts and both hooks
- [x] 4.2 End-to-end run against project 11 creating a throwaway issue with a current
      priority, read back through the `ProjectV2ItemIssueFieldValue` fragment, then closed
      and removed from the board
- [x] 4.3 A retired value fails with the new message
- [x] 4.4 `git diff -U0` on each hook shows no non-comment logic change
