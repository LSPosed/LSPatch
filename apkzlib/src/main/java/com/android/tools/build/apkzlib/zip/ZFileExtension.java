/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.build.apkzlib.zip;

import com.android.tools.build.apkzlib.utils.IOExceptionRunnable;
import java.io.IOException;
import javax.annotation.Nullable;

/**
 * An extension of a {@link ZFile}. Extensions are notified when files are open, updated, closed and
 * when files are added or removed from the zip. These notifications are received after the zip has
 * been updated in memory for open, when files are added or removed and when the zip has been
 * updated on disk or closed.
 *
 * <p>An extension is also notified before the file is updated, allowing it to modify the file
 * before the update happens. If it does, then all extensions are notified of the changes on the zip
 * file. Because the order of the notifications is preserved, all extensions are notified in the
 * same order. For example, if two extensions E1 and E2 are registered and they both add a file at
 * update time, this would be the flow:
 *
 * <ul>
 *   <li>E1 receives {@code beforeUpdate} notification.
 *   <li>E1 adds file F1 to the zip (notifying the addition is suspended because another
 *       notification is in progress).
 *   <li>E2 receives {@code beforeUpdate} notification.
 *   <li>E2 adds file F2 to the zip (notifying the addition is suspended because another
 *       notification is in progress).
 *   <li>E1 is notified F1 was added.
 *   <li>E2 is notified F1 was added.
 *   <li>E1 is notified F2 was added.
 *   <li>E2 is notified F2 was added.
 *   <li>(zip file is updated on disk)
 *   <li>E1 is notified the zip was updated.
 *   <li>E2 is notified the zip was updated.
 * </ul>
 *
 * <p>An extension should not modify the zip file when notified of changes. If allowed, this would
 * break event notification order in case multiple extensions are registered with the zip file. To
 * allow performing changes to the zip file, all notification method return a {@code
 * IOExceptionRunnable} that is invoked when {@link ZFile} has finished notifying all extensions.
 */
public abstract class ZFileExtension {

  /**
   * The zip file has been open and the zip's contents have been read. The default implementation
   * does nothing and returns {@code null}.
   *
   * @return an optional runnable to run when notification of all listeners has ended
   * @throws IOException failed to process the event
   */
  @Nullable
  public IOExceptionRunnable open() throws IOException {
    return null;
  }

  /**
   * The zip will be updated. This method allows the extension to register changes to the zip file
   * before the file is written. The default implementation does nothing and returns {@code null}.
   *
   * <p>After this notification is received, the extension will receive further {@link
   * #added(StoredEntry, StoredEntry)} and {@link #removed(StoredEntry)} notifications if it or
   * other extensions add or remove files before update.
   *
   * <p>When no more files are updated, the {@link #entriesWritten()} notification is sent.
   *
   * @return an optional runnable to run when notification of all listeners has ended
   * @throws IOException failed to process the event
   */
  @Nullable
  public IOExceptionRunnable beforeUpdate() throws IOException {
    return null;
  }

  /**
   * This notification is sent when all entries have been written in the file but the central
   * directory and the EOCD have not yet been written. No entries should be added, removed or
   * updated during this notification. If this method forces an update of either the central
   * directory or EOCD, then this method will be invoked again for all extensions with the new
   * central directory and EOCD.
   *
   * <p>After this notification, {@link #updated()} is sent.
   *
   * @throws IOException failed to process the event
   */
  public void entriesWritten() throws IOException {}

  /**
   * The zip file has been updated on disk. The default implementation does nothing.
   *
   * @throws IOException failed to perform update tasks
   */
  public void updated() throws IOException {}

  /**
   * The zip file has been closed. Note that if {@link ZFile#close()} requires that the zip file be
   * updated (because it had in-memory changes), {@link #updated()} will be called before this
   * method. The default implementation does nothing.
   */
  public void closed() {}

  /**
   * A new entry has been added to the zip, possibly replacing an entry in there. The default
   * implementation does nothing and returns {@code null}.
   *
   * @param entry the entry that was added
   * @param replaced the entry that was replaced, if any
   * @return an optional runnable to run when notification of all listeners has ended
   */
  @Nullable
  public IOExceptionRunnable added(StoredEntry entry, @Nullable StoredEntry replaced) {
    return null;
  }

  /**
   * An entry has been removed from the zip. This method is not invoked for entries that have been
   * replaced. Those entries are notified using <em>replaced</em> in {@link #added(StoredEntry,
   * StoredEntry)}. The default implementation does nothing and returns {@code null}.
   *
   * @param entry the entry that was deleted
   * @return an optional runnable to run when notification of all listeners has ended
   */
  @Nullable
  public IOExceptionRunnable removed(StoredEntry entry) {
    return null;
  }
}
