package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.GameScraper;
import me.theforbiddenai.gamefinder.utilities.steam.SteamRequests;
import me.theforbiddenai.gamefinder.webscraper.SteamWebScrape;

import java.io.IOException;
import java.util.*;

/**
 * Class responsible for retrieving games with a 100% discount from Steam
 *
 * @author TheForbiddenAi
 */
public class SteamScraper extends GameScraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private static final String STEAM_STORE_URL = "https://store.steampowered.com/";
    private static final String STEAM_CDN_URL = "https://cdn.cloudflare.steamstatic.com/";

    private static final int CURRENCY_DECIMAL_COUNT = 2;

    private final SteamRequests steamRequests;
    private final SteamWebScrape steamWebScrape;

    public SteamScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.STEAM);

        this.steamRequests = new SteamRequests(objectMapper);
        this.steamWebScrape = new SteamWebScrape();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            Optional<JsonNode> gameListOptional = steamRequests.getFreeGames();
            if (gameListOptional.isEmpty())
                throw new GameRetrievalException("Unable to retrieve games lists from Steam");

            // Get game list (json objects containing just name and url)
            JsonNode gameList = gameListOptional.get();

            // Pull out ids and format them to work with /IStoreBrowseService/GetItems/v1 endpoint
            String jsonIdList = joinIds(gameList.elements());

            // Submit request to /IStoreBrowseService/GetItems/v1 endpoint
            Optional<JsonNode> itemListNodeOptional = steamRequests.getItems(jsonIdList);

            // Make sure data was returned, if not return empty list
            if (itemListNodeOptional.isEmpty()) return List.of();

            JsonNode itemListNode = itemListNodeOptional.get();

            List<ScraperResult> results = new ArrayList<>();
            // Convert each itemNode to ScraperResult and add to results list
            itemListNode.forEach(itemNode -> {
                ScraperResult scraperResult = convertItemNodeToScrapperResult(itemNode);
                if (scraperResult != null) results.add(scraperResult);
            });

            return results;
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from Steam", ex);
        }
    }

    /**
     * Converts an itemNode returned by /IStoreBrowseService/GetItems/v1 endpoint to a ScrapperResult
     *
     * @param itemNode The JsonNode being converted
     * @return A ScrapperResult containing the game or a future game, or null if the game is not free
     */
    private ScraperResult convertItemNodeToScrapperResult(JsonNode itemNode) {
        // I could technically check using itemNode.get("is_free_temporarily")
        // However, I do not trust that it will appear everytime. I know this will always be there
        boolean isFree = Optional.ofNullable(itemNode.get("best_purchase_option"))
                .map(node -> node.get("discount_pct"))
                .map(node -> node.asInt(0))
                .orElse(0) == 100;

        if (!isFree) return null;

        // A game is a dlc if it's itemNode has a related_items object containing a parent_appid field
        boolean isDLC = Optional.ofNullable(itemNode.get("related_items"))
                .map(node -> node.get("parent_appid") != null)
                .orElse(false);

        // Make sure that includeDLCs is enabled if game is a DLC
        if (isDLC && !CONFIG.includeDLCs()) return null;

        // Form steam store url for the listing
        String gameUrl = STEAM_STORE_URL + itemNode.get("store_url_path").asText("");

        // Get short description from basic_info. Bundles do not have descriptions
        String description = Optional.ofNullable(itemNode.get("basic_info"))
                .map(node -> node.get("short_description"))
                .map(JsonNode::asText)
                .orElse("N/A");

        Optional<Integer> priceNoDecimalOptional = Optional.ofNullable(itemNode.get("best_purchase_option"))
                .map(node -> node.get("original_price_in_cents"))
                .map(JsonNode::asInt);

        // Build game from information available in itemNode
        Game.GameBuilder gameBuilder = Game.builder()
                .title(itemNode.get("name").asText())
                .description(description)
                .url(gameUrl)
                .isDLC(isDLC)
                .platform(Platform.STEAM)
                .storeMedia(getStoreMedia(itemNode))
                .media(getScreenshots(itemNode));

        // If priceNoDecimal exists format the price and set it
        priceNoDecimalOptional.ifPresent(price -> gameBuilder.originalPrice(price, CURRENCY_DECIMAL_COUNT));

        return getResultWithExpirationEpoch(itemNode, gameBuilder.build());
    }

    /**
     * Gets the expiration epoch for a listing either from the itemNode or web scraping (if enabled in config)
     * Preference is given to itemNode as it is significantly faster. Then wraps the result in a ScraperResult class
     *
     * @param itemNode The JsonNode containing the information about the listing
     * @param game     The game object with all other information inputted already
     * @return A ScraperResult containing the game or future game
     */
    private ScraperResult getResultWithExpirationEpoch(JsonNode itemNode, Game game) {
        long expirationEpoch = extractDiscountEndDate(itemNode);

        // If the expiration epoch is found, set the epoch and return a ScraperResult with a game object
        if (expirationEpoch != GameFinderConstants.NO_EXPIRATION_EPOCH) {
            game.setExpirationEpoch(expirationEpoch);
            return new ScraperResult(game);
        }

        // Use web scraping to find the expiration epoch
        // and return a ScrapperResult with a CompletableFuture<Game> object
        return new ScraperResult(steamWebScrape.modifyGameAttributes(game));
    }

    /**
     * Pulls the expirationEpoch for the discount that is 100% off, if it exists, from a itemNode
     *
     * @param itemNode The JsonNode containing the information about the listing
     * @return The found expirationEpoch or GameFinderConstants.NO_EXPIRATION_EPOCH
     */
    private long extractDiscountEndDate(JsonNode itemNode) {
        JsonNode bestPurchaseOption = itemNode.get("best_purchase_option");

        // Pull out active_discounts node
        JsonNode activeDiscounts = Optional.ofNullable(bestPurchaseOption)
                .map(node -> node.get("active_discounts"))
                .orElse(null);

        if (activeDiscounts == null) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        // Get the original price in cents. If it doesn't exist or is blank, return GameFinderConstants.NO_EXPIRATION_EPOCH
        Optional<Integer> originalPriceInCents = Optional.ofNullable(bestPurchaseOption.get("original_price_in_cents"))
                .map(JsonNode::asInt);

        // Validate there is a price
        if (originalPriceInCents.isEmpty()) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        // Loop through active discounts (unsure if it's possible for there to be more than one)
        for (JsonNode activeDiscount : activeDiscounts) {
            // Get discount amount
            long discountAmount = Optional.ofNullable(activeDiscount.get("discount_amount"))
                    .map(JsonNode::asLong)
                    .orElse(GameFinderConstants.NO_EXPIRATION_EPOCH);

            // Ensure that this is the correct discount by verifying that it is 100% off
            // by comparing the discountAmount to the originalPriceInCents
            if (discountAmount == originalPriceInCents.get()) {
                // Get the expirationEpoch if it exists, otherwise return GameFinderConstants.NO_EXPIRATION_EPOCH;
                Optional<Long> expirationEpoch = Optional.ofNullable(activeDiscount.get("discount_end_date"))
                        .map(JsonNode::asLong);
                // Return the found expirationEpoch only if it is not GameFinderConstants.NO_EXPIRATION_EPOCH
                if (expirationEpoch.isPresent()) return expirationEpoch.get();
            }
        }

        // No epoch found
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * Pulls store assets urls from a JsonNode and stores them in a map
     *
     * @param itemNode The item node containing the assets
     * @return A map of asset names to asset urls
     */
    private Map<String, String> getStoreMedia(JsonNode itemNode) {
        JsonNode assetsNode = itemNode.get("assets");
        if (assetsNode == null) return Map.of();

        // Get asset_url_format or return a blank string
        String assetUrlFormat = Optional.ofNullable(assetsNode.get("asset_url_format"))
                .map(JsonNode::asText)
                .orElse("");

        // Can't form urls without the asset url format
        if (assetUrlFormat.isBlank()) return Map.of();

        String cdnUrlFormat = STEAM_CDN_URL + assetUrlFormat;

        Map<String, String> storeMedia = new HashMap<>();

        // Loop through the fields in the assetsNode
        Iterator<Map.Entry<String, JsonNode>> fieldIterator = assetsNode.fields();
        while (fieldIterator.hasNext()) {
            Map.Entry<String, JsonNode> field = fieldIterator.next();

            JsonNode fieldNode = field.getValue();

            // Only want strings; realistically there should only be strings here
            if (fieldNode.getNodeType() != JsonNodeType.STRING) continue;

            String fieldValue = fieldNode.asText("");

            // Make sure fieldValue leads to a file
            if (!fieldValue.contains(".")) continue;

            // Replace filename placeholder with fieldValue
            String url = cdnUrlFormat.replace("${FILENAME}", fieldValue);
            storeMedia.put(field.getKey(), url);
        }

        return storeMedia;
    }

    /**
     * Gets all screenshot urls from an item node
     *
     * @param itemNode The item node containing the screenshots
     * @return A list of screenshots urls
     */
    private List<String> getScreenshots(JsonNode itemNode) {
        List<String> screenshots = new ArrayList<>();

        // Retrieve screenshots JsonNode and make sure it exists
        JsonNode screenshotsNode = itemNode.get("screenshots");
        if (screenshotsNode == null) return screenshots;

        // Extract screenshots from all_ages_screenshots node and add to screenshots list
        Optional.ofNullable(screenshotsNode.get("all_ages_screenshots"))
                .map(this::extractScreenshots)
                .map(screenshots::addAll);

        // Ensure mature content screenshots is enabled
        if (CONFIG.allowSteamMatureContentScreenshots()) {
            // Extract screenshots from mature_content_screenshots node and add to screenshots list
            Optional.ofNullable(screenshotsNode.get("mature_content_screenshots"))
                    .map(this::extractScreenshots)
                    .map(screenshots::addAll);
        }

        return screenshots;
    }

    /**
     * Gets screenshot urls from a screenshot json array stored in a JsonNode
     *
     * @param screenshotListNode The JsonNode storing the screenshot json array
     * @return A list of screenshot urls
     */
    private List<String> extractScreenshots(JsonNode screenshotListNode) {
        List<String> screenshots = new ArrayList<>();
        for (JsonNode screenshotNode : screenshotListNode) {
            // Pull out filename
            String filename = Optional.ofNullable(screenshotNode.get("filename"))
                    .map(JsonNode::asText)
                    .orElse("");

            // Make sure filename has content
            if (filename.isBlank()) continue;

            // Append CDN Url to file name and add it to screenshots list
            screenshots.add(STEAM_CDN_URL + filename);
        }

        return screenshots;
    }

    /**
     * Converts each gameList element into json and separates each entry with a comma
     *
     * @param gameListIterator The game list iterator
     * @return A string comprised of the id delimiter json.
     */
    private String joinIds(Iterator<JsonNode> gameListIterator) {
        StringBuilder idStringBuilder = new StringBuilder();

        // Loop through game list nodes
        while (gameListIterator.hasNext()) {
            // Get id json from node
            String idJson = convertGameNodeToJson(gameListIterator.next());
            if (idJson == null) continue;

            // Add to string builder
            idStringBuilder.append(idJson);
            // Add delimiter if there is another element
            if (gameListIterator.hasNext()) idStringBuilder.append(",");
        }

        return idStringBuilder.toString();
    }

    /**
     * Takes in a game node and converts it to the json format needed to for the /IStoreBrowseService/GetItems/v1 endpoint
     * I.e. {"appId": 12345}
     *
     * @param gameNode THe json information of the listing (name and logo url)
     * @return A string containing the information or null;
     */
    private String convertGameNodeToJson(JsonNode gameNode) {
        String logoUrl = gameNode.get("logo").asText();
        if (logoUrl == null) return null;

        if (logoUrl.contains("apps")) {
            // Extract app id from logo url
            String appId = extractId(logoUrl, "apps");
            return ("{\"appId\":" + appId + "}");
        }

        if (logoUrl.contains("subs")) {
            // Extract package id from logo url
            String packageId = extractId(logoUrl, "subs");
            return ("{\"packageId\":" + packageId + "}");
        }

        if (logoUrl.contains("bundles")) {
            // Extract bundle id from logo url
            String bundleId = extractId(logoUrl, "bundles");
            return ("{\"bundleId\":" + bundleId + "}");
        }

        // Unknown type, so return null
        return null;
    }

    /**
     * Pulls listing id from logo url based on type
     *
     * @param logoUrl Logo url
     * @param type    Type of listing (apps, subs, bundles)
     * @return The id if found
     */
    private String extractId(String logoUrl, String type) {
        int typeIndex = logoUrl.indexOf(type + "/");
        int lastForwardSlashIndex = logoUrl.lastIndexOf("/");
        return logoUrl.substring(typeIndex + type.length() + 1, lastForwardSlashIndex);
    }

}
