package com.android.tools.build.apkzlib.bytestorage;

import com.android.tools.build.apkzlib.zip.utils.CloseableByteSource;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Output stream that creates a {@link CloseableByteSource} from the data that was written to it.
 * Calling {@link #close} is optional as {@link #build()} will also close the output stream.
 */
public abstract class CloseableByteSourceFromOutputStreamBuilder extends OutputStream {

  /**
   * Creates the source from the data that has been written to the stream. No more data can be
   * written to the output stream after this method has been called.
   *
   * @return a source that will provide the data that was written to the stream before this method
   *     is invoked; where this data is stored is not specified by this interface
   * @throws IOException failed to build the byte source
   */
  public abstract CloseableByteSource build() throws IOException;
}
