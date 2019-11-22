package com.criteo.publisher;

import android.app.Application;
import android.support.test.InstrumentationRegistry;
import com.criteo.publisher.model.AdUnit;
import java.util.Arrays;

public class CriteoUtil {

  public static Criteo givenInitializedCriteo(AdUnit... preloadedAdUnits) throws CriteoInitException {
    Application app = (Application) InstrumentationRegistry.getTargetContext()
        .getApplicationContext();

    // clears any side effects from previous calls
    Criteo.setInstance(null);

    return Criteo.init(app, "B-000001", Arrays.asList(preloadedAdUnits));
  }

}
