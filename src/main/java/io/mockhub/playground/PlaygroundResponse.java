package io.mockhub.playground;

import java.util.List;

public record PlaygroundResponse(int status, List<Header> headers, String body, long size, boolean truncated, long durationMs) {}
