## 1. Build the skill

- [x] 1.1 Create `.skills/google-tasks/` in the shape of the other repo skills: `SKILL.md` with `name`/`description` frontmatter, `scripts/`, `references/`, `agents/openai.yaml`.
- [x] 1.2 Write `scripts/google_tasks.sh` (`set -euo pipefail`) with `doctor`, `auth`, `lists`, `pull`, `done`, `--help` on the script and on every subcommand.
- [x] 1.3 Read configuration from `${XDG_CONFIG_HOME:-$HOME/.config}/learning-app/google-tasks.env` with env-var overrides; default the client JSON to the same directory.
- [x] 1.4 Use `oauth2l header --credentials <json> --scope .../auth/tasks --refresh` per request; pass `showCompleted=false&showHidden=false` on the tasks call, since `showCompleted` defaults to true.
- [x] 1.5 Complete a task with `PATCH .../lists/{list}/tasks/{task}` and body `{"status":"completed"}` — `completed` is output-only and must not be sent.
- [x] 1.6 On a non-2xx, print the API's `error.message` and exit non-zero; never dump the raw response.
- [x] 1.7 Write `SKILL.md`: when to use, setup pointer, the commands, and the pull -> judge -> file -> mark-done workflow, including the issue-versus-comment rule from `AGENTS.md` **# Issues** and the ban on raw `gh issue create`.
- [x] 1.8 Write `references/setup.md`: oauth2l from the GCS bucket, Desktop-app OAuth client with `http://localhost` first, publishing status *In production* to avoid the 7-day refresh-token expiry, file locations and `chmod 600` (oauth2l creates `~/.oauth2l` world-readable — measured 0644 under umask 022).
- [x] 1.9 Write `references/google-tasks.env.example` with placeholders only.

## 2. Verify

- [x] 2.1 `bash -n` and `shellcheck` clean on `scripts/google_tasks.sh`.
- [x] 2.2 `--help` on the script and on each of the five subcommands prints usage and exits 0.
- [x] 2.3 `doctor` with nothing configured exits non-zero with an actionable message naming `references/setup.md` — not an unbound variable or a stack trace.
- [x] 2.4 `doctor` with a bogus client-JSON path fails the same way, naming the path.
- [x] 2.5 The `lists` and `pull` output shaping is executed against saved fixture responses, so the formatting is proven rather than assumed.
- [x] 2.6 Confirm the repository holds no credential: the tracked skill contains placeholders only.

## 3. Close out

- [x] 3.1 Archive this change on the issue branch, so one pull request carries the skill, the spec sync and the archive.
- [ ] 3.2 Open the pull request against `master` closing #386 and set the issue to In review. Do not merge — the first live API call needs the owner's Cloud Console setup.
