package com.battleshippark.bsp_langpod.data.server;

import com.google.auto.value.AutoValue;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;

/**
 */

@AutoValue
public abstract class ChannelJson {
    public static final long DEFAULT_ID = 0;

    public static ChannelJson create(long id, int order, String title, String desc, String image) {
        return new AutoValue_ChannelJson(id, order, title, desc, image);
    }

    public abstract long id();

    public abstract int order();

    public abstract String title();

    public abstract String desc();

    public abstract String image();

    public static TypeAdapter<ChannelJson> typeAdapter(Gson gson) {
        return new AutoValue_ChannelJson.GsonTypeAdapter(gson);
    }
}
