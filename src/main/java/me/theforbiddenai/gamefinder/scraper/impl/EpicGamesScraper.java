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
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Class responsible for retrieving games with a 100% discount from EpicGames
 *
 * @author TheForbiddenAi
 */
public class EpicGamesScraper extends GameScraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private static final String EPIC_STORE_URL = "https://store.epicgames.com/";
    private static final int MAX_ENTRIES = 100;

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
    public Collection<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            Set<ScraperResult> scraperResultSet = new HashSet<>();

            int startIndex = 0;
            int pagingCount;
            int pagingTotal;

            // GraphQL API

            do {
                JsonNode searchStoreNode = retrieveGamesFromGraphQL(startIndex);

                JsonNode elementsListNode = searchStoreNode.get("elements");
                JsonNode pagingNode = searchStoreNode.get("paging");

                processElementsNode(elementsListNode, scraperResultSet);

                pagingTotal = pagingNode.get("total").asInt();
                // This should always be equal to MAX_ENTRIES
                pagingCount = pagingNode.get("count").asInt();
                startIndex += pagingCount + 1;
            } while (startIndex < pagingTotal);

            // freeGamesPromotions endpoint

            Optional<JsonNode> freeGamePromotionsOptional = getFreeGamePromotions();
            if(freeGamePromotionsOptional.isPresent()) {
                JsonNode elementsListNode = freeGamePromotionsOptional.get();
                processElementsNode(elementsListNode, scraperResultSet);
            }

            return scraperResultSet;
        } catch (IOException | NullPointerException ex) {
            throw new GameRetrievalException("Unable to retrieve games from EpicGames", ex);
        }
    }

    /**
     * Converts each child in elementsListNode to a game object and adds it to the scraperResultSet, assuming it is
     * not deemed invalid. A game object is deemed invalid if it is null or if it is a DLC and DLCs are disabled in the config
     *
     * @param elementsListNode The JsonNode containing the game information
     * @param scraperResultSet The set of ScraperResult objects to add the processed games to
     */
    private void processElementsNode(JsonNode elementsListNode, Set<ScraperResult> scraperResultSet) {
        // Get set of all game titles that have already been processed
        // This only needs to be calculated once when this function is called
        Set<String> existingTitles = scraperResultSet.stream()
                .map(scraperResult -> scraperResult.getGame().getTitle())
                .collect(Collectors.toSet());

        for (JsonNode gameNode : elementsListNode) {
            Optional<String> gameTitle = Optional.ofNullable(gameNode.get("title"))
                    .map(JsonNode::asText);

            // Skip node if it has no title or has already been processed
            if(gameTitle.isEmpty() || existingTitles.contains(gameTitle.get())) continue;

            // Convert each element to a game object using jsonToGame,
            Game game = jsonToGame(gameNode);

            // A game object is deemed invalid if it is null or if it is a DLC and DLCs are disabled in the config
            boolean isInvalidGame = game == null || (!CONFIG.includeDLCs() && game.isDLC());
            // Only add to scraperResultSet if the game is valid
            if (!isInvalidGame) {
                // Wrap game object in a ScraperResult class and add it to the scraperResultSet list
                scraperResultSet.add(new ScraperResult(game));
            }
        }
    }

    /**
     * Converts a JsonNode object to a game object
     *
     * @param gameNode The JsonNode object containing data about a game listing
     * @return A game object, or null if:
     *         the found listing is not a game and includeDLCs is disabled in {@link GameFinderConfiguration}
     *         or there is no discount applied
     */
    private Game jsonToGame(JsonNode gameNode) {
        String offerType = gameNode.get("offerType").asText();
        boolean isDLC = offerType.equalsIgnoreCase("DLC") || offerType.equalsIgnoreCase("ADD_ON");

        JsonNode priceJson = gameNode.get("price");
        JsonNode totalPrice = priceJson.get("totalPrice");

        int discount = totalPrice.get("discountPrice").asInt();

        // Filter out all listings that do not have a 100% discount
        if (discount != 0) return null;

        int priceNoDecimal = totalPrice.get("originalPrice").asInt();
        int decimalCount = totalPrice.get("currencyInfo").get("decimals").asInt();

        getAllOfferNodes(gameNode);

        Game.GameBuilder gameBuilder = Game.builder()
                .title(gameNode.get("title").asText())
                .description(gameNode.get("description").asText())
                .url(getGameUrl(gameNode, isDLC))
                .originalPrice(priceNoDecimal, decimalCount)
                .isDLC(isDLC)
                .platform(Platform.EPIC_GAMES)
                .expirationEpoch(getOfferExpirationEpoch(gameNode));

        // Add image data
        setGameMedia(gameNode, gameBuilder);

        return gameBuilder.build();
    }

    /**
     * Retrieves store media and game media and adds it to the game builder
     *
     * @param gameNode    The JsonNode object containing data about a game listing
     * @param gameBuilder The GameBuilder being updated
     */
    private void setGameMedia(JsonNode gameNode, Game.GameBuilder gameBuilder) {
        JsonNode keyImageList = gameNode.get("keyImages");
        if (keyImageList == null) return;

        Map<String, String> storeImages = new HashMap<>();
        List<String> media = new ArrayList<>();

        // Loop through keyImage elements and sort them into storeImages and media objects
        for (JsonNode imageNode : keyImageList) {
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
     * @param gameNode The json data for the game listing
     * @param isDLC    Whether the listing is a DLC or nto
     * @return The URL for the game listing, or the epic games store URL if it cannot be found
     */
    private String getGameUrl(JsonNode gameNode, boolean isDLC) {
        // First try to find offer page if it exists
        // Ensure the necessary field exists before accessing it
        if ((isDLC && gameNode.has("urlSlug")) || (!isDLC && gameNode.has("productSlug"))) {
            String slug = isDLC ? gameNode.get("urlSlug").asText("") : gameNode.get("productSlug").asText("");
            if (!slug.isBlank()) return getEpicGamesListingURL(slug);
        }

        // If can't find offer page, attempt to find product home page in catalogNs
        JsonNode catalogNs = gameNode.get("catalogNs");

        if (catalogNs != null && catalogNs.has("mappings")) {
            // mappings will only ever have productHome pageTypes due to the way information is requested from GraphQL API
            Iterator<JsonNode> mappingIterator = catalogNs.get("mappings").iterator();
            if (mappingIterator.hasNext()) {
                String slug = mappingIterator.next().get("pageSlug").asText();
                return getEpicGamesListingURL(slug);
            }
        }

        // Can't find URL
        return EPIC_STORE_URL;
    }

    /**
     * Gets the locale specific url for a game on EpicGames
     *
     * @param slug The game's URL slug
     * @return The url
     */
    private String getEpicGamesListingURL(String slug) {
        Locale locale = CONFIG.getLocale();
        // EpicGames supports locales in the form en_US. However, this causes an additional redirect upon page load, which isn't ideal
        String urlFormat = "%s%s-%s/p/%s";
        return String.format(urlFormat, EPIC_STORE_URL, locale.getLanguage(), locale.getCountry(), slug);
    }

    /**
     * Gets the expiration epoch for a game listing from its json data
     *
     * @param gameNode The json data for the game listing
     * @return The epoch second when the offer expires or {@link GameFinderConstants#NO_EXPIRATION_EPOCH} if it can't be found
     */
    private long getOfferExpirationEpoch(JsonNode gameNode) {
        List<JsonNode> offerNodeList = getAllOfferNodes(gameNode);

        for (JsonNode offerNode : offerNodeList) {
            // Get the discountPercentage
            Optional<Integer> discountPercentage = Optional.ofNullable(offerNode.get("discountSetting"))
                    .map(node -> node.get("discountPercentage"))
                    .map(JsonNode::asInt);

            // 0 == 100% discount
            // If the discountPercentage isn't there or is not 0 then it is not the right discount, continue
            if (discountPercentage.isEmpty() || discountPercentage.get() != 0) continue;
            String endDate = Optional.ofNullable(offerNode.get("endDate"))
                    .map(JsonNode::asText)
                    .orElse("");

            long endEpoch = endDate.isBlank() ? GameFinderConstants.NO_EXPIRATION_EPOCH : Instant.parse(endDate).getEpochSecond();
            if (endEpoch > Instant.now().getEpochSecond()) return endEpoch;
        }

        // Expiration epoch not found; return GameFinderConstants.NO_EXPIRATION_EPOCH
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * Gets all promotions and line offer objects for a given game
     *
     * @param gameNode The json data for the game listing
     * @return A list of JsonNode objects containing the information for each promotion and line offer
     */
    private List<JsonNode> getAllOfferNodes(JsonNode gameNode) {
        List<JsonNode> offerNodeList = new ArrayList<>();

        /*
        JSON Structure:
        lineOffers is an array of JSON Objects
        Each of these objects have an appliedRules object
        Each of these objects hold the actual lineOffer information (i.e. start/end time, discount information, etc.)
         */
        Optional.ofNullable(gameNode.get("price"))
                .map(priceNode -> priceNode.get("lineOffers"))
                .ifPresent(lineOffersArray -> addOffersToList(lineOffersArray, "appliedRules", offerNodeList));

        /*
        JSON Structure:
        promotionalOffers is an array of JSON Objects
        Each of these objects have an promotionalOffers object
        Each of these objects hold the actual promotionalOffer information (i.e. start/end time, discount information, etc.)
         */
        Optional.ofNullable(gameNode.get("promotions"))
                .map(promotionsNode -> promotionsNode.get("promotionalOffers"))
                .ifPresent(promotionalOffersArray -> addOffersToList(promotionalOffersArray, "promotionalOffers", offerNodeList));

        return offerNodeList;
    }

    /**
     * Gets the individual offers from an offerArray JSON Node (lineOffers and promotionalOffers)
     *
     * @param offerArrayNode The JsonNode containing the offerArray (either the lineOffers or promotionalOffers object)
     * @param offerFieldName The name of the Json field that holds the array actual offer information (i.e. appliedRules or promotionalOffers)
     * @param offerNodeList  The list that the offer JsonNode objects are being added to
     */
    private void addOffersToList(JsonNode offerArrayNode, String offerFieldName, List<JsonNode> offerNodeList) {
        StreamSupport.stream(offerArrayNode.spliterator(), false)
                // Get offerFieldName array from each offerArrayNode element
                .map(offerNode -> offerNode.get(offerFieldName))
                // Filter out null entries
                .filter(Objects::nonNull)
                // Get individual offerFieldName object from each offerFieldArray
                .flatMap(offerFieldArray -> StreamSupport.stream(offerFieldArray.spliterator(), false))
                .forEach(offerNodeList::add);
    }

    /**
     * Retrieves the json data for free games on EpicGames
     *
     * @return A JsonNode containing the json data
     * @throws IOException If objectMapper fails to read the data
     */
    private JsonNode retrieveGamesFromGraphQL(int startIndex) throws IOException {

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
        variables.put("start", startIndex);
        variables.put("freeGame", true);
        variables.put("pageType", "productHome");
        variables.put("withPromotions", true);
        variables.put("withPrice", true);

        return graphQLClient.executeQuery(GraphQLClient.STORE_QUERY, variables).get("data")
                .get("Catalog")
                .get("searchStore");
    }

    /**
     * Gets the elements JsonNode containing the game listing information for the Epic's weekly/biweekly free game giveaway
     * NOTE: Nearly every game retrieved from here is covered under the GraphQL call
     * The only time a game is returned here and not from GraphQL is when a new listing is created expressly for this promotion
     * and is not listing as onSale
     *
     * @return An optional containing the elements JsonNode
     * @throws IOException IOException If the URL is malformed or if the mapper is unable to parse the json data
     */
    public Optional<JsonNode> getFreeGamePromotions() throws IOException {
        String localeString = CONFIG.getLocale().toString().replace("_", "-");
        String url = "https://store-site-backend-static-ipv4.ak.epicgames.com/freeGamesPromotions?locale=" + localeString;
        return Optional.of(getObjectMapper().readTree(new URL(url)))
                .map(node -> node.get("data"))
                .map(node -> node.get("Catalog"))
                .map(node -> node.get("searchStore"))
                .map(node -> node.get("elements"));
    }

}
