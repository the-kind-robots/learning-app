# The production shape boots in a container before it boots on the server

## Why

Five infra PRs in one day ended their verification with "proven on the next real deploy" (#199, #200, #209, #211, plus the deploy transport), and the #168 class of bug lived for months because nothing exists between "dev from source" and production. ADR-0010 names the missing rung: a systemd container built from the actual deb, answering "does the production shape hold?" in minutes.

## What changes

`infra/staging/` gains a runnable rung: `run.sh` builds the deb exactly as CI does, boots a systemd container (Ubuntu noble, the deb's real target family), stages the credentials an operator would create, pre-places a legacy `app.db`, installs the deb, delivers the jar the runbook way, and runs `smoke.sh` — one PASS/FAIL line per claim those PRs previously shipped as checklists: units active under their sandboxes, nginx answering, the auth boundary, migrations, #209's adoption, #211's backup archive carrying both stores. Certbot and remote borg stay dry; the kernel is shared — rung 3 (#215) owns reality.

## Impact

New `infra/staging/` (Dockerfile, run.sh, smoke.sh, README.md); no production files change. New capability spec `staging-environment`. CI wiring is a separate decision per #214.
