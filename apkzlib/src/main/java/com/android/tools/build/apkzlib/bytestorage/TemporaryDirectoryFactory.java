package com.android.tools.build.apkzlib.bytestorage;

import java.io.File;
import java.io.IOException;

/**
 * Factory that creates temporary directories. {@link
 * TemporaryDirectory#newSystemTemporaryDirectory()} conforms to this interface.
 */
public interface TemporaryDirectoryFactory {

  /**
   * Creates a new temporary directory.
   *
   * @return the new temporary directory that should be closed when finished
   * @throws IOException failed to create the temporary directory
   */
  TemporaryDirectory make() throws IOException;

  /**
   * Obtains a factory that creates temporary directories using {@link
   * TemporaryDirectory#fixed(File)}.
   *
   * @param directory the directory where all temporary files will be created
   * @return a factory that creates instances of {@link TemporaryDirectory} that creates all files
   *     inside {@code directory}
   */
  static TemporaryDirectoryFactory fixed(File directory) {
    return () -> TemporaryDirectory.fixed(directory);
  }
}
