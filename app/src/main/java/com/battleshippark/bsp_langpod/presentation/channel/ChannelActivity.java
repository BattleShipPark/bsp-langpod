package com.battleshippark.bsp_langpod.presentation.channel;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.battleshippark.bsp_langpod.R;
import com.battleshippark.bsp_langpod.dagger.DaggerDbApiGraph;
import com.battleshippark.bsp_langpod.dagger.DaggerDomainMapperGraph;
import com.battleshippark.bsp_langpod.dagger.DaggerServerApiGraph;
import com.battleshippark.bsp_langpod.data.db.ChannelDbApi;
import com.battleshippark.bsp_langpod.data.db.ChannelRealm;
import com.battleshippark.bsp_langpod.data.db.EpisodeRealm;
import com.battleshippark.bsp_langpod.data.downloader.DownloadCompleteParam;
import com.battleshippark.bsp_langpod.data.downloader.DownloadErrorParam;
import com.battleshippark.bsp_langpod.data.downloader.DownloadProgressParam;
import com.battleshippark.bsp_langpod.data.server.ChannelServerApi;
import com.battleshippark.bsp_langpod.domain.DomainMapper;
import com.battleshippark.bsp_langpod.domain.GetChannel;
import com.battleshippark.bsp_langpod.domain.SubscribeChannel;
import com.battleshippark.bsp_langpod.presentation.EpisodeDateFormat;
import com.battleshippark.bsp_langpod.service.downloader.Downloader;
import com.battleshippark.bsp_langpod.service.downloader.DownloaderBroadcastReceiver;
import com.battleshippark.bsp_langpod.service.player.Player;
import com.battleshippark.bsp_langpod.service.player.PlayerBroadcastReceiver;
import com.battleshippark.bsp_langpod.util.Logger;
import com.bumptech.glide.Glide;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Actions;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

import static com.battleshippark.bsp_langpod.Const.MEGA_BYTE;

