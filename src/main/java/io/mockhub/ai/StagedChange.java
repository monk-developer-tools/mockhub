package io.mockhub.ai;

public record StagedChange(ProposedChange change, String by, String at) {}
