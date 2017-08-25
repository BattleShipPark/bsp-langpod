package com.battleshippark.bsp_langpod.data.db;

import com.annimon.stream.Stream;

import java.util.List;

import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;
import rx.Completable;
import rx.Observable;

/**
 */

public class ChannelDbApi implements ChannelDbRepository {
    private final Realm realm;

    public ChannelDbApi() {
        this.realm = Realm.getDefaultInstance();
    }

    public ChannelDbApi(Realm realm) {
        this.realm = realm;
    }

    @Override
    public Observable<List<ChannelRealm>> entireChannelList() {
        return Observable.create(subscriber -> {
            try {
                RealmQuery<ChannelRealm> query = realm.where(ChannelRealm.class);
                RealmResults<ChannelRealm> results = query.findAllSorted("order");
                subscriber.onNext(results);
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Observable<List<ChannelRealm>> myChannelList() {
        return Observable.create(subscriber -> {
            try {
                RealmQuery<ChannelRealm> query = realm.where(ChannelRealm.class);
                RealmResults<ChannelRealm> results = query.equalTo(ChannelRealm.FIELD_SUBSCRIBED, true)
                        .findAllSorted(ChannelRealm.FIELD_ORDER);
                subscriber.onNext(realm.copyFromRealm(results));
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Observable<List<ChannelRealm>> channel(long id) {
        return Observable.create(subscriber -> {
            try {
                RealmResults<ChannelRealm> results = realm.where(ChannelRealm.class).equalTo("id", id).findAll();
                subscriber.onNext(results);
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Observable<ChannelRealm> channelWithEpisodeId(long episodeId) {
        return Observable.create(subscriber -> {
            try {
                RealmResults<ChannelRealm> results = realm.where(ChannelRealm.class).equalTo("episodes.id", episodeId).findAll();
                subscriber.onNext(realm.copyFromRealm(results.get(0)));
                subscriber.onCompleted();
            } catch (Exception e) {
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Completable putEntireChannelList(List<ChannelRealm> realmList) {
        return Completable.create(subscriber -> {
            realm.beginTransaction();
            try {
                realm.delete(ChannelRealm.class);
                Stream.of(realmList).forEach(realm::insertOrUpdate);

                realm.commitTransaction();
                subscriber.onCompleted();
            } catch (Exception e) {
                realm.cancelTransaction();
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Completable putChannel(ChannelRealm channelRealm) {
        return Completable.create(subscriber -> {
            realm.beginTransaction();
            try {
                realm.insertOrUpdate(channelRealm);

                realm.commitTransaction();
                subscriber.onCompleted();
            } catch (Exception e) {
                realm.cancelTransaction();
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Completable putEpisode(EpisodeRealm episodeRealm) {
        return Completable.create(subscriber -> {
            realm.beginTransaction();
            try {
                realm.insertOrUpdate(episodeRealm);
                realm.commitTransaction();
                subscriber.onCompleted();
            } catch (Exception e) {
                realm.cancelTransaction();
                subscriber.onError(e);
            }
        });
    }

    @Override
    public Completable switchSubscribe(ChannelRealm channelRealm) {
        return Completable.create(subscriber -> {
            long id = channelRealm.getId();
            boolean value = !channelRealm.isSubscribed();
            realm.beginTransaction();
            try {
                ChannelRealm newChannelRealm = realm.where(ChannelRealm.class).equalTo(ChannelRealm.FIELD_ID, id).findFirst();
                newChannelRealm.setSubscribed(value);
                realm.commitTransaction();
                subscriber.onCompleted();
            } catch (Exception e) {
                realm.cancelTransaction();
                subscriber.onError(e);
            }
        });
    }
}
