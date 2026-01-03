package org.araymond.joal.core.ttorrent.client.announcer;

import lombok.RequiredArgsConstructor;
import org.apache.http.client.HttpClient;
import org.araymond.joal.core.config.AppConfiguration;
import org.araymond.joal.core.torrent.torrent.MockedTorrent;
import org.araymond.joal.core.ttorrent.client.announcer.request.AnnounceDataAccessor;

import javax.inject.Provider;

@RequiredArgsConstructor
public class AnnouncerFactory {
    private final AnnounceDataAccessor announceDataAccessor;
    private final HttpClient httpClient;
    private final Provider<AppConfiguration> appConfigurationProvider;

    public Announcer create(final MockedTorrent torrent) {
        return new Announcer(torrent, this.announceDataAccessor, httpClient, appConfigurationProvider.get().getUploadRatioTarget());
    }
}
