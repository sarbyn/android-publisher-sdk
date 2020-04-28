package com.criteo.publisher.mock;

import static com.criteo.publisher.util.InstrumentationUtil.isRunningInInstrumentationTest;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.os.Build.VERSION_CODES;
import android.support.annotation.RequiresApi;
import android.support.test.InstrumentationRegistry;
import com.criteo.publisher.CriteoUtil;
import com.criteo.publisher.DependencyProvider;
import com.criteo.publisher.MockableDependencyProvider;
import com.criteo.publisher.concurrent.ThreadingUtil;
import com.criteo.publisher.concurrent.TrackingCommandsExecutor;
import com.criteo.publisher.model.CdbResponse;
import com.criteo.publisher.model.RemoteConfigResponse;
import com.criteo.publisher.network.PubSdkApi;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.internal.runners.statements.FailOnTimeout;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mockito.Mockito;

/**
 * Use this Rule when writing tests that require mocking global dependencies.
 */
public class MockedDependenciesRule implements MethodRule {

  /**
   * Apply a timeout on all tests using this rule.
   * <p>
   * Lot of tests are waiting for end of AsyncTasks or for third-parties' events. Those tests may be
   * stuck and block the overall execution. In order to avoid any infinite blocking, long tests will
   * be killed by this timeout rule.
   * <p>
   * This timeout duration is roughly set and may have to be updated in the future.
   * <p>
   * Warning: When debugging, this timeout may interrupt your work and be annoying. To deactivate
   * it, you may set the {@link #iAmDebuggingDoNotTimeoutMe} variable to <code>true</code>.
   */
  private final FailOnTimeout.Builder timeout = FailOnTimeout.builder()
      .withTimeout(30, TimeUnit.SECONDS);

  private final boolean iAmDebuggingDoNotTimeoutMe = false;

  protected DependencyProvider dependencyProvider;
  private TrackingCommandsExecutor trackingCommandsExecutor = null;

  @Override
  public Statement apply(Statement base, FrameworkMethod method, Object target) {
    return new Statement() {
      @RequiresApi(api = VERSION_CODES.O)
      @Override
      public void evaluate() throws Throwable {
        try {
          setUpDependencyProvider();
          injectDependencies(target);

          if (iAmDebuggingDoNotTimeoutMe) {
            base.evaluate();
          } else {
            timeout.build(base).evaluate();
          }
        } finally {
          // clean after self and ensures no side effects for subsequent tests
          MockableDependencyProvider.setInstance(null);
        }
      }
    };
  }

  private void setUpDependencyProvider() {
    DependencyProvider originalDependencyProvider = DependencyProvider.getInstance();

    Application application = getApplication();
    originalDependencyProvider.setApplication(application);
    originalDependencyProvider.setCriteoPublisherId(CriteoUtil.TEST_CP_ID);

    Executor oldExecutor = originalDependencyProvider.provideThreadPoolExecutor();

    trackingCommandsExecutor = new TrackingCommandsExecutor(oldExecutor);
    dependencyProvider = spy(originalDependencyProvider);
    MockableDependencyProvider.setInstance(dependencyProvider);
    doReturn(trackingCommandsExecutor).when(dependencyProvider).provideThreadPoolExecutor();
  }

  private Application getApplication() {
    if (isRunningInInstrumentationTest()) {
      return (Application) InstrumentationRegistry.getTargetContext().getApplicationContext();
    } else {
      return ApplicationMock.newMock();
    }
  }

  @RequiresApi(api = VERSION_CODES.O)
  private void injectDependencies(Object target) {
    DependenciesAnnotationInjection injection = new DependenciesAnnotationInjection(dependencyProvider);
    injection.process(target);
  }

  public DependencyProvider getDependencyProvider() {
    return dependencyProvider;
  }

  @RequiresApi(api = VERSION_CODES.M)
  public void waitForIdleState() {
    ThreadingUtil.waitForAllThreads(trackingCommandsExecutor);
  }

  public ResultCaptor<CdbResponse> captorCdbResult() {
    ResultCaptor<CdbResponse> captor = new ResultCaptor<>();
    PubSdkApi spyApi = spy(getDependencyProvider().providePubSdkApi());
    doReturn(spyApi).when(getDependencyProvider()).providePubSdkApi();

    try {
      doAnswer(captor).when(spyApi).loadCdb(any(), any());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return captor;
  }

  // FIXME (ma.chentir) https://jira.criteois.com/browse/EE-1040
  // To be removed when we are able to mock backend responses. Ideally, this behavior should be
  // defined in MockedDependenciesRule#setupNetworkDependencies(). However, this would require
  // mocking PubSdkApi and overriding its behavior (like what is done below). But... PubSdkApi, would
  // also be mocked within this test file specifically and hence all the behavior that was defined
  // earlier would be lost.
  // Once we will be able to mock backend responses with MockWebServer, we will be able to define
  // a mock response for the RemoteConfig backend without having to play PubSdkApi at all.
  public void givenMockedRemoteConfigResponse(PubSdkApi pubSdkApi)
      throws IOException {
    RemoteConfigResponse remoteConfigResponse = Mockito.mock(RemoteConfigResponse.class);
    doReturn(false).when(remoteConfigResponse).getKillSwitch();
    doReturn(true).when(remoteConfigResponse).getCsmEnabled();
    doReturn(remoteConfigResponse).when(pubSdkApi).loadConfig(any());
  }
}
