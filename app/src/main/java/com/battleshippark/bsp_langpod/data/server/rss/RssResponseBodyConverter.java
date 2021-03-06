package com.battleshippark.bsp_langpod.data.server.rss;

import com.battleshippark.bsp_langpod.data.server.ChannelJson;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Converter;

final class RssResponseBodyConverter implements Converter<ResponseBody, ChannelJson> {
    private final RssResponseMapper mapper;

    RssResponseBodyConverter(RssResponseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ChannelJson convert(ResponseBody value) throws IOException {
        try (XmlReader reader = new XmlReader(value.byteStream())) {
            SyndFeed feed = new SyndFeedInput().build(reader);
            return mapper.map(feed);
        } catch (FeedException e) {
            throw new IOException(e);
        }
    }
}
