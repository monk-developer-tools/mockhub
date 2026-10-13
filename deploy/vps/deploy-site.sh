#!/usr/bin/env bash
# Publishes the static website from your machine to the VPS. Run from the repository root.
set -euo pipefail

HOST=${HOST:-root@168.231.79.163}

rsync -rltz --delete --no-owner --no-group website/ "$HOST:/var/www/mockhub/"
ssh "$HOST" 'find /var/www/mockhub -type d -exec chmod 755 {} + && find /var/www/mockhub -type f -exec chmod 644 {} +'
echo "Website published to $HOST:/var/www/mockhub"
