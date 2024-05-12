package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

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

    /**
     * Gets the json data for one steam app listing
     *
     * @param appId The app id of the listing
     * @return An optional containing the json information for the listing if found
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public Optional<JsonNode> getAppDetails(@NonNull String appId) throws IOException {
        // Unfortunately the bulk version of this endpoint was decommissioned
        // Adding multiple appIds results in the API returning null unless you set the filters property to price_overview
        String url = "https://store.steampowered.com/api/appdetails?appids=" + appId + "&cc=US&l=english";
        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(jNode -> jNode.get(appId))
                .map(jNode -> jNode.get("data"));
    }

    /**
     * Gets the json data for the given steam packages
     *
     * @param packageIds The package ids
     * @return The retrieved json information for the packages
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public JsonNode getPackageDetails(@NonNull String... packageIds) throws IOException {
        String delimitedPackageIds = String.join(",", packageIds);
        String url = "https://store.steampowered.com/actions/ajaxresolvepackages?packageids=" + delimitedPackageIds
                + "&cc=US&l=english";
        return mapper.readTree(new URL(url));
    }

    /**
     * Gets the json data for the given steam packages
     *
     * @param bundleId The bundle ids
     * @return The retrieved json information for the bundles
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public JsonNode getBundleDetails(@NonNull String... bundleId) throws IOException {
        String delimitedBundleIds = String.join(",", bundleId);
        String url = "https://store.steampowered.com/actions/ajaxresolvebundles?bundleids=" + delimitedBundleIds
                + "&cc=US&l=english";
        return mapper.readTree(new URL(url));
    }

    /**
     * Gets the json data for clan events (i.e. publisher sales)
     *
     * @param clanId The clan id
     * @return An optional containing the json information for the events if found
     * @throws IOException If the mapper is unable to parse the json information, or if the URL is malformed
     */
    public Optional<JsonNode> getEventDetails(@NonNull String clanId) throws IOException {
        String url = "https://store.steampowered.com/events/ajaxgetadjacentpartnerevents/?clan_accountid="
                + clanId + "&cc=US&l=english";
        return Optional.ofNullable(mapper.readTree(new URL(url)))
                .map(jNode -> jNode.get("events"));
    }


}
