## 1. Implementation

- [x] 1.1 Add `.skills/gh-project-workflow/scripts/rename_session.sh <number> <title>`:
      sanitize the title, skip silently without socket/python3, send auth + rename over the
      unix socket, print `Session Name: ...` on success
- [x] 1.2 Call it from the tail of `start_issue_flow.sh` after `Issue Number:`, fetching the
      title from GitHub, guarded with `|| true`
- [x] 1.3 One line in `.skills/gh-project-workflow/SKILL.md` (Start Work) and one bullet in
      `AGENTS.md` (`# Delivery`)

## 2. Verification

- [x] 2.1 `bash -n` and `shellcheck` on both scripts
- [x] 2.2 Throwaway unix-socket listener receives exactly the auth line and the rename line
      with the sanitized name
- [x] 2.3 Unset socket env: exit 0, no output. Missing socket file: exit 0, no output
