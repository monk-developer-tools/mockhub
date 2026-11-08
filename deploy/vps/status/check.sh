#!/bin/sh
set -u

BASE=${BASE_URL:-https://mockhub.devops-monk.com}
OUT=${OUT_DIR:-/data}
INTERVAL=${INTERVAL:-300}
KEEP=${KEEP:-2016}
TIMEOUT=${TIMEOUT:-15}

probe() {
  body=$(mktemp)
  result=$(curl -s -o "$body" -w '%{http_code} %{time_total}' --max-time "$TIMEOUT" -H 'User-Agent: mockhub-status' "$1" 2>/dev/null) || result="000 0"
  code=$(echo "${result%% *}" | awk '{ printf "%d", $1 + 0 }')
  ms=$(echo "${result#* }" | awk '{ printf "%d", $1 * 1000 }')
  ok=0
  if [ "$code" = "$2" ] && grep -q "$3" "$body"; then ok=1; fi
  rm -f "$body"
  echo "$ok $code $ms"
}

entry() {
  set -- $2
  printf '{"id":"%s","ok":%s,"status":%s,"ms":%s}' "$1" "$([ "$2" = 1 ] && echo true || echo false)" "$3" "$4"
}

check_once() {
  mkdir -p "$OUT"
  now=$(date -u +%s)
  at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  app=$(probe "$BASE/__admin/health" 200 '"healthy"')
  website=$(probe "$BASE/" 200 'MockHub')
  tools=$(probe "$BASE/__public/echo" 200 '"method"')
  printf '{"checkedAt":"%s","intervalSeconds":%s,"checks":[%s,%s,%s]}\n' "$at" "$INTERVAL" \
    "$(entry app "app $app")" "$(entry website "website $website")" "$(entry tools "tools $tools")" > "$OUT/.status.json"
  mv "$OUT/.status.json" "$OUT/status.json"
  history="$OUT/history.csv"
  [ -f "$history" ] || echo "time,app,app_ms,website,website_ms,tools,tools_ms" > "$history"
  set -- $app $website $tools
  echo "$now,$1,$3,$4,$6,$7,$9" >> "$history"
  { head -n 1 "$history"; tail -n +2 "$history" | tail -n "$KEEP"; } > "$OUT/.history.csv"
  mv "$OUT/.history.csv" "$history"
}

if [ "${1:-}" = "--once" ]; then
  check_once
  exit 0
fi

while true; do
  check_once
  sleep "$INTERVAL"
done
