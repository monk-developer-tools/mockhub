package io.mockhub.ext;

import com.github.tomakehurst.wiremock.extension.StubLifecycleListener;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

public final class PersistenceListener implements StubLifecycleListener {
  @Override
  public String getName() {
    return "mockhub-persistence";
  }

  @Override
  public void beforeStubCreated(StubMapping stub) {
    if (stub.isPersistent() == null) {
      stub.setPersistent(true);
    }
  }

  @Override
  public void beforeStubEdited(StubMapping oldStub, StubMapping newStub) {
    if (newStub.isPersistent() == null) {
      newStub.setPersistent(oldStub.isPersistent() == null || oldStub.isPersistent());
    }
  }
}
