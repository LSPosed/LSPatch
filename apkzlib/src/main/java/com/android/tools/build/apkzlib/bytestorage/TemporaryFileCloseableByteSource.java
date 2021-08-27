package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableDelegateByteSource;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;

/**
 * Closeable byte source that uses a temporary file to store its contents. The file is deleted when
 * the byte source is closed.
 */
class TemporaryFileCloseableByteSource extends CloseableDelegateByteSource {

  /** Temporary file backing the byte source. */
  private final TemporaryFile temporaryFile;

  /** Callback to notify when the byte source is closed. */
  private final Runnable closeCallback;

  /**
   * Creates a new byte source based on the given file. The provided callback is executed when the
   * source is deleted. There is no guarantee about which thread invokes the callback (it is the
   * thread that closes the source).
   */
  TemporaryFileCloseableByteSource(File file, Runnable closeCallback) {
    super(Files.asByteSource(file), file.length());
    temporaryFile = new TemporaryFile(file);
    this.closeCallback = closeCallback;
  }

  @Override
  protected synchronized void innerClose() throws IOException {
    super.innerClose();
    temporaryFile.close();
    closeCallback.run();
  }
}
