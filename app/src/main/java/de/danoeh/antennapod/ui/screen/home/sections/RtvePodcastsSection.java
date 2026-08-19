package de.danoeh.antennapod.ui.screen.home.sections;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.event.FeedListUpdateEvent;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter;
import de.danoeh.antennapod.ui.common.SquareImageView;
import de.danoeh.antennapod.ui.screen.home.HomeSection;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okhttp3.Request;
import okhttp3.Response;

public class RtvePodcastsSection extends HomeSection {
    public static final String TAG = "RtvePodcastsSection";

    private static final String API_URL =
            "https://api.rtve.es/api/agr-programas/18690/programas.json?size=8";
    private static final String FEED_URL_TEMPLATE =
            "https://api.rtve.es/api/programas/%s/audios.rss";
    private static final int NUM_ITEMS = 8;

    private Disposable disposable;
    private RtveHorizontalAdapter listAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);

        listAdapter = new RtveHorizontalAdapter((MainActivity) requireActivity());
        listAdapter.setDummyViews(NUM_ITEMS);
        viewBinding.recyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false));
        viewBinding.recyclerView.setAdapter(listAdapter);
        int paddingHorizontal = (int) (12 * getResources().getDisplayMetrics().density);
        viewBinding.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0);
        viewBinding.emptyLabel.setText(R.string.home_new_empty_text);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        loadItems();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onFeedListChanged(FeedListUpdateEvent event) {
        loadItems();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (disposable != null) {
            disposable.dispose();
        }
    }

    @Override
    protected void handleMoreClick() {
    }

    @Override
    protected String getSectionTitle() {
        return getString(R.string.home_rtve_podcasts_title);
    }

    @Override
    protected String getMoreLinkTitle() {
        return "";
    }

    private void loadItems() {
        if (disposable != null) {
            disposable.dispose();
        }
        listAdapter.setDummyViews(NUM_ITEMS);

        disposable = Observable.fromCallable(() -> {
            Request request = new Request.Builder().url(API_URL).build();
            try (Response response = AntennapodHttpClient.getHttpClient()
                            .newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw new IOException("Unexpected response: " + response);
                }
                return parsePodcasts(response.body().string());
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(items -> {
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(items);
                    boolean isEmpty = items.isEmpty();
                    viewBinding.recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    viewBinding.emptyLabel.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                }, error -> {
                    Log.e(TAG, Log.getStackTraceString(error));
                    listAdapter.setDummyViews(0);
                    listAdapter.updateData(new ArrayList<>());
                    viewBinding.recyclerView.setVisibility(View.GONE);
                    viewBinding.emptyLabel.setVisibility(View.VISIBLE);
                });
    }

    private static List<RtveItem> parsePodcasts(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONArray items = root.getJSONObject("page").getJSONArray("items");
        List<RtveItem> result = new ArrayList<>();

        for (int i = 0; i < items.length() && result.size() < NUM_ITEMS; i++) {
            JSONObject item = items.getJSONObject(i);
            String id = item.optString("id", "");
            String name = item.optString("name", "");
            String feedUrl = String.format(FEED_URL_TEMPLATE, id);
            String imageUrl = item.optString("imagePodcast", null);
            if (imageUrl == null || imageUrl.isEmpty()) {
                imageUrl = item.optString("logo", null);
            }
            result.add(new RtveItem(name, imageUrl, feedUrl));
        }
        return result;
    }

    private static class RtveItem {
        final String title;
        final String imageUrl;
        final String feedUrl;

        RtveItem(String title, String imageUrl, String feedUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.feedUrl = feedUrl;
        }
    }

    private static class RtveHorizontalAdapter extends RecyclerView.Adapter<RtveHorizontalAdapter.Holder> {
        private final WeakReference<MainActivity> mainActivityRef;
        private final List<RtveItem> items = new ArrayList<>();
        private int dummyViews = 0;

        RtveHorizontalAdapter(MainActivity activity) {
            this.mainActivityRef = new WeakReference<>(activity);
        }

        void setDummyViews(int dummyViews) {
            this.dummyViews = dummyViews;
            notifyDataSetChanged();
        }

        void updateData(List<RtveItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MainActivity activity = mainActivityRef.get();
            View convertView;
            if (activity != null) {
                convertView = View.inflate(activity, R.layout.horizontal_feed_item, null);
            } else {
                convertView = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.horizontal_feed_item, parent, false);
            }
            return new Holder(convertView);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            MainActivity activity = mainActivityRef.get();
            if (activity == null) {
                holder.itemView.setAlpha(0.1f);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }
            holder.cardView.setVisibility(View.VISIBLE);
            holder.actionButton.setVisibility(View.GONE);
            if (position >= items.size()) {
                holder.itemView.setAlpha(0.1f);
                Glide.with(activity).clear(holder.imageView);
                holder.imageView.setImageResource(R.color.medium_gray);
                holder.imageView.setOnClickListener(null);
                holder.titleView.setVisibility(View.GONE);
                return;
            }

            holder.itemView.setAlpha(1.0f);
            RtveItem item = items.get(position);
            holder.imageView.setContentDescription(item.title);
            holder.titleView.setText(item.title);
            holder.titleView.setVisibility(View.VISIBLE);
            holder.imageView.setOnClickListener(v ->
                    activity.startActivity(
                            new OnlineFeedviewActivityStarter(activity, item.feedUrl).getIntent()));

            Glide.with(activity)
                    .load(item.imageUrl)
                    .apply(new RequestOptions()
                            .placeholder(R.color.light_gray)
                            .centerCrop()
                            .dontAnimate())
                    .into(holder.imageView);
        }

        @Override
        public int getItemCount() {
            return items.size() + dummyViews;
        }

        static class Holder extends RecyclerView.ViewHolder {
            SquareImageView imageView;
            androidx.cardview.widget.CardView cardView;
            android.widget.Button actionButton;
            TextView titleView;

            Holder(@NonNull View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.discovery_cover);
                imageView.setDirection(SquareImageView.DIRECTION_HEIGHT);
                actionButton = itemView.findViewById(R.id.actionButton);
                cardView = itemView.findViewById(R.id.cardView);
                titleView = itemView.findViewById(R.id.titleLabel);
            }
        }
    }
}
