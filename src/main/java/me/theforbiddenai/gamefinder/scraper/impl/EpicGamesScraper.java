package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.Scraper;

import java.io.IOException;
import java.net.URL;
import java.time.Instant;
import java.util.*;

/**
 * Class responsible for retrieving games with a 100% discount from EpicGames
 *
 * @author TheForbiddenAi
 */
public class EpicGamesScraper extends Scraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    // URLs
    private static final String JSON_URL = "https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=en-US";
    private static final String EPIC_STORE_URL = "https://store.epicgames.com/";
    private static final String EPIC_URL_PREFIX = EPIC_STORE_URL + "en-US/p/";

    public EpicGamesScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.EPIC_GAMES);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            JsonNode jNode = retrieveJson();

            List<ScraperResult> scraperResults = new ArrayList<>();

            // Loop through jNode elements,
            jNode.forEach(gameJson ->
                    // Convert each element to a game object using jsonToGame,
                    // then wrap the nonnull objects in a ScraperResult class and add them to scraperResults list
                    jsonToGame(gameJson).ifPresent(game -> scraperResults.add(new ScraperResult(game)))
            );

            return scraperResults;
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from EpicGames", ex);
        }
    }

    /**
     * Converts a JsonNode object to a game object
     *
     * @param gameJson The JsonNode object containing data about a game listing
     * @return An optional containing the game object, or an empty optional if:
     *         the found listing is not a game and includeDLCs is disabled in {@link GameFinderConfiguration}
     *         or there is no discount applied
     */
    private Optional<Game> jsonToGame(JsonNode gameJson) {
        String offerType = gameJson.get("offerType").asText();
        boolean isDLC = !offerType.equalsIgnoreCase("BASE_GAME");

        // Filter out all non games if includeDLCs is disabled
        if (!CONFIG.includeDLCs() && isDLC) return Optional.empty();

        JsonNode price = gameJson.get("price")
                .get("totalPrice");

        int discount = price.get("discount").asInt();

        // Filter out all listings with a discount of 0
        // This includes the "Mystery Game" postings EpicGames shows before the game is announced
        if (discount == 0) return Optional.empty();

        Game.GameBuilder gameBuilder = Game.builder()
                .title(gameJson.get("title").asText())
                .description(gameJson.get("description").asText())
                .url(getGameUrl(gameJson))
                .isDLC(isDLC)
                .platform(Platform.EPIC_GAMES)
                .expirationEpoch(getOfferExpirationEpoch(gameJson));

        // Add image data
        setGameMedia(gameJson, gameBuilder);

        return Optional.ofNullable(gameBuilder.build());
    }

    /**
     * Retrieves store media and game media and adds it to the game builder
     *
     * @param gameJson    The JsonNode object containing data about a game listing
     * @param gameBuilder The GameBuilder being updated
     */
    private void setGameMedia(JsonNode gameJson, Game.GameBuilder gameBuilder) {
        JsonNode keyImages = gameJson.get("keyImages");
        if (keyImages == null) return;

        Map<String, String> storeImages = new HashMap<>();
        List<String> media = new ArrayList<>();

        // Loop through keyImage elements and sort them into storeImages and media objects
        for (JsonNode imageNode : keyImages) {
            String type = imageNode.get("type").asText();
            String url = imageNode.get("url").asText();

            // This adds all in game screenshots to the media list
            if (type.equalsIgnoreCase("featuredMedia")) {
                media.add(url);
                continue;
            }

            // Add all non-in-game screenshots to storeImages; I've never seen any field other than featuredMedia repeated
            storeImages.put(type, url);
        }

        // Add data to game builder
        gameBuilder.storeMedia(storeImages);
        gameBuilder.media(media);
    }

    /**
     * Gets the store page URL for a game listing from its json data
     *
     * @param gameJson The json data for the game listing
     * @return The URL for the game listing, or the epic games store URL if it cannot be found
     */
    private String getGameUrl(JsonNode gameJson) {
        // First try to find offer page if it exists
        JsonNode offerMappings = gameJson.get("offerMappings");

        // This loop should in theory only ever find one or no items. I've yet to see one with more than one
        for (JsonNode offer : offerMappings) {
            if (!offer.has("pageSlug")) continue;
            return EPIC_URL_PREFIX + offer.get("pageSlug").asText();
        }

        // If can't find offer page, attempt to find home page
        JsonNode catalogNs = gameJson.get("catalogNs");

        if (catalogNs.has("mappings")) {
            // This loop should in theory only ever find one or no items. I've yet to see one with more than one
            for (JsonNode mapping : catalogNs.get("mappings")) {
                if (!mapping.has("pageSlug")) continue;
                return EPIC_URL_PREFIX + mapping.get("pageSlug").asText();
            }
        }

        // Can't find URL
        return EPIC_STORE_URL;
    }

    /**
     * Gets the expiration epoch for a game listing from its json data
     *
     * @param gameJson The json data for the game listing
     * @return The epoch second when the offer expires or {@link GameFinderConstants#NO_EXPIRATION_EPOCH} if it can't be found
     */
    private long getOfferExpirationEpoch(JsonNode gameJson) {
        // Retrieve endDate string (empty if not found)
        String endDate = Optional.ofNullable(gameJson.get("promotions"))
                .map(node -> node.get("promotionalOffers"))
                .map(node -> node.elements().next())
                .map(node -> node.get("promotionalOffers"))
                .map(node -> node.elements().next())
                .map(node -> node.get("endDate").asText())
                .orElse("");

        // Return GameFinderConstants.NO_EXPIRATION_EPOCH if not found or end date epoch
        return endDate.isBlank() ? GameFinderConstants.NO_EXPIRATION_EPOCH : Instant.parse(endDate).getEpochSecond();
    }

    /**
     * Retrieves the json data for free games on EpicGames
     *
     * @return A JsonNode containing the json data
     * @throws IOException If objectMapper fails to read the data;
     */
    private JsonNode retrieveJson() throws IOException {
        JsonNode jNode = super.getObjectMapper().readTree(new URL(JSON_URL));
        return jNode.get("data")
                .get("Catalog")
                .get("searchStore")
                .get("elements");
    }

}
