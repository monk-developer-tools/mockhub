package io.mockhub.playground;

public record PlaygroundEntry(String id, String at, PlaygroundRequest request, PlaygroundResponse response, String error, boolean local) {}
