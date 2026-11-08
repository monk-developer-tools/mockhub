#!/usr/bin/env bash
set -euo pipefail

HOST=${HOST:-root@168.231.79.163}
IMAGE=${IMAGE:-curlimages/curl:8.10.1}

scp -q deploy/vps/status/check.sh "$HOST:/tmp/mockhub-status-check.sh"
ssh "$HOST" IMAGE="$IMAGE" 'bash -s' <<'REMOTE'
set -euo pipefail
install -d -m 755 /opt/mockhub/status
install -m 755 /tmp/mockhub-status-check.sh /opt/mockhub/status/check.sh
rm -f /tmp/mockhub-status-check.sh
install -d -m 755 /var/www/mockhub/status
install -d -m 755 -o 100 -g 101 /var/www/mockhub/status/data
docker pull -q "$IMAGE" > /dev/null
docker rm -f mockhub-status > /dev/null 2>&1 || true
docker run -d --name mockhub-status --restart unless-stopped --memory 64m --cpus 0.25 \
  --log-opt max-size=1m --log-opt max-file=2 \
  -v /opt/mockhub/status/check.sh:/check.sh:ro -v /var/www/mockhub/status/data:/data \
  --entrypoint sh "$IMAGE" /check.sh > /dev/null
sleep 20
docker ps --filter name=mockhub-status --format '{{.Names}} {{.Status}}'
cat /var/www/mockhub/status/data/status.json
REMOTE
