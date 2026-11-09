package io.mockhub.environment;

import java.util.Optional;

public final class EnvironmentScope {
  private final ThreadLocal<Environment> current = new ThreadLocal<>();

  public void enter(Environment environment) {
    if (environment == null) current.remove();
    else current.set(environment);
  }

  public Optional<Environment> current() {
    return Optional.ofNullable(current.get());
  }
}
