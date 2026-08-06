# 0010. Staging ladder: headless, container, metal

- Status: proposed
- Date: 2026-08-06

## Context

One day of delivery produced five PRs whose verification honestly ends with "proven on the next real deploy": systemd stop behavior (#199), sandbox scores (#200), deploy transport (#198), database adoption (#209), backup contents (#211). The #168 class of bug — real only in the packaged run — lived for months precisely because nothing between "dev from source" and "production" exists. Meanwhile the dev stand is a shared, stateful workspace: agents may not touch it, and measurements on it fight the owner's own browser.

Rejected earlier and recorded in #169: driving the visible Windows browser via mirrored networking — a global switch risking two working dev stacks for a nice-to-have.

## Decision

Three rungs, each with a distinct question it answers:

1. **Headless in WSL** (exists — #169): does the application behave? Playwright against the suite's own backend; no nginx, no CouchDB, no stand.
2. **Container from the deb** (#214): does the production *shape* hold? The actual `.deb` installed in a systemd container; units boot under their sandboxes, migrations and adoption run, `backup.sh` produces a coherent archive, smoke over nginx. Turns in-PR checklists into a script.
3. **Mini PC staging** (#215): does the production *reality* hold? A self-hosted runner on home metal runs a `deploy-staging` mirror of the real deploy workflow — same transport, real systemd, multi-day observations, restore drills. Re-imaged only via `admin-setup.sh`, which thereby stays honest.

The rungs are ordered by cost of the answer: minutes, minutes-to-hours, standing infrastructure.

## Consequences

- Every "pends the server" claim gains a home short of production; infra PRs stop merging on faith.
- The public repository makes the self-hosted runner the sharpest edge: it accepts only `workflow_dispatch` deploys behind a reviewed GitHub Environment — never `pull_request` jobs. The rule precedes the runner.
- Certbot stays dry-run below production unless a staging DNS name is ever wanted.
- The container rung is fragile by nature (systemd-in-docker cgroup care); the incantation gets recorded, not rediscovered.
- Production deploys gain a precondition worth writing into the delivery flow once rung 2 exists: infra changes visit staging first.
