package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.Scraper;
import me.theforbiddenai.gamefinder.utilities.SteamAppToGame;
import me.theforbiddenai.gamefinder.utilities.SteamRequests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SteamScraper extends Scraper {

    private final SteamRequests steamRequests;
    private final SteamAppToGame steamAppToGame;

    public SteamScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.STEAM);

        this.steamRequests = new SteamRequests(objectMapper);
        this.steamAppToGame = new SteamAppToGame(steamRequests);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Game> retrieveGames() throws GameRetrievalException {
        try {
            Optional<JsonNode> gameListOptional = steamRequests.getFreeGames();
            if (gameListOptional.isEmpty())
                throw new GameRetrievalException("Unable to retrieve games lists from Steam");

            JsonNode gameList = gameListOptional.get();
            List<Game> retrievedGames = new ArrayList<>();

            for (JsonNode gameNode : gameList) {
                processGameNode(gameNode).ifPresent(retrievedGames::add);
            }

            return retrievedGames;
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from Steam", ex);
        }
    }

    /**
     * Converts a game node to a game object; handles logic of determining which conversion class to use
     *
     * @param gameNode The json information of the listing (name and logo url)
     * @return An optional containing the game or an empty option if conversion fails or no url is found
     * @throws IOException If json parsing fails or URL is malformed
     */
    private Optional<Game> processGameNode(JsonNode gameNode) throws IOException {
        String logoUrl = gameNode.get("logo").asText();
        if (logoUrl == null) return Optional.empty();

        // Bulk request for subs/bundles
        if (logoUrl.contains("app")) {
            // Extract app id from logo url
            int appsIndex = logoUrl.indexOf("apps/");
            int lastForwardSlashIndex = logoUrl.lastIndexOf("/");
            String appId = logoUrl.substring(appsIndex + 5, lastForwardSlashIndex);

            return steamAppToGame.convertAppToGame(appId);
        }

        // TODO: subs (packages), and bundles

        return Optional.empty();
    }

}
