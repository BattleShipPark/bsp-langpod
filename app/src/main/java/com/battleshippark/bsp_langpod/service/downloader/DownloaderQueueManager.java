package com.battleshippark.bsp_langpod.service.downloader;

import com.battleshippark.bsp_langpod.data.db.ChannelRealm;
import com.battleshippark.bsp_langpod.data.db.DownloadDbRepository;
import com.battleshippark.bsp_langpod.data.db.DownloadRealm;
import com.battleshippark.bsp_langpod.data.db.EpisodeRealm;
import com.battleshippark.bsp_langpod.domain.DomainMapper;

import java.util.List;

/**
 */

public class DownloaderQueueManager {
    private static DownloaderQueueManager MANAGER;
    private final DownloaderQueue queue = new DownloaderQueue();
    private final DownloadDbRepository downloadDbApi;
    private final DomainMapper domainMapper;

    public static void create(DownloadDbRepository repository, DomainMapper domainMapper) {
        MANAGER = new DownloaderQueueManager(repository, domainMapper);
    }

    static DownloaderQueueManager getInstance() {
        if (MANAGER == null) {
            throw new RuntimeException();
        }
        return MANAGER;
    }

    DownloaderQueueManager(DownloadDbRepository repository, DomainMapper domainMapper) {
        this.downloadDbApi = repository;
        this.domainMapper = domainMapper;
    }

    void offer(ChannelRealm channelRealm, EpisodeRealm episodeRealm) {
        DownloadRealm downloadRealm = domainMapper.asDownloadRealm(channelRealm, episodeRealm);
        downloadDbApi.insert(downloadRealm).subscribe();
        queue.offer(downloadRealm);
    }

    DownloadRealm peek() throws InterruptedException {
        return queue.peek();
    }

    void remove(DownloadRealm downloadRealm) {
        queue.remove(downloadRealm);
    }

    void clearWith(List<DownloadRealm> downloadRealms) {
        queue.clearWith(downloadRealms);
    }

    void markComplete(DownloadRealm downloadRealm) {
        downloadRealm.setDownloadState(DownloadRealm.DownloadState.DOWNLOADED);
        downloadDbApi.update(downloadRealm).subscribe();
    }

    void markError(DownloadRealm downloadRealm) {
        downloadRealm.setDownloadState(DownloadRealm.DownloadState.FAILED_DOWNLOAD);
        downloadDbApi.update(downloadRealm).subscribe();
    }

    void markDownloading(DownloadRealm downloadRealm) {
        downloadRealm.setDownloadState(DownloadRealm.DownloadState.DOWNLOADING);
        downloadDbApi.update(downloadRealm).subscribe();
    }
}
