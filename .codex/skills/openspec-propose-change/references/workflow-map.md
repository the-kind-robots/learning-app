# OpenSpec Flow Map

This repo's Codex-side OpenSpec flow is intentionally simpler than the older `opencode`/`opsx` surface.

## Primary Codex path

1. `openspec-propose-change`
2. `openspec-apply-change`
3. `openspec-archive-change`

## Supporting skills

- `openspec-verify-change`
- `openspec-sync-specs`
- `openspec-onboard`

## Legacy mapping

| Older `opsx` action | Codex-side replacement |
| --- | --- |
| `opsx:new` | `openspec-propose-change` |
| `opsx:continue` | usually `openspec-propose-change` for artifact creation, then `openspec-apply-change` once tasks exist |
| `opsx:apply` | `openspec-apply-change` |
| `opsx:verify` | `openspec-verify-change` |
| `opsx:sync` | `openspec-sync-specs` |
| `opsx:archive` | `openspec-archive-change` |
| `opsx:onboard` | `openspec-onboard` |

The old expanded flow is still useful as background context, but the preferred Codex mental model in this repo is `propose -> apply -> archive`.
