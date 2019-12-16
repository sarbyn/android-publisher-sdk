package com.criteo.publisher.integration;

import static com.criteo.publisher.CriteoUtil.TEST_CP_ID;
import static com.criteo.publisher.CriteoUtil.givenInitializedCriteo;
import static com.criteo.publisher.ThreadingUtil.waitForAllThreads;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.support.test.InstrumentationRegistry;
import com.criteo.publisher.Criteo;
import com.criteo.publisher.TestAdUnits;
import com.criteo.publisher.Util.MockedDependenciesRule;
import com.criteo.publisher.model.AdUnit;
import com.criteo.publisher.model.BannerAdUnit;
import com.criteo.publisher.model.Cdb;
import com.criteo.publisher.model.InterstitialAdUnit;
import com.criteo.publisher.network.PubSdkApi;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;

public class CriteoFunctionalTest {

  @Rule
  public MockedDependenciesRule mockedDependenciesRule  = new MockedDependenciesRule();

  private final BannerAdUnit validBannerAdUnit = TestAdUnits.BANNER_320_50;
  private final InterstitialAdUnit validInterstitialAdUnit = TestAdUnits.INTERSTITIAL;

  @Test
  public void init_GivenPrefetchCachedBannerAndReInitWithSameBanner_CdbIsNotCallTheSecondTime() throws Exception {
    init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(validBannerAdUnit);
  }

  @Test
  public void init_GivenPrefetchCachedInterstitialAndReInitWithSameInterstitial_CdbIsNotCallTheSecondTime() throws Exception {
    init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(validInterstitialAdUnit);
  }

  private void init_GivenPrefetchCachedAdAndReInitWithSameAdUnit_CdbIsNotCallTheSecondTime(AdUnit adUnit)
      throws Exception {
    Application app = (Application) InstrumentationRegistry.getTargetContext()
        .getApplicationContext();

    PubSdkApi api = spy(mockedDependenciesRule.getDependencyProvider().providePubSdkApi(app));
    when(mockedDependenciesRule.getDependencyProvider().providePubSdkApi(any())).thenReturn(api);

    int dayTtl = 3600 * 24;

    doAnswer(invocation -> {
      Object response = invocation.callRealMethod();
      Cdb cdbResponse = (Cdb) response;
      cdbResponse.getSlots().forEach(slot -> {
        slot.setTtl(dayTtl);
      });
      return cdbResponse;
    }).when(api).loadCdb(any(), any());

    givenInitializedCriteo(adUnit);
    waitForBids();

    Criteo.init(app, TEST_CP_ID, Collections.singletonList(adUnit));
    waitForBids();

    verify(api, times(1)).loadCdb(any(), any());
  }

  private void waitForBids() {
    waitForAllThreads(mockedDependenciesRule.getTrackingCommandsExecutor());
  }

}