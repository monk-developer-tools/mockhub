#!/usr/bin/env bash
# Builds and (re)starts the MockHub demo container on the VPS. Run on the VPS as root.
# Touches only: /opt/mockhub, the "mockhub" container and the "mockhub:latest" image.
set -euo pipefail

REPO=https://github.com/monk-developer-tools/mockhub.git
BASE=/opt/mockhub
PORT=8095
PUBLIC_URL=https://mockhub.devops-monk.com

mkdir -p "$BASE/data"
chown -R 65532:65532 "$BASE/data"

if [ ! -f "$BASE/mockhub.env" ]; then
  admin_password=$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)
  umask 077
  cat > "$BASE/mockhub.env" <<ENV
MOCKHUB_PUBLIC_URL=$PUBLIC_URL
MOCKHUB_USERS=admin:$admin_password:admin,demo:demo:viewer
MOCKHUB_JOURNAL_MAX_ENTRIES=2000
ENV
  echo "Created $BASE/mockhub.env (admin password inside)."
fi

if [ -d "$BASE/src/.git" ]; then
  git -C "$BASE/src" fetch --quiet origin main
  git -C "$BASE/src" reset --quiet --hard origin/main
else
  git clone --quiet --depth 1 "$REPO" "$BASE/src"
fi

nice -n 10 docker build --pull --quiet -t mockhub:latest "$BASE/src"

docker rm -f mockhub >/dev/null 2>&1 || true
docker run -d --name mockhub \
  --restart unless-stopped \
  -p 127.0.0.1:$PORT:8080 \
  -v "$BASE/data:/data" \
  --env-file "$BASE/mockhub.env" \
  --memory 768m --cpus 1 \
  mockhub:latest >/dev/null

healthy=false
for _ in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:$PORT/__admin/health" >/dev/null 2>&1; then
    healthy=true
    break
  fi
  sleep 1
done
if [ "$healthy" != true ]; then
  echo "MockHub did not become healthy; check: docker logs mockhub" >&2
  exit 1
fi
echo "MockHub is up on 127.0.0.1:$PORT"

api="http://127.0.0.1:$PORT/__admin/mockhub/api"
admin=$(grep '^MOCKHUB_USERS=' "$BASE/mockhub.env" | cut -d= -f2- | tr ',' '\n' | grep ':admin$' | head -1 | sed 's/:admin$//' || true)
if [ -z "$admin" ]; then
  echo "No admin in MOCKHUB_USERS; skipping example installs."
  exit 0
fi
as_admin() { printf 'user = "%s"\n' "$admin" | curl -fsS -K - "$@"; }
installed=$(as_admin "$api/projects" | jq -r '.[].slug')
for example in $(as_admin "$api/examples" | jq -r '.[] | "\(.id)=\(.slug)"'); do
  id=${example%%=*}
  slug=${example#*=}
  if ! grep -qx "$slug" <<<"$installed"; then
    as_admin -X POST "$api/examples/$id/install" >/dev/null && echo "Installed example $id at /$slug"
  fi
done
exit 0
