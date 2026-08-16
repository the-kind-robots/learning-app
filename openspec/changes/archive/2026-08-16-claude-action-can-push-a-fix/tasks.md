## 1. Workflow

- [x] 1.1 Add `.github/workflows/claude.yml` with the `@claude` triggers on issues, issue
      comments, pull request reviews and review comments
- [x] 1.2 Grant the job `contents: write` and `pull-requests: write`, keeping `actions: read` and
      `id-token: write`
- [x] 1.3 Authenticate through the `CLAUDE_CODE_OAUTH_TOKEN` secret, with no token in the file

## 2. Verification

- [x] 2.1 Confirm the workflow parses as YAML and its permission block reads as intended
- [x] 2.2 Confirm `CLAUDE_CODE_OAUTH_TOKEN` exists in the repository secrets
- [x] 2.3 Confirm `master` is protected, so the granted write access cannot bypass review

## 3. Closeout

- [ ] 3.1 Delete the superseded branch `add-claude-github-actions-1786896889717`
- [ ] 3.2 Archive the change and open the pull request
