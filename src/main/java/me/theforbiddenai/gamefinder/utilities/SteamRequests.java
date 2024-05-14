package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

public class SteamRequests {

    @Getter(value = AccessLevel.PROTECTED)
    private final ObjectMapper mapper;

    public SteamRequests(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Gets a json list of games and DLCs ids with a 100% off discount
     *
     * @return An optional containing the json information
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public Optional<JsonNode> getFreeGames() throws IOException {
        // Testing URL: https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=5&specials=1&json=1
        // Production URL: https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=free&specials=1&json=1
        String url = "https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=free&specials=1&json=1";
        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(node -> node.get("items"));
    }

    public Optional<JsonNode> getItems(String jsonIdList) throws IOException {
        // See https://steamapi.xpaw.me/#IStoreBrowseService/GetItems for more info
        // Note: You do not need an access key despite it saying you do
        String url = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1" +
                "?input_json={\"ids\":[" + jsonIdList + "]," +
                "\"context\":{\"language\":\"english\",\"country_code\":\"US\",\"steam_realm\":1}," +
                "\"data_request\":{\"include_basic_info\":true,\"include_assets\":true,\"include_screenshots\":true}}";

        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(node -> node.get("response"))
                .map(node -> node.get("store_items"));
    }


}
