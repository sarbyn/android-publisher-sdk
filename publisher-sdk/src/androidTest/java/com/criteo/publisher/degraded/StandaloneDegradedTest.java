package com.criteo.publisher.degraded;

import static com.criteo.publisher.CriteoUtil.givenInitializedCriteo;
import static com.criteo.publisher.concurrent.ThreadingUtil.callOnMainThreadAndWait;
import static com.criteo.publisher.concurrent.ThreadingUtil.runOnMainThreadAndWait;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.criteo.publisher.CriteoBannerAdListener;
import com.criteo.publisher.CriteoBannerView;
import com.criteo.publisher.CriteoErrorCode;
import com.criteo.publisher.CriteoInterstitial;
import com.criteo.publisher.CriteoInterstitialAdListener;
import com.criteo.publisher.mock.MockedDependenciesRule;
import com.criteo.publisher.mock.SpyBean;
import com.criteo.publisher.model.AdSize;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.InterstitialAdUnit;
import com.criteo.publisher.network.PubSdkApi;
import com.criteo.publisher.util.DeviceUtil;
import javax.inject.Inject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class StandaloneDegradedTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule = new MockedDependenciesRule();

  private final BannerAdUnit bannerAdUnit = new BannerAdUnit("banner", new AdSize(1, 2));
  private final InterstitialAdUnit interstitialAdUnit = new InterstitialAdUnit("interstitial");

  @Inject
  private Context context;

  @Mock
  private PubSdkApi api;

  @SpyBean
  private DeviceUtil deviceUtil;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    when(deviceUtil.isVersionSupported()).thenReturn(false);

    givenInitializedCriteo();
  }

  @Test
  public void whenLoadingABanner_ShouldNotDoAnyCallToCdb() throws Exception {
    runOnMainThreadAndWait(() -> {
      CriteoBannerView bannerView = new CriteoBannerView(context, bannerAdUnit);
      bannerView.loadAd();
    });

    waitForIdleState();
    verifyNoInteractions(api);
  }

  @Test
  public void whenLoadingTwiceABanner_ShouldCallBackListenerWithErrorNoFill() throws Exception {
    CriteoBannerAdListener listener = mock(CriteoBannerAdListener.class);

    CriteoBannerView bannerView = callOnMainThreadAndWait(() ->
      new CriteoBannerView(context, bannerAdUnit));

    bannerView.setCriteoBannerAdListener(listener);

    runOnMainThreadAndWait(bannerView::loadAd);
    waitForIdleState();

    // Load twice, because first one is a cache miss
    runOnMainThreadAndWait(bannerView::loadAd);
    waitForIdleState();

    verify(listener, never()).onAdReceived(any());
    verify(listener, times(2)).onAdFailedToReceive(CriteoErrorCode.ERROR_CODE_NO_FILL);
  }

  @Test
  public void whenLoadingAnInterstitial_ShouldNotDoAnyCallToCdb() throws Exception {
    runOnMainThreadAndWait(() -> {
      CriteoInterstitial interstitial = new CriteoInterstitial(context, interstitialAdUnit);

      interstitial.loadAd();
    });

    waitForIdleState();
    verifyNoInteractions(api);
  }

  @Test
  public void whenLoadingTwiceAnInterstitial_ShouldCallBackListenerWithErrorNoFill()
      throws Exception {
    CriteoInterstitialAdListener listener = mock(CriteoInterstitialAdListener.class);

    CriteoInterstitial interstitial = callOnMainThreadAndWait(() ->
        new CriteoInterstitial(context, interstitialAdUnit));

    interstitial.setCriteoInterstitialAdListener(listener);

    runOnMainThreadAndWait(interstitial::loadAd);
    waitForIdleState();

    // Load twice, because first one is a cache miss
    runOnMainThreadAndWait(interstitial::loadAd);
    waitForIdleState();

    verify(listener, never()).onAdReceived();
    verify(listener, times(2)).onAdFailedToReceive(CriteoErrorCode.ERROR_CODE_NO_FILL);
  }

  private void waitForIdleState() {
    mockedDependenciesRule.waitForIdleState();
  }

}
