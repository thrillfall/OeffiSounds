package de.danoeh.antennapod.parser.feed;

import de.danoeh.antennapod.parser.feed.util.TypeGetter;
import org.apache.commons.io.input.XmlStreamReader;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import de.danoeh.antennapod.model.feed.Feed;

public class FeedHandler {
    public FeedHandlerResult parseFeed(Feed feed) throws SAXException, IOException,
            ParserConfigurationException, UnsupportedFeedtypeException {
        File file = new File(feed.getLocalFileUrl());
        if (isJsonFile(file)) {
            try {
                return AudiothekJsonFeedParser.parse(feed);
            } catch (org.json.JSONException e) {
                throw new IOException(e);
            }
        }

        TypeGetter tg = new TypeGetter();
        TypeGetter.Type type = tg.getType(feed);
        SyndHandler handler = new SyndHandler(feed, type);

        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser saxParser = factory.newSAXParser();
        Reader inputStreamReader = new XmlStreamReader(file);
        InputSource inputSource = new InputSource(inputStreamReader);

        saxParser.parse(inputSource, handler);
        inputStreamReader.close();
        return new FeedHandlerResult(handler.state.feed, handler.state.alternateUrls, handler.state.redirectUrl);
    }

    private static boolean isJsonFile(File file) {
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            int i = 0;
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) {
                i++;
            }
            return i < content.length() && content.charAt(i) == '{';
        } catch (Exception e) {
            return false;
        }
    }
}
