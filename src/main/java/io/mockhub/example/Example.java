package io.mockhub.example;

import java.util.List;

public record Example(String id, String name, String slug, String description, List<String> tags, String file) {}
