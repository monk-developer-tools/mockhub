package io.mockhub.ha;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

public final class LeaderLease implements AutoCloseable {
  private final FileChannel channel;
  private final FileLock lock;

  private LeaderLease(FileChannel channel, FileLock lock) {
    this.channel = channel;
    this.lock = lock;
  }

  public static Optional<LeaderLease> tryAcquire(Path file) {
    try {
      Files.createDirectories(file.getParent());
      FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
      FileLock lock;
      try {
        lock = channel.tryLock();
      } catch (OverlappingFileLockException e) {
        lock = null;
      }
      if (lock == null) {
        channel.close();
        return Optional.empty();
      }
      return Optional.of(new LeaderLease(channel, lock));
    } catch (IOException e) {
      throw new UncheckedIOException("Can't use the lock file " + file, e);
    }
  }

  @Override
  public void close() {
    try {
      if (lock.isValid()) lock.release();
      channel.close();
    } catch (IOException ignored) {
      return;
    }
  }
}
