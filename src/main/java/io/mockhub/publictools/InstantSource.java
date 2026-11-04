package io.mockhub.publictools;

import io.mockhub.project.Project;

@FunctionalInterface
public interface InstantSource {
  InstantSeed build(Project project);
}
