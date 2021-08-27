package com.android.tools.build.apkzlib.bytestorage;

import com.google.common.annotations.VisibleForTesting;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A temporary directory is a directory that creates temporary files. Upon close, all temporary
 * files are removed. Whether the directory itself is removed is dependent on the actual
 * implementation.
 */
public interface TemporaryDirectory extends Closeable {

  /**
   * Creates a new file in the directory. This method returns a new file that deleted, recreated,
   * read and written freely by the caller. No assumptions are made on the contents of this file
   * except that it will be deleted it if it still exists when the temporary directory is closed.
   */
  File newFile() throws IOException;

  /** Obtains the directory, only useful for tests. */
  @VisibleForTesting // private otherwise.
  File getDirectory();

  /**
   * Creates a new temporary directory in the system's temporary directory. All files created will
   * be created in this directory. The directory will be deleted (as long as all the files in it)
   * when closed.
   */
  static TemporaryDirectory newSystemTemporaryDirectory() throws IOException {
    Path tempDir = Files.createTempDirectory("tempdir_");
    TemporaryFile tempDirFile = new TemporaryFile(tempDir.toFile());
    return new TemporaryDirectory() {
      @Override
      public File newFile() throws IOException {
        return Files.createTempFile(tempDir, "temp_", ".data").toFile();
      }

      @Override
      public File getDirectory() {
        return tempDir.toFile();
      }

      @Override
      public void close() throws IOException {
        tempDirFile.close();
      }
    };
  }

  /**
   * Creates a new temporary directory that uses a fixed directory.
   *
   * @param directory the directory that will be returned; this directory won't be deleted when the
   *     {@link TemporaryDirectory} objects are closed
   * @return a {@link TemporaryDirectory} that will create files in {@code directory}
   */
  static TemporaryDirectory fixed(File directory) {
    return new TemporaryDirectory() {
      @Override
      public File newFile() throws IOException {
        return Files.createTempFile(directory.toPath(), "temp_", ".data").toFile();
      }

      @Override
      public File getDirectory() {
        return directory;
      }

      @Override
      public void close() throws IOException {}
    };
  }
}
