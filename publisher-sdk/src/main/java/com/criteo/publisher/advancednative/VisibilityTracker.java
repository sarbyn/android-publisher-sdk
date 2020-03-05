package com.criteo.publisher.advancednative;

import android.support.annotation.GuardedBy;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnPreDrawListener;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

class VisibilityTracker {

  @NonNull
  private final VisibilityChecker visibilityChecker;

  @NonNull
  @GuardedBy("lock")
  private Map<View, VisibilityTrackingTask> trackedViews = new WeakHashMap<>();

  private final Object lock = new Object();

  VisibilityTracker(@NonNull VisibilityChecker visibilityChecker) {
    this.visibilityChecker = visibilityChecker;
  }

  /**
   * Add the given {@link View} to the set of watched views.
   * <p>
   * As long as this view live, if at one moment it is drawn and {@linkplain
   * VisibilityChecker#isVisible(View) visible} on user screen, then the given listener will be
   * invoked.
   * <p>
   * It is safe to call again this method with the same view and listener, and it is also same to
   * call again with the same view and an other listener. For a given view, only the last registered
   * listener will be invoked. Hence, when having recycled view, you do not need to clean it
   * before.
   *
   * @param view     new or recycle view to watch for visibility
   * @param listener listener to trigger once visibility is detected
   */
  void watch(@NonNull View view, @NonNull VisibilityListener listener) {
    VisibilityTrackingTask trackingTask;

    synchronized (lock) {
      trackingTask = trackedViews.get(view);
      if (trackingTask == null) {
        trackingTask = startTrackingNewView(view);
        trackedViews.put(view, trackingTask);
      }
    }

    trackingTask.setListener(listener);
  }

  @NonNull
  private VisibilityTrackingTask startTrackingNewView(@NonNull View view) {
    return new VisibilityTrackingTask(new WeakReference<>(view), visibilityChecker);
  }

  @VisibleForTesting
  static class VisibilityTrackingTask implements OnPreDrawListener {

    @NonNull
    private final Reference<View> trackedViewRef;

    @NonNull
    private final VisibilityChecker visibilityChecker;

    @Nullable
    private volatile VisibilityListener listener = null;

    VisibilityTrackingTask(@NonNull Reference<View> viewRef, @NonNull VisibilityChecker visibilityChecker) {
      this.trackedViewRef = viewRef;
      this.visibilityChecker = visibilityChecker;

      setUpObserver();
    }

    private void setUpObserver() {
      View view = trackedViewRef.get();
      if (view == null) {
        return;
      }

      ViewTreeObserver observer = view.getViewTreeObserver();
      if (observer.isAlive()) {
        observer.addOnPreDrawListener(this);
      }
    }

    void setListener(@Nullable VisibilityListener listener) {
      this.listener = listener;
    }

    @Override
    public boolean onPreDraw() {
      if (shouldTrigger()) {
        triggerListener();
      }
      return true;
    }

    private boolean shouldTrigger() {
      View trackedView = trackedViewRef.get();
      if (trackedView == null) {
        return false;
      }

      return visibilityChecker.isVisible(trackedView);
    }

    private void triggerListener() {
      VisibilityListener listener = this.listener;

      if (listener != null) {
        listener.onVisible();
      }
    }
  }

}
