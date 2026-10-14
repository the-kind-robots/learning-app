# Infra Entry Points

Use this file to jump to the authoritative configuration files. Load only what you need.

## CI/CD

- `.github/workflows/integration.yml` — automatic PR and `master` test workflow.
- `.github/workflows/deploy-infra.yml` — manual infrastructure deployment workflow.
- `.github/workflows/deploy-app.yml` — manual application deployment workflow.
- `build.clj` — uberjar build and entrypoint.

## Production runtime

- `infra/production/usr/share/learning-app/admin-setup.sh`
- `infra/production/etc/systemd/system/learning-app-run.service`
- `infra/production/etc/systemd/system/learning-app-restart.path`
- `infra/production/etc/systemd/system/learning-app-restart.service`
- `infra/production/etc/systemd/system/learning-app-certbot.service`
- `infra/production/etc/systemd/system/learning-app-certbot.timer`
- `infra/production/etc/nginx/sites-available/learning-app.conf`
- `infra/production/DEBIAN/postinst`
- `infra/production/DEBIAN/postrm`

## Documentation

- `docs/ops/runbook.md`
- `docs/ops/server-configuration.md`
- `docs/ops/verification.md`
- `readme.md`
