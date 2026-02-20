package de.danoeh.antennapod.net.sync.nextcloud;

import android.content.Context;

import com.google.gson.GsonBuilder;
import com.nextcloud.android.sso.AccountImporter;
import com.nextcloud.android.sso.QueryParam;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.api.NextcloudAPI;
import com.nextcloud.android.sso.api.Response;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import de.danoeh.antennapod.net.sync.gpoddernet.mapper.ResponseMapper;
import de.danoeh.antennapod.net.sync.gpoddernet.model.GpodnetUploadChangesResponse;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeAction;
import de.danoeh.antennapod.net.sync.serviceinterface.EpisodeActionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.ISyncService;
import de.danoeh.antennapod.net.sync.serviceinterface.SubscriptionChanges;
import de.danoeh.antennapod.net.sync.serviceinterface.SyncServiceException;
import de.danoeh.antennapod.net.sync.serviceinterface.UploadChangesResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.commons.io.IOUtils;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;

public class NextcloudSyncService implements ISyncService {
    private static final int UPLOAD_BULK_SIZE = 30;
    private static final String GPODDERSYNC_API_BASE = "/index.php/apps/gpoddersync";

    private final Context context;
    private final String accountName;
    private NextcloudAPI nextcloudApi;
    private SingleSignOnAccount ssoAccount;

    public NextcloudSyncService(Context context, String accountName) {
        this.context = context.getApplicationContext();
        this.accountName = accountName;
    }

    @Override
    public void login() throws SyncServiceException {
        if (accountName == null) {
            throw new SyncServiceException(new IllegalStateException("No Nextcloud account selected"));
        }
        try {
            ssoAccount = AccountImporter.getSingleSignOnAccount(context, accountName);
            nextcloudApi = new NextcloudAPI(context, ssoAccount, new GsonBuilder().create());
        } catch (Exception e) {
            throw new SyncServiceException(e);
        }
    }

    @Override
    public SubscriptionChanges getSubscriptionChanges(long lastSync) throws SyncServiceException {
        try {
            NextcloudRequest request = new NextcloudRequest.Builder()
                    .setMethod("GET")
                    .setUrl(makeUrl(GPODDERSYNC_API_BASE + "/subscriptions"))
                    .addParameter(new QueryParam("since", String.valueOf(lastSync)))
                    .setAccountName(accountName)
                    .setToken(ssoAccount.token)
                    .build();
            String responseString = performRequest(request);
            JSONObject json = new JSONObject(responseString);
            return ResponseMapper.readSubscriptionChangesFromJsonObject(json);
        } catch (JSONException | MalformedURLException e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        }
    }

    @Override
    public UploadChangesResponse uploadSubscriptionChanges(List<String> addedFeeds,
                                                           List<String> removedFeeds)
            throws NextcloudSynchronizationServiceException {
        try {
            final JSONObject requestObject = new JSONObject();
            requestObject.put("add", new JSONArray(addedFeeds));
            requestObject.put("remove", new JSONArray(removedFeeds));
            NextcloudRequest request = new NextcloudRequest.Builder()
                    .setMethod("POST")
                    .setUrl(makeUrl(GPODDERSYNC_API_BASE + "/subscription_change/create"))
                    .setRequestBody(requestObject.toString())
                    .setAccountName(accountName)
                    .setToken(ssoAccount.token)
                    .build();
            String responseString = performRequest(request);
            return GpodnetUploadChangesResponse.fromJSONObject(responseString);
        } catch (Exception e) {
            e.printStackTrace();
            throw new NextcloudSynchronizationServiceException(e);
        }
    }

    @Override
    public EpisodeActionChanges getEpisodeActionChanges(long timestamp) throws SyncServiceException {
        try {
            NextcloudRequest request = new NextcloudRequest.Builder()
                    .setMethod("GET")
                    .setUrl(makeUrl(GPODDERSYNC_API_BASE + "/episode_action"))
                    .addParameter(new QueryParam("since", String.valueOf(timestamp)))
                    .setAccountName(accountName)
                    .setToken(ssoAccount.token)
                    .build();
            String responseString = performRequest(request);
            JSONObject json = new JSONObject(responseString);
            return ResponseMapper.readEpisodeActionsFromJsonObject(json);
        } catch (JSONException | MalformedURLException e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        } catch (Exception e) {
            e.printStackTrace();
            throw new SyncServiceException(e);
        }
    }

    @Override
    public UploadChangesResponse uploadEpisodeActions(List<EpisodeAction> queuedEpisodeActions)
            throws NextcloudSynchronizationServiceException {
        for (int i = 0; i < queuedEpisodeActions.size(); i += UPLOAD_BULK_SIZE) {
            uploadEpisodeActionsPartial(queuedEpisodeActions,
                    i, Math.min(queuedEpisodeActions.size(), i + UPLOAD_BULK_SIZE));
        }
        return new NextcloudGpodderEpisodeActionPostResponse(System.currentTimeMillis() / 1000);
    }

    private void uploadEpisodeActionsPartial(List<EpisodeAction> queuedEpisodeActions, int from, int to)
            throws NextcloudSynchronizationServiceException {
        try {
            final JSONArray list = new JSONArray();
            for (int i = from; i < to; i++) {
                EpisodeAction episodeAction = queuedEpisodeActions.get(i);
                JSONObject obj = episodeAction.writeToJsonObject();
                if (obj != null) {
                    list.put(obj);
                }
            }
            NextcloudRequest request = new NextcloudRequest.Builder()
                    .setMethod("POST")
                    .setUrl(makeUrl(GPODDERSYNC_API_BASE + "/episode_action/create"))
                    .setRequestBody(list.toString())
                    .setAccountName(accountName)
                    .setToken(ssoAccount.token)
                    .build();
            performRequest(request);
        } catch (Exception e) {
            e.printStackTrace();
            throw new NextcloudSynchronizationServiceException(e);
        }
    }

    private String performRequest(NextcloudRequest request) throws Exception {
        if (nextcloudApi == null) {
            throw new IllegalStateException("Not logged in");
        }
        Response response = nextcloudApi.performNetworkRequestV2(request);
        try (java.io.InputStream body = response.getBody()) {
            return IOUtils.toString(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String makeUrl(String path) {
        if (path == null || path.isEmpty()) {
            return "/";
        }
        if (path.startsWith("/")) {
            return path;
        }
        return "/" + path;
    }

    @Override
    public void logout() {
        if (nextcloudApi != null) {
            nextcloudApi.close();
            nextcloudApi = null;
        }
    }

    private static class NextcloudGpodderEpisodeActionPostResponse extends UploadChangesResponse {
        public NextcloudGpodderEpisodeActionPostResponse(long epochSecond) {
            super(epochSecond);
        }
    }
}

