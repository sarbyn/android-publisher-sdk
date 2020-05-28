package com.criteo.publisher.advancednative;

import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ImageView;
import androidx.annotation.UiThread;
import java.net.URL;

public interface ImageLoader {

  /**
   * Preload the image at the given URL.
   * <p>
   * This method is called before the rendering of the native Ad and lets you start downloading
   * images and caching them in order to have them ready to be rendered when {@link
   * #loadImageInto(URL, ImageView, Drawable)} is called.
   * <p>
   * Implementation is expected to move in a worker thread when doing long task such as network to
   * download the image.
   *
   * @param imageUrl URL of the image to preload
   * @see #loadImageInto(URL, ImageView, Drawable)
   */
  void preload(@NonNull URL imageUrl) throws Exception;

  /**
   * Load the image at the given URL and set it in the given image view when finished.
   * <p>
   * The given image URL is in HTTPS and represents images in PNG, WebP or JPEG format.
   * <p>
   * This method is called on the UI-thread, so you can prepare the image view as you need.
   * Implementation is expected to move in a worker thread when doing long task such as network to
   * download the image. Also, having a caching mechanism is recommended to minimize network calls
   * and avoid flickering effects in your RecyclerViews.
   * <p>
   * If you're using RecyclerViews, then the implementation should be aware that an image view can
   * be recycled and reused for another URL. This also means that a given image view may contain an
   * old image. If you need to do any operations outside the UI-thread, you're expected to clean the
   * state of the view by setting the given placeholder. If you're using an image loading library,
   * it generally already takes care of that.
   *
   * @param imageUrl URL of the image to load
   * @param imageView the image view to fill
   * @param placeholder that you defined the {@link CriteoMediaView}
   */
  @UiThread
  void loadImageInto(
      @NonNull URL imageUrl,
      @NonNull ImageView imageView,
      @Nullable Drawable placeholder
  ) throws Exception;

}
