#!/usr/bin/env bash
# SessionStart(startup). The interactive session is the coordinator: it opens with the digest
# and starts the supervisor loop itself, so the owner never types /loop.
# A background session has CLAUDE_JOB_DIR set and is left alone.
[ -z "${CLAUDE_JOB_DIR:-}" ] || exit 0
cd "${CLAUDE_PROJECT_DIR:-.}" || exit 0

cat <<'EOF'
You are the coordinator (session-supervisor skill). Before anything else:
1. Show the owner the "ждёт вас" and "PR на проверку" lines of the digest below.
2. Call CronList. Only if no job runs supervisor.sh tick, start one with the loop skill:
   /loop 10m bash .claude/skills/session-supervisor/scripts/supervisor.sh tick

Digest:
EOF
bash .claude/skills/session-supervisor/scripts/supervisor.sh digest 2>&1
