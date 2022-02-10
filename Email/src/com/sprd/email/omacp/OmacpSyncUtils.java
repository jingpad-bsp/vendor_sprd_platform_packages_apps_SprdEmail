
package com.sprd.email.omacp;

import java.util.concurrent.Semaphore;

/*SPRD add for bug677524*/
public class OmacpSyncUtils {
    private final static Semaphore semp = new Semaphore(1);

    public static void acquireLock() {
        try {
            semp.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void realseLock() {
        semp.release();
    }
}
