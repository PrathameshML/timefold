package com.scheduler;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import org.jboss.logging.Logger;

@QuarkusMain
public class ShiftSchedulerApp implements QuarkusApplication {
    
    private static final Logger LOG = Logger.getLogger(ShiftSchedulerApp.class);

    @Override
    public int run(String... args) throws Exception {
        LOG.info("ShiftSchedulerApp (V3) is starting...");
        Quarkus.waitForExit();
        return 0;
    }

    public static void main(String... args) {
        Quarkus.run(ShiftSchedulerApp.class, args);
    }
}
