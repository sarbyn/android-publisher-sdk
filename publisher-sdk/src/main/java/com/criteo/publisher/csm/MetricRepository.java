package com.criteo.publisher.csm;

import android.support.annotation.NonNull;
import java.util.Collection;

public abstract class MetricRepository {

  /**
   * Atomically update the metric matching the given id with the given updater.
   * <p>
   * If no metric match the given id, then a new empty one is created and updated accordingly.
   * <p>
   * When multiple threads attempt updates, the update function may be called multiple times.
   * <p>
   * If the repository is full, it may, silently, skip creating new metric or evict old data.
   *
   * @param impressionId ID of the metric to update
   * @param updater function representing the update to apply
   */
  abstract void updateById(@NonNull String impressionId, @NonNull MetricUpdater updater);

  /**
   * Atomically move the metric matching the given id with the given mover.
   * <p>
   * The metric is read, deleted and moved with the given mover. If a final move is unsuccessful,
   * the operation is rollback.
   *
   * As the metric is first deleted, and then moved, this means that in case of crashes, the data
   * may be lost. This is expected since no metric duplications are allowed.
   *
   * @param impressionId ID of the metric to move
   * @param mover the definition of the move to handle
   */
  abstract void moveById(@NonNull String impressionId, @NonNull MetricMover mover);

  /**
   * Returns all stored metric into this repository.
   * <p>
   * Individual metrics are read atomically, however the overall metrics are not read at the same
   * time and hence is not globally atomic.
   *
   * @return all stored metrics
   */
  @NonNull
  abstract Collection<Metric> getAllStoredMetrics();

  interface MetricUpdater {

    void update(@NonNull Metric.Builder metricBuilder);
  }
}
