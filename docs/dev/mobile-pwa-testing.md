# Mobile PWA Dev Access (Cloudflare Tunnel)

Use this guide to open your local dev app on a real mobile device over HTTPS.

The tunnel is a public front for the dev stand from `development-setup.md`: Cloudflare terminates TLS at `<name>.dev.sprecha.de` and forwards into the same local Nginx that serves `sprecha.localhost`.

## Team model

- Use one tunnel per developer.
- Use one hostname per developer, for example `<name>.dev.sprecha.de`.
- Do not share tunnel tokens between developers.

This avoids routing conflicts and makes ownership clear.

## Prerequisites

1. Local app stack works on your machine (`docs/dev/development-setup.md`).
2. `sprecha.de` DNS is managed by Cloudflare.
3. `cloudflared` installed.

macOS:

```bash
brew install cloudflared
```

Ubuntu/Debian:

```bash
sudo mkdir -p --mode=0755 /usr/share/keyrings
curl -fsSL https://pkg.cloudflare.com/cloudflare-public-v2.gpg | sudo tee /usr/share/keyrings/cloudflare-public-v2.gpg >/dev/null
echo 'deb [signed-by=/usr/share/keyrings/cloudflare-public-v2.gpg] https://pkg.cloudflare.com/cloudflared any main' | sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt-get update
sudo apt-get install -y cloudflared
```

## One-time setup (per developer)

1. Create a Cloudflare Tunnel in Zero Trust.
2. Add a public hostname route:
   - Hostname: `<name>.dev.sprecha.de`
   - Service type: `HTTP`
   - Service URL: `127.0.0.1:80`
   - Origin settings:
     - `HTTP Host Header = sprecha.localhost`
3. Install service with your tunnel token:

```bash
sudo cloudflared service install <TUNNEL_TOKEN>
sudo systemctl enable --now cloudflared
```

4. Verify connector is healthy:

```bash
systemctl status cloudflared
journalctl -u cloudflared -f
```

## Daily workflow

1. Start local app stack.
2. Ensure `cloudflared` service is running.
3. Open `https://<name>.dev.sprecha.de` on phone.
4. Install to home screen and test from PWA icon.

## Quick troubleshooting

- `Error 1033`: connector is not connected. Check `systemctl status cloudflared` and logs.
- `502`/`525`: route origin settings are wrong. Recheck `No TLS Verify` and `HTTP Host Header`.
- PWA seems stale: clear site data/service worker and reinstall from the dev hostname.
