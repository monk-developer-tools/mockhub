package io.mockhub.journal;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.store.DefaultStores;
import com.github.tomakehurst.wiremock.store.RequestJournalStore;

public final class MockHubStores extends DefaultStores {
  private final RequestJournalStore journal;

  public MockHubStores(FileSource fileRoot, int maxJournalEntries, int maxJournalMb) {
    super(fileRoot);
    this.journal = new BoundedRequestJournalStore(maxJournalEntries, maxJournalMb * 1024L * 1024L);
  }

  @Override
  public RequestJournalStore getRequestJournalStore() {
    return journal;
  }
}
