package org.araymond.joal.core.bandwith;

import lombok.Getter;
import org.araymond.joal.core.config.AppConfiguration;

import javax.inject.Provider;
import java.util.concurrent.ThreadLocalRandom;

public class RandomSpeedProvider {
    private final Provider<AppConfiguration> appConfProvider;

    @Getter
    private long currentSpeed;  // bytes/s

    public RandomSpeedProvider(final Provider<AppConfiguration> appConfProvider) {
        this.appConfProvider = appConfProvider;
        this.refresh();
    }

    public void refresh() {
        final AppConfiguration appConf = appConfProvider.get();
        final long minUploadRateInBytes = appConf.getMinUploadRate() * 1000L;
        final long maxUploadRateInBytes = appConf.getMaxUploadRate() * 1000L;
        this.currentSpeed = (minUploadRateInBytes == maxUploadRateInBytes)
                ? maxUploadRateInBytes
                : ThreadLocalRandom.current().nextLong(minUploadRateInBytes, maxUploadRateInBytes);
    }
}
