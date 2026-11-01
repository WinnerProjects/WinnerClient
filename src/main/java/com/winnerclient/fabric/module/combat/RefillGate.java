package com.winnerclient.fabric.module.combat;

/**
 * AutoTotem'in aynı envanter işlemini sürekli göndermesini engeller.
 * Zamanlar oyuncu tick sayısı üzerinden takip edilir.
 */
final class RefillGate {
    private boolean waitingForConfirmation;
    private long retryAtTick;

    void reset() {
        waitingForConfirmation = false;
        retryAtTick = 0L;
    }

    boolean ready(int currentTick) {
        if ((long) currentTick < retryAtTick) {
            return false;
        }

        // Onay gelmediyse zaman aşımından sonra yeniden denemeye izin ver.
        waitingForConfirmation = false;
        return true;
    }

    boolean pending() {
        return waitingForConfirmation;
    }

    void attempted(int currentTick, int confirmationTicks) {
        waitingForConfirmation = true;
        retryAtTick = (long) currentTick
                + Math.max(1, confirmationTicks);
    }

    void confirmed() {
        waitingForConfirmation = false;
        retryAtTick = 0L;
    }

    void rejected(int currentTick, int retryDelayTicks) {
        /*
         * İlgisiz bir envanter paketi gelmiş olabilir.
         * Mevcut onay süresini kısaltmadan beklemeye devam et.
         */
        long retryAfterRejection = (long) currentTick
                + Math.max(1, retryDelayTicks);

        retryAtTick = Math.max(retryAtTick, retryAfterRejection);
    }

    void popped() {
        waitingForConfirmation = false;
        retryAtTick = 0L;
    }
}
