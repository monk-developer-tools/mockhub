#!/usr/bin/env bash
set -euo pipefail

IMAGE=${IMAGE:-mockhub-bench:local}
SERVER_CPUS=${SERVER_CPUS:-2}
SERVER_MEMORY=${SERVER_MEMORY:-1g}
LOAD_CPUS=${LOAD_CPUS:-2}
CONNECTIONS=${CONNECTIONS:-32}
WARMUP=${WARMUP:-20}
DURATION=${DURATION:-60}
SOAK=${SOAK:-600}
ONLY=${ONLY:-}
LOAD_IMAGE=${LOAD_IMAGE:-eclipse-temurin:21-jre}
NETWORK=mockhub-bench
SERVER=mockhub-bench-server

cd "$(dirname "$0")/.."
OUT=${OUT:-build/benchmark/$(date -u +%Y%m%dT%H%M%SZ)}
mkdir -p "$OUT"
OUT=$(cd "$OUT" && pwd)

./gradlew -q shadowJar benchJar
if [ "${SKIP_IMAGE_BUILD:-}" != "true" ]; then docker build -q -t "$IMAGE" . > /dev/null; fi

cleanup() {
  [ -n "${SAMPLER:-}" ] && kill "$SAMPLER" 2>/dev/null && wait "$SAMPLER" 2>/dev/null || true
  if [ -n "${STARTED:-}" ]; then
    docker logs "$SERVER" > "$OUT/server.log" 2>&1 || true
    docker inspect "$SERVER" --format 'running={{.State.Running}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' > "$OUT/server-state.txt" 2>&1 || true
  fi
  docker rm -f -v "$SERVER" > /dev/null 2>&1 || true
  docker network rm "$NETWORK" > /dev/null 2>&1 || true
}
trap cleanup EXIT
cleanup
docker network create "$NETWORK" > /dev/null

docker run -d --name "$SERVER" --network "$NETWORK" --cpus "$SERVER_CPUS" --memory "$SERVER_MEMORY" \
  -e MOCKHUB_USERS='bench:bench-pass-1:admin' "$IMAGE" > /dev/null
STARTED=true

uptime > "$OUT/host-load.txt"
echo "time,memory,cpu" > "$OUT/server-stats.csv"
(
  while true; do
    docker stats --no-stream --format '{{.MemUsage}},{{.CPUPerc}}' "$SERVER" 2>/dev/null \
      | sed "s/^/$(date -u +%H:%M:%S),/" >> "$OUT/server-stats.csv" || true
    sleep 5
  done
) &
SAMPLER=$!

docker run --rm --network "$NETWORK" --cpus "$LOAD_CPUS" \
  -v "$PWD/build/libs:/bench:ro" -v "$OUT:/out" "$LOAD_IMAGE" \
  java -cp /bench/mockhub.jar:/bench/mockhub-bench.jar io.mockhub.bench.Benchmark \
  --base "http://$SERVER:8080" --user bench --password bench-pass-1 --connections "$CONNECTIONS" \
  --warmup "$WARMUP" --duration "$DURATION" --soak "$SOAK" ${ONLY:+--only "$ONLY"} --out /out/results.json \
  | tee "$OUT/results.md"

docker stats --no-stream --format '{{.MemUsage}},{{.CPUPerc}}' "$SERVER" | sed "s/^/$(date -u +%H:%M:%S),/" >> "$OUT/server-stats.csv"
uptime >> "$OUT/host-load.txt"
docker image inspect "$IMAGE" --format '{{.Size}}' > "$OUT/image-bytes.txt"
docker info --format '{{.NCPU}} CPUs, {{.MemTotal}} bytes, Docker {{.ServerVersion}}, {{.OperatingSystem}}' > "$OUT/host.txt"
echo "Results in $OUT"
