package com.criteo.publisher;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import com.criteo.publisher.AppEvents.AppEvents;
import com.criteo.publisher.Util.AdUnitType;
import com.criteo.publisher.Util.AppLifecycleUtil;
import com.criteo.publisher.Util.DeviceUtil;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.AdUnitHelper;
import com.criteo.publisher.model.CacheAdUnit;
import com.criteo.publisher.model.ScreenSize;
import com.criteo.publisher.model.Slot;
import com.criteo.publisher.model.TokenValue;
import java.util.ArrayList;
import java.util.List;

public final class Criteo {

    private static final String TAG = Criteo.class.getSimpleName();

    private static Criteo criteo;
    private BidManager bidManager;
    private AppEvents appEvents;
    private AppLifecycleUtil appLifecycleUtil;

    public static Criteo init(Application application, String criteoPublisherId, List<AdUnit> adUnits) {
        synchronized (Criteo.class) {
            if (criteo == null) {
                criteo = new Criteo(application, adUnits, criteoPublisherId);
            }
        }
        return criteo;
    }

    public static Criteo getInstance() {
        return criteo;
    }

    private Criteo(Application application, List<AdUnit> adUnits, String criteoPublisherId) {
        if (application == null) {
            throw new IllegalArgumentException("Application reference is required.");
        }

        if (TextUtils.isEmpty(criteoPublisherId)) {
            throw new IllegalArgumentException("Criteo Publisher Id is required.");
        }

        if (adUnits == null || adUnits.size() == 0) {
            adUnits = new ArrayList<>();
        }

        Context context = application.getApplicationContext();
        createSupportedScreenSizes(application);
        List<CacheAdUnit> cacheAdUnits = AdUnitHelper.convertAdUnits(context, adUnits);

        for (CacheAdUnit cacheAdUnit : cacheAdUnits) {
            if (TextUtils.isEmpty(cacheAdUnit.getPlacementId()) || cacheAdUnit.getSize() == null
                    || cacheAdUnit.getSize().getWidth() <= 0 || cacheAdUnit.getSize().getHeight() <= 0) {
                Log.e(TAG, "Found an invalid AdUnit: " + cacheAdUnit);
                return;
            }
        }

        this.bidManager = new BidManager(context, criteoPublisherId, cacheAdUnits,
                new TokenCache());
        this.appEvents = new AppEvents(context);
        this.appLifecycleUtil = new AppLifecycleUtil(application, appEvents, bidManager);
        bidManager.prefetch();
    }

    public void setBidsForAdUnit(Object object, AdUnit adUnit) {
        if (bidManager == null) {
            return;
        }
        bidManager.enrichBid(object, adUnit);
    }

    /**
     * Method to start new CdbDownload Asynctask
     */
    Slot getBidForAdUnit(AdUnit adUnit) {
        if (bidManager == null) {
            return null;
        }
        return bidManager.getBidForAdUnitAndPrefetch(adUnit);
    }

    private void createSupportedScreenSizes(Application application) {

        ArrayList<ScreenSize> screenSizesPortrait = new ArrayList<>();
        screenSizesPortrait.add(new ScreenSize(320, 480));
        screenSizesPortrait.add(new ScreenSize(360, 640));

        ArrayList<ScreenSize> screenSizesLandscape = new ArrayList<>();
        screenSizesLandscape.add(new ScreenSize(480, 320));
        screenSizesLandscape.add(new ScreenSize(640, 360));

        try {
            DisplayMetrics metrics = new DisplayMetrics();
            ((WindowManager) application.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay()
                    .getMetrics(metrics);
            DeviceUtil.setScreenSize(Math.round(metrics.widthPixels / metrics.density),
                    Math.round(metrics.heightPixels / metrics.density), screenSizesPortrait,
                    screenSizesLandscape);
        } catch (Exception e) {
            throw new Error("Screen parameters can not be empty or null");
        }
    }

    public BidResponse getBidResponse(AdUnit adUnit) {
        if (bidManager == null) {
            return null;
        }
        return bidManager.getBidForInhouseMediation(adUnit);
    }

    TokenValue getTokenValue(BidToken bidToken, AdUnitType adUnitType) {
        if (bidManager == null) {
            return null;
        }
        return bidManager.getTokenValue(bidToken, adUnitType);
    }

}
