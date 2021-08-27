package com.android.tools.build.apkzlib.bytestorage;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.TreeSet;
import javax.annotation.Nullable;

/**
 * A tracker that keeps a list of the last-recently-used objects of type {@code T}. The tracker
 * doesn't define what LRU means, it has a method, {@link #access(Object)} that marks the object as
 * being accessed and moves it to the top of the queue.
 *
 * <p>This implementation is O(log(N)) on all operations.
 *
 * <p>Implementation note: we don't keep track of time. Instead we use a counter that is incremented
 * every time a new access is done or a new object is tracked. Because of this, each access time is
 * unique for each object (although it will change after each access).
 */
class LruTracker<T> {

  /** Maps each object to its unique access time and vice-versa. */
  private final BiMap<T, Integer> objectToAccessTime;

  /**
   * Ordered set of all object's access times. This set has the same contents as {@code
   * objectToAccessTime.value()}. It is sorted from the highest access time (newest) to the lowest
   * access time (oldest).
   */
  private final TreeSet<Integer> accessTimes;

  /** Next access time to use for tracking or accessing. */
  private int currentTime;

  /** Creates a new tracker without any objects. */
  LruTracker() {
    currentTime = 1;
    objectToAccessTime = HashBiMap.create();
    accessTimes = new TreeSet<>((i0, i1) -> i1 - i0);
  }

  /** Starts tracking an object. This object's will be the most recently used. */
  synchronized void track(T object) {
    Preconditions.checkState(!objectToAccessTime.containsKey(object));
    objectToAccessTime.put(object, currentTime);
    accessTimes.add(currentTime);
    currentTime++;
  }

  /** Stops tracking an object. */
  synchronized void untrack(T object) {
    Preconditions.checkState(objectToAccessTime.containsKey(object));
    accessTimes.remove(objectToAccessTime.get(object));
    objectToAccessTime.remove(object);
  }

  /** Marks the given object as having been accessed promoting it as the most recently used. */
  synchronized void access(T object) {
    untrack(object);
    track(object);
  }

  /**
   * Obtains the position of an object in the queue. It will be {@code 0} for the most recently used
   * object.
   */
  synchronized int positionOf(T object) {
    Preconditions.checkState(objectToAccessTime.containsKey(object));
    int lastAccess = objectToAccessTime.get(object);
    return accessTimes.headSet(lastAccess).size();
  }

  /**
   * Obtains the last element, the one last accessed earliest. Will return empty if there are no
   * objects being tracked.
   */
  @Nullable
  synchronized T last() {
    if (accessTimes.isEmpty()) {
      return null;
    }

    return objectToAccessTime.inverse().get(accessTimes.last());
  }
}
