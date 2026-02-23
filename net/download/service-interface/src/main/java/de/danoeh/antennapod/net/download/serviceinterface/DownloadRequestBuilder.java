package de.danoeh.antennapod.net.download.serviceinterface;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.net.URI;
import java.net.URISyntaxException;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.UrlChecker;

public class DownloadRequestBuilder {
    private final String destination;
    private String source;
    private final String title;
    private String username;
    private String password;
    private String lastModified;
    private final long feedfileId;
    private final int feedfileType;
    private final Bundle arguments = new Bundle();
    private boolean initiatedByUser = true;

    public DownloadRequestBuilder(@NonNull String destination, @NonNull FeedMedia media) {
        this.destination = destination;
        this.source = UrlChecker.prepareUrl(media.getDownloadUrl());
        this.title = media.getHumanReadableIdentifier();
        this.feedfileId = media.getId();
        this.feedfileType = FeedMedia.FEEDFILETYPE_FEEDMEDIA;
    }

    public DownloadRequestBuilder(@NonNull String destination, @NonNull Feed feed) {
        this.destination = destination;
        String preparedUrl = feed.isLocalFeed() ? feed.getDownloadUrl() : UrlChecker.prepareUrl(feed.getDownloadUrl());
        this.source = ensureAudiothekLimit(preparedUrl);
        this.title = feed.getHumanReadableIdentifier();
        this.feedfileId = feed.getId();
        this.feedfileType = Feed.FEEDFILETYPE_FEED;
        arguments.putInt(DownloadRequest.REQUEST_ARG_PAGE_NR, feed.getPageNr());
    }

    public DownloadRequestBuilder withInitiatedByUser(boolean initiatedByUser) {
        this.initiatedByUser = initiatedByUser;
        return this;
    }

    public void setSource(String source) {
        this.source = ensureAudiothekLimit(source);
    }

    public void setForce(boolean force) {
        if (force) {
            lastModified = null;
        }
    }

    public DownloadRequestBuilder lastModified(String lastModified) {
        this.lastModified = lastModified;
        return this;
    }

    public DownloadRequestBuilder withAuthentication(String username, String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public DownloadRequest build() {
        return new DownloadRequest(destination, source, title, feedfileId, feedfileType,
                lastModified, username, password, false, arguments, initiatedByUser);
    }

    private static final String AUDIOTHEK_HOST = "api.ardaudiothek.de";
    private static final String PROGRAM_SET_PATH_PREFIX = "/programsets/";
    private static final String PARAM_ORDER = "order";
    private static final String PARAM_LIMIT = "limit";
    private static final String ORDER_DESC = "desc";
    private static final String LIMIT_SIZE = "200";

    private static String ensureAudiothekLimit(String url) {
        if (url == null) {
            return null;
        }
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase(AUDIOTHEK_HOST)) {
                return url;
            }
            String path = uri.getPath();
            if (path == null || !path.startsWith(PROGRAM_SET_PATH_PREFIX)) {
                return url;
            }
            String query = uri.getQuery();
            if (query != null && query.contains(PARAM_ORDER)) {
                return url;
            }
            String appended = PARAM_ORDER + '=' + ORDER_DESC + '&' + PARAM_LIMIT + '=' + LIMIT_SIZE;
            URI updated = new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), appended, uri.getFragment());
            return updated.toString();
        } catch (URISyntaxException e) {
            return url;
        }
    }

}