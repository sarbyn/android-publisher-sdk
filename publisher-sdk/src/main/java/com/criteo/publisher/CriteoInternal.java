package com.criteo.publisher;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import com.criteo.publisher.AppEvents.AppEvents;
import com.criteo.publisher.Util.AdUnitType;
import com.criteo.publisher.Util.AppLifecycleUtil;
import com.criteo.publisher.Util.DeviceUtil;
import com.criteo.publisher.Util.TextUtils;
import com.criteo.publisher.interstitial.InterstitialActivityHelper;
import com.criteo.publisher.privacy.UserPrivacyUtil;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.Config;
import com.criteo.publisher.model.DeviceInfo;
import com.criteo.publisher.model.Slot;
import com.criteo.publisher.model.TokenValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

final class CriteoInternal extends Criteo {

  private static final String TAG = CriteoInternal.class.getSimpleName();

  @NonNull
  private final BidManager bidManager;

  @NonNull
  private final DeviceInfo deviceInfo;

  @NonNull
  private final Config config;

  @NonNull
  private final UserPrivacyUtil userPrivacyUtil;

  @NonNull
  private final InHouse inHouse;

  @NonNull
  private final InterstitialActivityHelper interstitialActivityHelper;

  CriteoInternal(
      Application application,
      List<AdUnit> adUnits,
      String criteoPublisherId,
      @Nullable Boolean usPrivacyOptout,
      @Nullable String mopubConsent,
      DependencyProvider dependencyProvider) {

    if (application == null) {
      throw new IllegalArgumentException("Application reference is required.");
    }

    if (TextUtils.isEmpty(criteoPublisherId)) {
      throw new IllegalArgumentException("Criteo Publisher Id is required.");
    }

    if (adUnits == null) {
      adUnits = new ArrayList<>();
    }

    Context context = application.getApplicationContext();
    DeviceUtil deviceUtil = dependencyProvider.provideDeviceUtil(context);
    deviceUtil.createSupportedScreenSizes(application);

    deviceInfo = dependencyProvider.provideDeviceInfo(context);
    deviceInfo.initialize();

    config = dependencyProvider.provideConfig(context);

    bidManager = dependencyProvider.provideBidManager(context, criteoPublisherId);
    inHouse = dependencyProvider.provideInHouse(context, criteoPublisherId);

    interstitialActivityHelper = dependencyProvider.provideInterstitialActivityHelper(context);

    userPrivacyUtil = dependencyProvider.provideUserPrivacyUtil(context);
    if (usPrivacyOptout != null) {
      userPrivacyUtil.storeUsPrivacyOptout(usPrivacyOptout);
    }

    // this nulll check ensures that instantiating Criteo object with null mopub consent value,
    // doesn't erase the previously stored consent value
    if (mopubConsent != null) {
      userPrivacyUtil.storeMopubConsent(mopubConsent);
    }

    AppEvents appEvents = dependencyProvider.provideAppEvents(context);
    AppLifecycleUtil lifecycleCallback = new AppLifecycleUtil(appEvents, bidManager);
    application.registerActivityLifecycleCallbacks(lifecycleCallback);

    prefetchAdUnits(dependencyProvider.provideRunOnUiThreadExecutor(), adUnits);
  }

  private void prefetchAdUnits(Executor executor, List<AdUnit> adUnits) {
    executor.execute(new Runnable() {
      @Override
      public void run() {
        bidManager.prefetch(adUnits);
      }
    });
  }

  @Override
  public void setBidsForAdUnit(Object object, AdUnit adUnit) {
    try {
      doSetBidsForAdUnit(object, adUnit);
    } catch (Throwable e) {
      Log.e(TAG, "Internal error while setting bids for adUnit.", e);
    }
  }

  private void doSetBidsForAdUnit(Object object, AdUnit adUnit) {
    bidManager.enrichBid(object, adUnit);
  }

  /**
   * Method to start new CdbDownload Asynctask
   */
  @Nullable
  @Override
  Slot getBidForAdUnit(AdUnit adUnit) {
    return bidManager.getBidForAdUnitAndPrefetch(adUnit);
  }

  @Override
  public BidResponse getBidResponse(AdUnit adUnit) {
    BidResponse response;

    try {
      response = doGetBidResponse(adUnit);
    } catch (Throwable e) {
      response = new BidResponse();
      Log.e(TAG, "Internal error while getting Bid Response.", e);
    }

    return response;
  }

  private BidResponse doGetBidResponse(@Nullable AdUnit adUnit) {
    return inHouse.getBidResponse(adUnit);
  }

  @Nullable
  @Override
  TokenValue getTokenValue(@Nullable BidToken bidToken, @NonNull AdUnitType adUnitType) {
    return inHouse.getTokenValue(bidToken, adUnitType);
  }

  @NonNull
  @Override
  DeviceInfo getDeviceInfo() {
    return deviceInfo;
  }

  @NonNull
  @Override
  Config getConfig() {
    return config;
  }

  @NonNull
  @Override
  InterstitialActivityHelper getInterstitialActivityHelper() {
    return interstitialActivityHelper;
  }

  @Override
  public void setUsPrivacyOptOut(boolean usPrivacyOptOut) {
    userPrivacyUtil.storeUsPrivacyOptout(usPrivacyOptOut);
  }

  @Override
  public void setMopubConsent(@Nullable String mopubConsent) {
    userPrivacyUtil.storeMopubConsent(mopubConsent);
  }
}
