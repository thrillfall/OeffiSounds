package de.danoeh.antennapod.net.discovery;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.core.SingleOnSubscribe;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SrfPlayPodcastSearcher implements PodcastSearcher {
    private static final String API_URL =
            "https://il.srgssr.ch/integrationlayer/2.0/srf/searchResultShowList?q=%s&pageSize=20&vector=APPPLAY";
    private static final String FEED_URL_TEMPLATE = "https://www.srf.ch/feed/podcast/sd/%s.xml";
    private static final String EPISODE_COMPOSITION_URL_TEMPLATE =
            "https://il.srgssr.ch/integrationlayer/2.0/srf/episodeComposition/latestByShow/radio/%s"
                    + "?pageSize=40&vector=APPPLAY";

    @Override
    public Single<List<PodcastSearchResult>> search(String query) {
        return Single.create((SingleOnSubscribe<List<PodcastSearchResult>>) subscriber -> {
            String encodedQuery;
            try {
                encodedQuery = URLEncoder.encode(query, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                encodedQuery = query;
            }

            String url = String.format(API_URL, encodedQuery);

            OkHttpClient client = AntennapodHttpClient.getHttpClient();
            Request request = new Request.Builder().url(url).build();
            List<PodcastSearchResult> podcasts = new ArrayList<>();
            try {
                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String resultString = response.body().string();
                    JSONObject result = new JSONObject(resultString);
                    JSONArray shows = result.optJSONArray("searchResultShowList");
                    if (shows != null) {
                        for (int i = 0; i < shows.length(); i++) {
                            JSONObject show = shows.getJSONObject(i);
                            if (!"RADIO".equals(show.optString("transmission", ""))) {
                                continue;
                            }
                            String id = show.optString("id", null);
                            if (id == null || id.isEmpty()) {
                                continue;
                            }
                            String title = show.optString("title", "");
                            String imageUrl = show.optString("podcastImageUrl", null);
                            if (imageUrl == null || imageUrl.isEmpty()) {
                                imageUrl = show.optString("imageUrl", null);
                            }
                            String feedUrl = String.format(FEED_URL_TEMPLATE, id);
                            if (!feedExists(client, feedUrl)) {
                                feedUrl = String.format(EPISODE_COMPOSITION_URL_TEMPLATE, id);
                            }
                            String author = "SRF";
                            podcasts.add(PodcastSearchResult.fromExternalSource(title, imageUrl, feedUrl, author));
                        }
                    }
                } else {
                    subscriber.onError(new IOException(response.toString()));
                }
            } catch (IOException | JSONException e) {
                subscriber.onError(e);
            }
            subscriber.onSuccess(podcasts);
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private static boolean feedExists(OkHttpClient client, String feedUrl) {
        Request head = new Request.Builder().url(feedUrl).head().build();
        try (Response r = client.newCall(head).execute()) {
            return r.isSuccessful();
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public Single<String> lookupUrl(String url) {
        return Single.just(url);
    }

    @Override
    public boolean urlNeedsLookup(String url) {
        return false;
    }

    @Override
    public String getName() {
        return "SRF Play";
    }
}
