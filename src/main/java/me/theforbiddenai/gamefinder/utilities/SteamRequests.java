package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;

/**
 * Responsible for making requests to undocumented Steam API endpoints
 *
 * @author TheForbiddenAi
 */
public class SteamRequests {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    @Getter(value = AccessLevel.PROTECTED)
    private final ObjectMapper mapper;

    public SteamRequests(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // TODO: Add ratelimiter to prevent

    /**
     * Gets a json list of games and DLCs ids with a 100% off discount
     *
     * @return An optional containing the json information if found
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public Optional<JsonNode> getFreeGames() throws IOException {
        // Testing URL: https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=5&specials=1&json=1
        // Production URL: https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=free&specials=1&json=1
        String url = "https://store.steampowered.com/search/results/?ignore_preferences=1&maxprice=free&specials=1&json=1";
        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(node -> node.get("items"));
    }

    /**
     * Gets json information for apps, packages, and bundles
     *
     * @param jsonIdList The ids of the apps package and bundles in the correct form (i.e. {"appId":123})
     * @return An optional containing the json information if found
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public Optional<JsonNode> getItems(String jsonIdList) throws IOException {
        // See https://steamapi.xpaw.me/#IStoreBrowseService/GetItems for more info
        // Note: You do not need an access key despite it saying you do. It also does not need to be protobuf encoded
        Locale locale = CONFIG.getLocale();

        String url = "https://api.steampowered.com/IStoreBrowseService/GetItems/v1" +
                "?input_json={\"ids\":[" + jsonIdList + "],\"context\":" +
                "{\"language\":\"" + locale.getDisplayLanguage() + "\",\"country_code\":\"" + locale.getCountry() +
                "\",\"steam_realm\":1},\"data_request\":{\"include_basic_info\":true,\"include_assets\":true,\"include_screenshots\":true}}";

        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(node -> node.get("response"))
                .map(node -> node.get("store_items"));
    }


}
