package io.mockhub.callout;

import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ServeEventListener;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class CalloutListener implements ServeEventListener {
  public static final String NAME = "mockhub-callout";

  private final CalloutService callouts;
  private final ScheduledExecutorService scheduler;

  public CalloutListener(CalloutService callouts, ScheduledExecutorService scheduler) {
    this.callouts = callouts;
    this.scheduler = scheduler;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public boolean applyGlobally() {
    return false;
  }

  @Override
  public void afterComplete(ServeEvent event, Parameters parameters) {
    CalloutSpec spec = CalloutSpec.from(parameters);
    scheduler.schedule(() -> callouts.call(spec, event, CalloutService.ASYNC), spec.delayMs(), TimeUnit.MILLISECONDS);
  }
}
