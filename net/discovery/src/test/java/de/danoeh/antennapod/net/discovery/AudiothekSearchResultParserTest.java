package de.danoeh.antennapod.net.discovery;

import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class AudiothekSearchResultParserTest {

    @Test
    public void parseProgramSets_createsProgramSetUrlAndMapsFields() {
        String json = "{\n"
                + "  \"data\": {\n"
                + "    \"search\": {\n"
                + "      \"programSets\": {\n"
                + "        \"nodes\": [\n"
                + "          {\n"
                + "            \"id\": \"urn:ard:show:44b142e0544f2807\",\n"
                + "            \"rowId\": \"69106044\",\n"
                + "            \"title\": \"Example Show\",\n"
                + "            \"image\": {\"url1X1\": \"https://img.example/{width}.jpg\"},\n"
                + "            \"publicationService\": {\"organizationName\": \"WDR\"}\n"
                + "          }\n"
                + "        ]\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}";

        JSONObject root = new JSONObject(json);
        String rssTemplate = "https://api.ardaudiothek.de/programsets/%s";

        List<PodcastSearchResult> results = AudiothekSearchResultParser.parseProgramSets(root, rssTemplate);
        assertEquals(1, results.size());

        PodcastSearchResult result = results.get(0);
        assertEquals("Example Show", result.title);
        assertEquals("WDR", result.author);
        assertEquals("https://api.ardaudiothek.de/programsets/69106044", result.feedUrl);
        assertNotNull(result.imageUrl);
        assertEquals("https://img.example/128.jpg", result.imageUrl);
    }
}