public class ChannelActivity extends Activity implements OnItemListener {
    private static final String TAG = ChannelActivity.class.getSimpleName();
    private static final String KEY_ID = "keyId";
    private static final Logger logger = new Logger(TAG);

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.progressbar)
    ProgressBar progressBar;
    @BindView(R.id.channel_rv)
    RecyclerView rv;
    @BindView(R.id.msg_tv)
    TextView msgTextView;

    private CompositeSubscription subscription = new CompositeSubscription();
    private Unbinder unbinder;
    private ChannelAdapter adapter;
    private PlayerBroadcastReceiver playerBcReceiver;
    private DownloaderBroadcastReceiver downloaderBcReceiver;

    private Player player;
    private Downloader downloader;

    private GetChannel getChannel;
    private SubscribeChannel subscribeChannel;

    private long channelId;
    private ChannelRealm channelRealm;
    private EpisodeDateFormat dateFormat = new EpisodeDateFormat();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_channel);

        unbinder = ButterKnife.bind(this);

        initData(savedInstanceState);
        initUI();

        requestChannel();
    }

    private void initData(Bundle savedInstanceState) {
        ChannelDbApi channelDbApi = DaggerDbApiGraph.create().channelApi();
        ChannelServerApi channelServerApi = DaggerServerApiGraph.create().channelApi();
        DomainMapper domainMapper = DaggerDomainMapperGraph.create().domainMapper();

        getChannel = new GetChannel(channelDbApi, channelServerApi, Schedulers.io(), AndroidSchedulers.mainThread(), domainMapper);
        subscribeChannel = new SubscribeChannel(channelDbApi, Schedulers.io(), AndroidSchedulers.mainThread());

        adapter = new ChannelAdapter(this);

        player = new Player(this);
        downloader = new Downloader(this);

        downloaderBcReceiver = new DownloaderBroadcastReceiver(this,
                this::onDownloadProgress, this::onDownloadCompleted, this::onDownloadError);
        playerBcReceiver = new PlayerBroadcastReceiver(this,
                this::onMediaPlay, this::onMediaPause, this::onMediaPlayed, this::onMediaPlaying);

        if (savedInstanceState == null) {
            channelId = getIntent().getLongExtra(KEY_ID, 0);
        } else {
            channelId = savedInstanceState.getLong(KEY_ID);
        }
    }

    private void initUI() {
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowHomeEnabled(true);

        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);
    }

    private void requestChannel() {
        progressBar.setVisibility(View.VISIBLE);
        subscription.add(
                getChannel.execute(new GetChannel.Param(channelId, GetChannel.Type.DB_AND_SERVER))
                        .subscribe(this::showData, this::showError, this::dataCompleted));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        player.onStart();
        downloader.init();
        registerReceiver();
    }

    @Override
    protected void onStop() {
        unregisterReceiver();
        downloader.release();
        player.onStop();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        subscription.unsubscribe();
        unbinder.unbind();
        super.onDestroy();
    }

    void showData(ChannelRealm channelRealm) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        this.channelRealm = channelRealm;
        adapter.setItems(this.channelRealm);

        rv.setVisibility(View.VISIBLE);
        msgTextView.setVisibility(View.GONE);

        toolbar.setTitle(channelRealm.getTitle());
    }

    void showError(Throwable throwable) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        GetChannel.GetChannelThrowable t = (GetChannel.GetChannelThrowable) throwable;
        if (t.getType() == GetChannel.Type.ONLY_DB) {
            rv.setVisibility(View.GONE);
            msgTextView.setVisibility(View.VISIBLE);
            msgTextView.setText(R.string.my_list_error_msg);
        } else {
            Toast.makeText(this, R.string.channel_failed_load_from_network, Toast.LENGTH_SHORT).show();
        }
        progressBar.setVisibility(View.GONE);
        logger.w(throwable);
    }

    void dataCompleted() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void onBindHeaderViewHolder(ChannelAdapter.HeaderViewHolder holder, ChannelRealm channel) {
        Glide.with(holder.imageView.getContext()).load(channel.getImage()).into(holder.imageView);

        holder.descView.setText(channel.getDesc());
        holder.copyrightView.setText(channel.getCopyright());
        holder.episodeCountView.setText(String.valueOf(channel.getEpisodes().size()));

        holder.subscribeView.setSelected(channel.isSubscribed());
        holder.subscribeView.setOnClickListener(
                v -> subscribeChannel.execute(channel)
                        .subscribe(
                                subscribed -> {
                                    channelRealm.setSubscribed(subscribed);
                                    adapter.notifyDataSetChanged();
                                },
                                logger::w
                        )
        );
    }

    @Override
    public void onBindEpisodeViewHolder(ChannelAdapter.EpisodeViewHolder holder, EpisodeRealm episode) {
        holder.itemView.setOnClickListener(v -> onClickEpisode(episode));
        holder.itemView.setOnLongClickListener(v -> onLongClickEpisode(episode));

        holder.descView.setText(episode.getDesc());
        holder.dateView.setText(dateFormat.format(episode.getDate()));
        holder.statusTv.setText(getStatusText(episode));
        holder.statusIv.setImageResource(getStatusImage(episode));

        if (episode.getPlayState() == EpisodeRealm.PlayState.PLAYED) {
            holder.itemView.setBackgroundColor(0xFF444444);
        } else {
            holder.itemView.setBackground(null);
        }
    }

    private void onClickEpisode(EpisodeRealm episode) {
        if (episode.getDownloadState() == EpisodeRealm.DownloadState.NOT_DOWNLOADED
                || episode.getDownloadState() == EpisodeRealm.DownloadState.FAILED_DOWNLOAD) {
            tryDownload(episode);
        } else if (episode.getDownloadState() == EpisodeRealm.DownloadState.DOWNLOADED) {
            if (episode.getPlayState() == EpisodeRealm.PlayState.NOT_PLAYED
                    || episode.getPlayState() == EpisodeRealm.PlayState.PAUSE
                    || episode.getPlayState() == EpisodeRealm.PlayState.PLAYED) {
                playEpisode(episode);
            } else if (episode.getPlayState() == EpisodeRealm.PlayState.PLAYING) {
                pauseEpisode(episode);
            }
        }
    }

    private boolean onLongClickEpisode(EpisodeRealm episode) {
        if (episode.getDownloadState() == EpisodeRealm.DownloadState.DOWNLOADING) {
            new AlertDialog.Builder(this).setMessage(R.string.download_downloading_episode)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> tryDownload(episode))
                    .setNegativeButton(android.R.string.no, (dialog, which) -> Actions.empty())
                    .show();
        }
        return true;
    }

    private void pauseEpisode(EpisodeRealm episode) {
        player.pause(channelRealm, episode);

        episode.setPlayState(EpisodeRealm.PlayState.PAUSE);
        adapter.notifyDataSetChanged();
    }

    private void playEpisode(EpisodeRealm episode) {
        player.play(channelRealm, episode);

        episode.setPlayState(EpisodeRealm.PlayState.PLAYING);
        adapter.notifyDataSetChanged();
    }

    private void tryDownload(EpisodeRealm episode) {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isAvailable() || !networkInfo.isConnected()) {
            new AlertDialog.Builder(this).setMessage(R.string.unavailable_network)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                    .show();
        } else {
            if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                startDownload(episode);
            } else {
                new AlertDialog.Builder(this).setMessage(R.string.download_in_mobile_network)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            startDownload(episode);
                        })
                        .setNegativeButton(android.R.string.no, (dialog, which) -> {
                        })
                        .show();
            }
        }
    }

    private void startDownload(EpisodeRealm episode) {
        episode.setDownloadState(EpisodeRealm.DownloadState.DOWNLOADING);
        adapter.notifyDataSetChanged();

        downloader.enqueue(channelRealm, episode);
    }

    private String getStatusText(EpisodeRealm episode) {
        if (episode.getDownloadState() == EpisodeRealm.DownloadState.NOT_DOWNLOADED) {
            return getString(R.string.episode_not_downloaded);
        } else if (episode.getDownloadState() == EpisodeRealm.DownloadState.DOWNLOADING) {
            return getString(R.string.episode_downloading, episode.getDownloadedBytes() / MEGA_BYTE, episode.getTotalBytes() / MEGA_BYTE);
        } else if (episode.getDownloadState() == EpisodeRealm.DownloadState.FAILED_DOWNLOAD) {
            return getString(R.string.episode_failed_download);
        } else {
            if (episode.getPlayState() == EpisodeRealm.PlayState.PLAYING) {
                return getString(R.string.episode_playing, toTimeFormat(episode.getPlayTimeInMs() / 1000), toTimeFormat(episode.getLength()));
            } else if (episode.getPlayState() == EpisodeRealm.PlayState.PAUSE) {
                return getString(R.string.episode_pause, toTimeFormat(episode.getPlayTimeInMs() / 1000), toTimeFormat(episode.getLength()));
            }
        }
        return "";
    }

    private String toTimeFormat(long seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60);
    }

    private int getStatusImage(EpisodeRealm episode) {
        if (episode.getDownloadState() == EpisodeRealm.DownloadState.NOT_DOWNLOADED
                || episode.getDownloadState() == EpisodeRealm.DownloadState.DOWNLOADING
                || episode.getDownloadState() == EpisodeRealm.DownloadState.FAILED_DOWNLOAD) {
            return R.drawable.download;
        } else if (episode.getDownloadState() == EpisodeRealm.DownloadState.DOWNLOADED) {
            if (episode.getPlayState() == EpisodeRealm.PlayState.NOT_PLAYED
                    || episode.getPlayState() == EpisodeRealm.PlayState.PLAYED) {
                return R.drawable.play;
            } else if (episode.getPlayState() == EpisodeRealm.PlayState.PLAYING) {
                return R.drawable.pause;
            }
        }
        return R.drawable.play;
    }

    private void onDownloadProgress(DownloadProgressParam param) {
        findEpisode(param.identifier())
                .ifPresent(episodeRealm -> {
                    episodeRealm.setDownloadState(EpisodeRealm.DownloadState.DOWNLOADING);
                    episodeRealm.setDownloadedBytes(param.bytesRead());
                    episodeRealm.setTotalBytes(param.contentLength());
                    adapter.notifyDataSetChanged();
                    logger.d("episode=%d, bytes=%d, total=%d", episodeRealm.getId(), param.bytesRead(), param.contentLength());
                });
    }

    private void onDownloadCompleted(DownloadCompleteParam param) {
        findEpisode(param.identifier())
                .ifPresent(episodeRealm -> {
                    episodeRealm.setDownloadState(EpisodeRealm.DownloadState.DOWNLOADED);
                    episodeRealm.setDownloadedPath(param.file().getAbsolutePath());
                    adapter.notifyDataSetChanged();
                });
    }

    private void onDownloadError(DownloadErrorParam param) {
        findEpisode(param.identifier())
                .ifPresent(episodeRealm -> {
                    episodeRealm.setDownloadState(EpisodeRealm.DownloadState.FAILED_DOWNLOAD);
                    adapter.notifyDataSetChanged();

                    Toast.makeText(this, "Download Error: " + param.identifier(), Toast.LENGTH_SHORT).show();
                });
        logger.w(param.throwable());
    }

    private void onMediaPlay(long episodeId) {
        findEpisode(String.valueOf(episodeId)).ifPresent(episodeRealm -> {
            findPlayingEpisodeExcept(episodeRealm.getId())
                    .ifPresent(episodeRealm1 -> {
                        episodeRealm1.setPlayState(EpisodeRealm.PlayState.PAUSE);
                    });
            episodeRealm.setPlayState(EpisodeRealm.PlayState.PLAYING);
            adapter.notifyDataSetChanged();
        });
    }

    private void onMediaPause(long episodeId) {
        findEpisode(String.valueOf(episodeId)).ifPresent(episodeRealm -> {
            episodeRealm.setPlayState(EpisodeRealm.PlayState.PAUSE);
            adapter.notifyDataSetChanged();
        });
    }

    private void onMediaPlayed(long episodeId) {
        findEpisode(String.valueOf(episodeId)).ifPresent(episodeRealm -> {
            episodeRealm.setPlayState(EpisodeRealm.PlayState.PLAYED);
            episodeRealm.setPlayTimeInMs(0);
            adapter.notifyDataSetChanged();
        });
    }

    private void onMediaPlaying(long episodeId, long currentPosition) {
        findEpisode(String.valueOf(episodeId)).ifPresent(episodeRealm -> {
            episodeRealm.setPlayTimeInMs((int) currentPosition);
            adapter.notifyDataSetChanged();
        });
    }

    private Optional<EpisodeRealm> findPlayingEpisodeExcept(long episodeId) {
        return Stream.of(channelRealm.getEpisodes())
                .filter(episodeRealm -> episodeRealm.getPlayState() == EpisodeRealm.PlayState.PLAYING
                        && episodeRealm.getId() != episodeId)
                .findFirst();
    }

    private Optional<EpisodeRealm> findEpisode(String episodeId) {
        if (channelRealm == null) {
            return Optional.empty();
        }
        return Stream.of(channelRealm.getEpisodes())
                .filter(episodeRealm -> episodeRealm.getId() == Long.valueOf(episodeId))
                .findFirst();
    }

    private void registerReceiver() {
        playerBcReceiver.register();
        downloaderBcReceiver.register();
    }

    private void unregisterReceiver() {
        downloaderBcReceiver.unregister();
        playerBcReceiver.unregister();
    }

    public static Intent createIntent(Context context, long id) {
        Intent intent = new Intent(context, ChannelActivity.class);
        intent.putExtra(KEY_ID, id);
        return intent;
    }
}
