# MockHub on a shared VPS

How mockhub.devops-monk.com runs on a VPS that already hosts other sites. Everything MockHub owns:

| What | Where |
|---|---|
| nginx site | `/etc/nginx/sites-available/mockhub.devops-monk.com` (+ symlink in `sites-enabled`) |
| Website | `/var/www/mockhub` |
| Demo app | container `mockhub`, image `mockhub:latest`, bound to `127.0.0.1:8095` |
| Demo data and secrets | `/opt/mockhub/data`, `/opt/mockhub/mockhub.env` |
| TLS | `certbot --nginx -d mockhub.devops-monk.com` |

nginx serves the website pages (`/`, `/docs/`, `/assets/`) from disk and proxies everything else to the demo, so the UI
lives at `/__admin/mockhub/ui/` and the example mocks at paths like `/example-petstore/pets`.

## First install

```bash
scp deploy/vps/mockhub.devops-monk.com.conf root@VPS:/etc/nginx/sites-available/mockhub.devops-monk.com
ssh root@VPS 'ln -s /etc/nginx/sites-available/mockhub.devops-monk.com /etc/nginx/sites-enabled/ && nginx -t && systemctl reload nginx'
deploy/vps/deploy-site.sh
ssh root@VPS 'bash -s' < deploy/vps/deploy-app.sh
ssh root@VPS 'certbot --nginx -d mockhub.devops-monk.com --non-interactive --redirect'
```

## Updates

```bash
deploy/vps/deploy-site.sh                       # website
ssh root@VPS 'bash -s' < deploy/vps/deploy-app.sh  # demo app (rebuilds from main)
```

The admin password for the demo is in `/opt/mockhub/mockhub.env` on the VPS. Visitors sign in as `demo` / `demo`
(read-only).
