package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.GameScraper;
import me.theforbiddenai.gamefinder.utilities.epicgames.GraphQLClient;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Class responsible for retrieving games with a 100% discount from EpicGames
 *
 * @author TheForbiddenAi
 */
public class EpicGamesScraper extends GameScraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    // URLs
    private static final String EPIC_STORE_URL = "https://store.epicgames.com/";
    private static final String EPIC_URL_PREFIX = EPIC_STORE_URL + "en-US/p/";

    private static final int MAX_ENTRIES = 10;

    private final GraphQLClient graphQLClient;

    public EpicGamesScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.EPIC_GAMES);

        this.graphQLClient = new GraphQLClient(objectMapper);
    }

    public EpicGamesScraper(ObjectMapper objectMapper, GraphQLClient graphQLClient) {
        super(objectMapper, Platform.EPIC_GAMES);

        this.graphQLClient = graphQLClient;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            List<ScraperResult> scraperResults = new ArrayList<>();
            JsonNode jNode = retrieveJson();

            for (JsonNode gameJson : jNode) {
                // Convert each element to a game object using jsonToGame,
                Game game = jsonToGame(gameJson);

                // A game object is deemed invalid if it is null or if it is a DLC and DLCs are disabled in the config
                boolean isInvalidGame = game == null || (!CONFIG.includeDLCs() && game.isDLC());

                if (isInvalidGame) continue;

                // Wrap game object in a ScraperResult class and add it to the scraperResults list
                scraperResults.add(new ScraperResult(game));
            }

            return scraperResults;
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from EpicGames", ex);
        }
    }

    /**
     * Converts a JsonNode object to a game object
     *
     * @param gameJson The JsonNode object containing data about a game listing
     * @return A game object, or null if:
     *         the found listing is not a game and includeDLCs is disabled in {@link GameFinderConfiguration}
     *         or there is no discount applied
     */
    private Game jsonToGame(JsonNode gameJson) {
        String offerType = gameJson.get("offerType").asText();
        boolean isDLC = offerType.equalsIgnoreCase("DLC") || offerType.equalsIgnoreCase("ADD_ON");

        JsonNode priceJson = gameJson.get("price");
        JsonNode totalPrice = priceJson.get("totalPrice");

        int discount = totalPrice.get("discountPrice").asInt();

        // Filter out all listings that do not have a 100% discount
        if (discount != 0) return null;

        int priceNoDecimal = totalPrice.get("originalPrice").asInt();
        int decimalCount = totalPrice.get("currencyInfo").get("decimals").asInt();

        Game.GameBuilder gameBuilder = Game.builder()
                .title(gameJson.get("title").asText())
                .description(gameJson.get("description").asText())
                .url(getGameUrl(gameJson, isDLC))
                .originalPrice(priceNoDecimal, decimalCount)
                .isDLC(isDLC)
                .platform(Platform.EPIC_GAMES)
                .expirationEpoch(getOfferExpirationEpoch(priceJson));

        // Add image data
        setGameMedia(gameJson, gameBuilder);

        return gameBuilder.build();
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
     * @param isDLC    Whether the listing is a DLC or nto
     * @return The URL for the game listing, or the epic games store URL if it cannot be found
     */
    private String getGameUrl(JsonNode gameJson, boolean isDLC) {
        // First try to find offer page if it exists
        // Ensure the necessary field exists before accessing it
        if ((isDLC && gameJson.has("urlSlug")) || (!isDLC && gameJson.has("productSlug"))) {
            String slug = isDLC ? gameJson.get("urlSlug").asText("") : gameJson.get("productSlug").asText("");
            if (!slug.isBlank()) return EPIC_URL_PREFIX + slug;
        }

        // If can't find offer page, attempt to find product home page in catalogNs
        JsonNode catalogNs = gameJson.get("catalogNs");

        if (catalogNs != null && catalogNs.has("mappings")) {
            // mappings will only ever have productHome pageTypes due to the way information is requested from GraphQL API
            Iterator<JsonNode> mappingIterator = catalogNs.get("mappings").iterator();
            if (mappingIterator.hasNext()) {
                return EPIC_URL_PREFIX + mappingIterator.next().get("pageSlug").asText();
            }
        }

        // Can't find URL
        return EPIC_STORE_URL;
    }

    /**
     * Gets the expiration epoch for a game listing from its json data
     *
     * @param priceJson The json data for the price object inside the game listing's json
     * @return The epoch second when the offer expires or {@link GameFinderConstants#NO_EXPIRATION_EPOCH} if it can't be found
     */
    private long getOfferExpirationEpoch(JsonNode priceJson) {
        // Retrieve appliedRules json node
        JsonNode lineOffers = priceJson.get("lineOffers");
        if (lineOffers == null) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        // Loop through lineOffers
        for (JsonNode line : lineOffers) {

            // Get the appliedRules for the current lineOffer
            JsonNode appliedRules = line.get("appliedRules");
            if (appliedRules == null) continue;

            // Loop through the rules
            for (JsonNode rule : appliedRules) {
                // Get the discountPercentage
                Optional<Integer> discountPercentage = Optional.ofNullable(rule.get("discountSetting"))
                        .map(node -> node.get("discountPercentage"))
                        .map(JsonNode::asInt);

                // 0 == 100% discount
                // If the discountPercentage isn't there or is not 0 then it is not the right discount, continue
                if (discountPercentage.isEmpty() || discountPercentage.get() != 0) continue;
                String endDate = rule.get("endDate").asText();

                // Return GameFinderConstants.NO_EXPIRATION_EPOCH if not found or end date epoch
                return endDate.isBlank() ? GameFinderConstants.NO_EXPIRATION_EPOCH : Instant.parse(endDate).getEpochSecond();
            }

        }

        // Expiration epoch not found; return GameFinderConstants.NO_EXPIRATION_EPOCH
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * Retrieves the json data for free games on EpicGames
     *
     * @return A JsonNode containing the json data
     * @throws IOException If objectMapper fails to read the data
     */
    private JsonNode retrieveJson() throws IOException {

        Map<String, Object> variables = new HashMap<>();

        String category = "games|bundles";
        // Retrieve addons if DLCs is enabled
        category = CONFIG.includeDLCs() ? category + "|addons" : category;

        variables.put("allowCountries", CONFIG.getLocale().getCountry());
        variables.put("category", category);
        variables.put("count", MAX_ENTRIES);
        variables.put("onSale", true);
        variables.put("sortBy", "currentPrice");
        variables.put("sortDir", "ASC");
        variables.put("start", 0);
        variables.put("freeGame", true);
        variables.put("pageType", "productHome");
        variables.put("withPrice", true);

        return graphQLClient.executeQuery(GraphQLClient.STORE_QUERY, variables).get("data")
                .get("Catalog")
                .get("searchStore")
                .get("elements");
    }

}
