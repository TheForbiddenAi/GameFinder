package io.github.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.theforbiddenai.gamefinder.GameFinderConfiguration;
import io.github.theforbiddenai.gamefinder.exception.GameRetrievalException;
import io.github.theforbiddenai.gamefinder.scraper.GameScraper;
import io.github.theforbiddenai.gamefinder.domain.Game;
import io.github.theforbiddenai.gamefinder.domain.Platform;
import io.github.theforbiddenai.gamefinder.domain.ScraperResult;
import io.github.theforbiddenai.gamefinder.utilities.gog.GOGRequests;
import io.github.theforbiddenai.gamefinder.webscraper.GOGWebScraper;

import java.io.IOException;
import java.util.*;

/**
 * Class responsible for retrieving games with a 100% discount from GOG
 * GOG only outputs currency in USD and CAD. Currently, this will only retrieve currency in USD
 *
 * @author TheForbiddenAi
 */
public class GOGScraper extends GameScraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    // First %s is for language, second %s is for the game slug
    private static final String GOG_GAME_URL_FORMAT = "https://www.gog.com/%s/game/%s";

    private final GOGRequests gogRequests;
    private final GOGWebScraper gogWebScraper;

    public GOGScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.GOG);

        this.gogRequests = new GOGRequests(objectMapper);
        this.gogWebScraper = new GOGWebScraper(objectMapper);
    }

    public GOGScraper(ObjectMapper objectMapper, GOGRequests gogRequests, GOGWebScraper gogWebScraper) {
        super(objectMapper, Platform.GOG);

        this.gogRequests = gogRequests;
        this.gogWebScraper = gogWebScraper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            // Retrieve data
            Optional<JsonNode> gameListOptional = gogRequests.getGameList();
            Map<String, JsonNode> giveawayNodes = getGiveawayNodes();

            // Make sure there is data to process
            if (gameListOptional.isEmpty() && giveawayNodes.isEmpty()) return Set.of();

            Set<ScraperResult> scraperResultSet = new HashSet<>();

            // Convert the values in giveaway nodes to ScraperResults and add the nonnull objects to the scraperResultSet list
            giveawayNodes.values()
                    .stream()
                    .map(this::getResultFromJson)
                    .filter(Objects::nonNull)
                    .forEach(scraperResultSet::add);

            // There are no more games to process, return scraperResultSet
            if (gameListOptional.isEmpty()) return scraperResultSet;

            // Loop through gameList JsonNodes
            for (JsonNode gameNode : gameListOptional.get()) {
                // Make sure this game isn't listed as a GIVEAWAY
                String id = gameNode.get("id").asText();
                if (giveawayNodes.containsKey(id)) continue;

                // Convert the gameNode to a ScraperResult and add it to the list if it isn't null
                ScraperResult scraperResult = getResultFromJson(gameNode);
                if (scraperResult != null) scraperResultSet.add(scraperResult);
            }

            return scraperResultSet;
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from GOG", ex);
        }
    }

    /**
     * Gives all of Json product nodes associated with 100% off game giveaways
     *
     * @return A map where the game id is the key and the json product node is the value
     * @throws IOException If there is an issue retrieving a section or parsing the json data
     */
    private Map<String, JsonNode> getGiveawayNodes() throws IOException {
        // Retrieve home page sections
        Optional<JsonNode> homePageSections = gogRequests.getHomePageSections();
        if (homePageSections.isEmpty()) return Map.of();

        List<String> giveawaySectionIds = new ArrayList<>();

        // Loop through home page sections to find GIVEAWAY_SECTION ids
        for (JsonNode homePageSectionNode : homePageSections.get()) {
            String type = homePageSectionNode.get("sectionType").asText();

            // Make sure it is a giveaway section
            if (!type.equalsIgnoreCase("GIVEAWAY_SECTION")) continue;
            giveawaySectionIds.add(homePageSectionNode.get("sectionId").asText());
        }

        Map<String, JsonNode> giveawaySectionMap = new HashMap<>();

        // Loop through the giveaway section ids
        for (String sectionId : giveawaySectionIds) {
            // Get the section json information
            Optional<JsonNode> productionOptional = gogRequests.getHomePageSection(sectionId)
                    .map(giveawaySection -> giveawaySection.get("product"));
            // Make sure the data was retrieved
            if (productionOptional.isEmpty()) continue;

            // Get the product json
            JsonNode product = productionOptional.get();

            // Add the product json to the map
            giveawaySectionMap.put(product.get("id").asText(), product);
        }

        return giveawaySectionMap;
    }

    /**
     * Creates a Game object from the provided JsonNode and wraps it in a ScraperResult
     *
     * @param gameNode The JsonNode containing the information for the Game object
     * @return A ScraperResult containing the game in the form of a CompletableFuture
     */
    private ScraperResult getResultFromJson(JsonNode gameNode) {
        String productType = gameNode.get("productType")
                .asText()
                .toLowerCase();

        boolean isDLC = productType.equals("dlc") || productType.equals("extra");

        // Make sure that DLCs are enabled before continuing on with DLC object parsing
        if (!CONFIG.includeDLCs() && isDLC) return null;

        Map<String, String> storeMedia = new HashMap<>();

        Optional.ofNullable(gameNode.get("coverHorizontal"))
                .ifPresent(coverHorizontal ->  storeMedia.put("coverHorizontal", coverHorizontal.asText()));

        Optional.ofNullable(gameNode.get("coverVertical"))
                .ifPresent(coverHorizontal ->  storeMedia.put("coverVertical", coverHorizontal.asText()));

        // I use slug instead of storeLink because the giveaway object does not contain a storeLink object
        String urlSlug = gameNode.get("slug").asText();
        String url = String.format(GOG_GAME_URL_FORMAT, CONFIG.getLocale().getLanguage(), urlSlug);

        // Price is not set here because it is horribly unreliable and spits out inaccurate information
        Game game = Game.builder()
                .title(gameNode.get("title").asText())
                .url(url)
                .isDLC(isDLC)
                .storeMedia(storeMedia)
                .media(getScreenshots(gameNode))
                .platform(Platform.GOG)
                .build();

        return new ScraperResult(gogWebScraper.modifyGameAttributes(game));
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
