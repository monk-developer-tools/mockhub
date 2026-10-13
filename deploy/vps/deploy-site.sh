#!/usr/bin/env bash
# Publishes the static website from your machine to the VPS. Run from the repository root.
set -euo pipefail

HOST=${HOST:-root@168.231.79.163}

rsync -az --delete --chmod=D755,F644 website/ "$HOST:/var/www/mockhub/"
echo "Website published to $HOST:/var/www/mockhub"
