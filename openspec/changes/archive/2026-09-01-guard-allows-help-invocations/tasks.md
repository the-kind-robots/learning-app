## 1. Implementation

- [x] 1.1 In `.claude/hooks/delivery-guard.sh`, skip a statement carrying a standalone
      `--help` or `-h`, placed once ahead of the `git`/`gh` dispatch
- [x] 1.2 Match the flag as a whole word and record, in the comment, what the test cannot
      tell apart after upstream word-splitting

## 2. Verification

- [x] 2.1 `bash -n .claude/hooks/delivery-guard.sh`
- [x] 2.2 Feed the hook harness-shaped payloads on stdin: `gh issue create --help`,
      `gh pr create --help`, `gh issue create -h` pass with no decision
- [x] 2.3 Same harness: `gh issue create --title ... --body ...` still denied,
      `gh pr create --fill` from a non-issue branch still denied,
      `git checkout -b something` still asks
