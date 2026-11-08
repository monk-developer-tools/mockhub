package io.mockhub.bench;

record Latency(long count, double meanMicros, double p50Micros, double p90Micros, double p99Micros, double p999Micros, double maxMicros) {}
