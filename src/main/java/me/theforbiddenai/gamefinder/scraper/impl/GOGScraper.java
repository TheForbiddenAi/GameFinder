package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.GameScraper;
import me.theforbiddenai.gamefinder.utilities.gog.GOGRequests;
import me.theforbiddenai.gamefinder.webscraper.GOGWebScrape;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class GOGScraper extends GameScraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    // First %s is for language, second %s is for the game slug
    private static final String GOG_GAME_URL_FORMAT = "https://www.gog.com/%s/game/%s";

    private final GOGRequests gogRequests;
    private final GOGWebScrape webScrape;

    public GOGScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.GOG);

        this.webScrape = new GOGWebScrape(objectMapper);
        this.gogRequests = new GOGRequests(objectMapper);
    }

    @Override
    public List<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            Optional<JsonNode> gameListNode = gogRequests.getGameList();

            /*
             * Check homepages for GIVEAWAY_SECTION
             * If exists, extract the game
             *
             * Then check game list
             * Combine into one list (make sure no duplicates, utilize id map or smth)
             * Get as many details as can from given objects
             * Web scrape the rest
             */


        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from GOG", ex);
        }
        return List.of();
    }

    private CompletableFuture<Game> retrieveGameFromGOG(JsonNode gameListNode) {

        String productType = gameListNode.get("productType")
                .asText()
                .toLowerCase();

        boolean isDLC = productType.equals("dlc") || productType.equals("extra");

        // Make sure that DLCs are enabled before continuing on with DLC object parsing
        if (!CONFIG.includeDLCs() && isDLC) return null;

        Map<String, String> storeMedia = new HashMap<>();
        storeMedia.put("coverHorizontal", gameListNode.get("coverHorizontal").asText());
        storeMedia.put("coverVertical", gameListNode.get("coverVertical").asText());

        // I use slug instead of storeLink because the giveaway object does not contain a storeLink object
        String urlSlug = gameListNode.get("slug").asText();
        String url = String.format(GOG_GAME_URL_FORMAT, CONFIG.getLocale().getLanguage(), urlSlug);

        Game game = Game.builder()
                .title(gameListNode.get("title").asText())
                .url(url)
                .isDLC(isDLC)
                .storeMedia(storeMedia)
                .media(getScreenshots(gameListNode))
                .platform(Platform.GOG)
                .build();

        return webScrape.modifyGameAttributes(game);
    }

    /**
     * Gets the screenshot URL list from a gameListNode
     *
     * @param gameListNode The gameListNode
     * @return A list of screenshot URLs
     */
    private List<String> getScreenshots(JsonNode gameListNode) {
        JsonNode screenshotListNode = gameListNode.get("screenshots");
        if (screenshotListNode == null) return List.of();

        List<String> screenshotList = new ArrayList<>();
        // Loop through the screenshots
        for (JsonNode screenshotNode : screenshotListNode) {
            // Remove formatter block so the URL is valid
            String url = screenshotNode.asText().replace("_{formatter}", "");
            // Add the screenshot url to the list if it is not blank
            if (!url.isBlank()) screenshotList.add(url);
        }

        return screenshotList;
    }

}
