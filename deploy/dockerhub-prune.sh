#!/usr/bin/env bash
# Deletes old sha-* tags of the MockHub image on Docker Hub, keeping "latest", the image it points to and the newest N.
# Usage: DOCKERHUB_TOKEN=... deploy/dockerhub-prune.sh [--keep N] [--yes]
# Without --yes it only lists what it would delete. The token needs Read, Write, Delete access.
set -euo pipefail

REPO=${REPO:-abhaypratap/mockhub}
USERNAME=${DOCKERHUB_USERNAME:-${REPO%%/*}}
KEEP=0
APPLY=false

while [ $# -gt 0 ]; do
  case "$1" in
    --keep) KEEP=$2; shift 2 ;;
    --yes) APPLY=true; shift ;;
    *) echo "Unknown option $1" >&2; exit 2 ;;
  esac
done

TAGS_JSON=$(curl -fsS "https://hub.docker.com/v2/repositories/$REPO/tags?page_size=100")

DELETE=$(KEEP="$KEEP" python3 -c '
import json, os, sys
tags = json.load(sys.stdin)["results"]
latest = next((t["digest"] for t in tags if t["name"] == "latest"), None)
shas = sorted((t for t in tags if t["name"].startswith("sha-")), key=lambda t: t["last_updated"], reverse=True)
kept = 0
for t in shas:
    if t["digest"] == latest:
        continue
    if kept < int(os.environ["KEEP"]):
        kept += 1
        continue
    print(t["name"])
' <<< "$TAGS_JSON")

if [ -z "$DELETE" ]; then
  echo "Nothing to delete."
  exit 0
fi

echo "Tags to delete from $REPO:"
printf '  %s\n' $DELETE

if [ "$APPLY" != true ]; then
  echo "Dry run. Add --yes to delete them."
  exit 0
fi

if [ -z "${DOCKERHUB_TOKEN:-}" ]; then
  read -r -s -p "Docker Hub access token for $USERNAME: " DOCKERHUB_TOKEN
  echo
fi

JWT=$(curl -fsS -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$DOCKERHUB_TOKEN\"}" \
  https://hub.docker.com/v2/users/login | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

for tag in $DELETE; do
  code=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE -H "Authorization: Bearer $JWT" \
    "https://hub.docker.com/v2/repositories/$REPO/tags/$tag/")
  echo "  $tag: $([ "$code" = 204 ] && echo deleted || echo "failed (HTTP $code)")"
done
