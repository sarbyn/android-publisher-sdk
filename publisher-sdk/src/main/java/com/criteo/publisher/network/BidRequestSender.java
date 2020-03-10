package com.criteo.publisher.network;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import com.criteo.publisher.Util.LoggingUtil;
import com.criteo.publisher.Util.NetworkResponseListener;
import com.criteo.publisher.model.CacheAdUnit;
import com.criteo.publisher.model.CdbRequest;
import com.criteo.publisher.model.CdbRequestFactory;
import com.criteo.publisher.model.CdbResponse;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.RemoteConfigRequest;
import com.criteo.publisher.model.RemoteConfigRequestFactory;
import com.criteo.publisher.model.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import org.json.JSONObject;

public class BidRequestSender {

  @NonNull
  private final CdbRequestFactory cdbRequestFactory;

  @NonNull
  private final RemoteConfigRequestFactory remoteConfigRequestFactory;

  @NonNull
  private final PubSdkApi api;

  @NonNull
  private final LoggingUtil loggingUtil;

  @NonNull
  private final Executor executor;

  @NonNull
  @GuardedBy("lock")
  private final Map<CacheAdUnit, Future<?>> pendingTasks;
  private final Object pendingTasksLock = new Object();

  public BidRequestSender(
      @NonNull CdbRequestFactory cdbRequestFactory,
      @NonNull RemoteConfigRequestFactory remoteConfigRequestFactory,
      @NonNull PubSdkApi api,
      @NonNull LoggingUtil loggingUtil,
      @NonNull Executor executor) {
    this.cdbRequestFactory = cdbRequestFactory;
    this.remoteConfigRequestFactory = remoteConfigRequestFactory;
    this.api = api;
    this.loggingUtil = loggingUtil;
    this.executor = executor;
    this.pendingTasks = new ConcurrentHashMap<>();
  }

  @VisibleForTesting
  Set<CacheAdUnit> getPendingTaskAdUnits() {
    return pendingTasks.keySet();
  }

  /**
   * Asynchronously send a remote config request and update the given config.
   * <p>
   * If no error occurs during the request, the given configuration is updated. Else, it is left
   * unchanged.
   *
   * @param configToUpdate configuration to update after request
   */
  public void sendRemoteConfigRequest(@NonNull Config configToUpdate) {
    executor.execute(new RemoteConfigCall(configToUpdate));
  }

  /**
   * Asynchronously send a bid request with the given requested ad units.
   * <p>
   * The given listener is notified before and after the call was made.
   * <p>
   * When an ad unit is sent for request, it is considered as pending until the end of its request
   * (successful or not). While an ad unit is pending, it cannot be requested again. So if in given
   * ones, some are pending, they will be ignored from the request. If all given ad units are
   * pending, then no call is done and listener is not notified.
   *
   * @param adUnits  ad units to request
   * @param listener listener to notify
   */
  public void sendBidRequest(
      @NonNull List<CacheAdUnit> adUnits,
      @NonNull NetworkResponseListener listener) {
    List<CacheAdUnit> requestedAdUnits = new ArrayList<>(adUnits);
    FutureTask<Void> task;

    synchronized (pendingTasksLock) {
      requestedAdUnits.removeAll(pendingTasks.keySet());
      if (requestedAdUnits.isEmpty()) {
        return;
      }

      task = createCdbCallTask(requestedAdUnits, listener);

      for (CacheAdUnit requestedAdUnit : requestedAdUnits) {
        pendingTasks.put(requestedAdUnit, task);
      }
    }

    try {
      executor.execute(task);
      task = null;
    } finally {
      if (task != null) {
        // If an exception was thrown when scheduling the task, then we remove the ad unit from the
        // pending tasks.
        removePendingTasksWithAdUnits(requestedAdUnits);
      }
    }
  }

  @NonNull
  private FutureTask<Void> createCdbCallTask(
      @NonNull List<CacheAdUnit> requestedAdUnits,
      @NonNull NetworkResponseListener responseListener) {
    CdbCall task = new CdbCall(requestedAdUnits, responseListener);

    Runnable withRemovedPendingTasksAfterExecution = new Runnable() {
      @Override
      public void run() {
        try {
          task.run();
        } finally {
          removePendingTasksWithAdUnits(requestedAdUnits);
        }
      }
    };

    return new FutureTask<>(withRemovedPendingTasksAfterExecution, null);
  }

  private void removePendingTasksWithAdUnits(List<CacheAdUnit> adUnits) {
    synchronized (pendingTasksLock) {
      pendingTasks.keySet().removeAll(adUnits);
    }
  }

  /**
   * Attempts to cancel all pending tasks of bid request.
   */
  public void cancelAllPendingTasks() {
    synchronized (pendingTasksLock) {
      for (Future<?> task : pendingTasks.values()) {
        task.cancel(true);
      }
      pendingTasks.clear();
    }
  }

  private class CdbCall implements Runnable {

    private final String TAG = CdbCall.class.getSimpleName();

    @NonNull
    private final List<CacheAdUnit> requestedAdUnits;

    @NonNull
    private final NetworkResponseListener listener;

    private CdbCall(
        @NonNull List<CacheAdUnit> requestedAdUnits,
        @NonNull NetworkResponseListener listener) {
      this.requestedAdUnits = requestedAdUnits;
      this.listener = listener;
    }

    @Override
    public void run() {
      try {
        doRun();
      } catch (Throwable tr) {
        Log.e(TAG, "Internal error", tr);
      }
    }

    private void doRun() throws Exception {
      CdbRequest cdbRequest = cdbRequestFactory.createRequest(requestedAdUnits);
      String userAgent = cdbRequestFactory.getUserAgent().get();
      CdbResponse cdbResponse = api.loadCdb(cdbRequest, userAgent);
      logCdbResponse(cdbResponse);

      if (cdbResponse != null) {
        listener.setCacheAdUnits(cdbResponse.getSlots());
        listener.setTimeToNextCall(cdbResponse.getTimeToNextCall());
      }
    }

    private void logCdbResponse(@Nullable CdbResponse response) {
      if (loggingUtil.isLoggingEnabled() && response != null && response.getSlots().size() > 0) {
        StringBuilder builder = new StringBuilder();
        for (Slot slot : response.getSlots()) {
          builder.append(slot.toString());
          builder.append("\n");
        }
        Log.d(TAG, builder.toString());
      }
    }
  }

  private class RemoteConfigCall implements Runnable {

    private final String TAG = RemoteConfigCall.class.getSimpleName();

    @NonNull
    private final Config configToUpdate;

    private RemoteConfigCall(@NonNull Config configToUpdate) {
      this.configToUpdate = configToUpdate;
    }

    @Override
    public void run() {
      try {
        doRun();
      } catch (Throwable tr) {
        Log.e(TAG, "Internal error", tr);
      }
    }

    private void doRun() {
      RemoteConfigRequest request = remoteConfigRequestFactory.createRequest();
      JSONObject configResult = api.loadConfig(request);

      if (configResult != null) {
        configToUpdate.refreshConfig(configResult);
      }
    }
  }
}