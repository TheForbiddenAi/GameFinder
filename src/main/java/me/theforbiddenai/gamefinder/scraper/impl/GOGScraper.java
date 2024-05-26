package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.Scraper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GOGScraper extends Scraper {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private static final Pattern CARD_PRODUCT_PATTERN = Pattern.compile("cardProduct: (\\{.*})");
    private static final Pattern CARD_PROMO_END_PATTERN = Pattern.compile("window\\.productcardData\\.cardProductPromoEndDate = (\\{.*})");

    private final OkHttpClient httpClient;

    public GOGScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.GOG);

        this.httpClient = new OkHttpClient();
    }

    @Override
    public List<ScraperResult> retrieveResults() throws GameRetrievalException {
        try {
            // System.out.println(retrieveGameList());
            JsonNode gameListNode = retrieveGameList();
            for (JsonNode gameNode : gameListNode) {
                retrieveGameFromGOG(gameNode);//.join();
            }
        } catch (IOException ex) {
            throw new GameRetrievalException("Unable to retrieve games from GOG", ex);
        }
        return List.of();
    }

    private CompletableFuture<Game> retrieveGameFromGOG(JsonNode gameListNode) throws IOException, GameRetrievalException {
        return CompletableFuture.supplyAsync(() -> {
            String productType = gameListNode.get("productType").asText();
            boolean isDLC = productType.equalsIgnoreCase("dlc") || productType.equalsIgnoreCase("extra");

            // Pull formatted original price string out
            String originalPrice = Optional.ofNullable(gameListNode.get("price"))
                    .map(node -> node.get("base"))
                    .map(node -> node.asText(""))
                    .orElse("");

            Map<String, String> storeMedia = new HashMap<>();
            storeMedia.put("coverHorizontal", gameListNode.get("coverHorizontal").asText());
            storeMedia.put("coverVertical", gameListNode.get("coverVertical").asText());

            String url = gameListNode.get("storeLink").asText();
            try {
                JsonNode productCardData = getProductCardData(gameListNode.get("storeLink").asText());

                JsonNode cardProductNode = productCardData.get("cardProduct");

                Game.GameBuilder gameBuilder = Game.builder()
                        .title(gameListNode.get("title").asText())
                        .description(getDescription(cardProductNode))
                        .url(url)
                        .isDLC(isDLC)
                        .originalPrice(originalPrice)
                        .storeMedia(storeMedia)
                        .media(getScreenshots(gameListNode))
                        .platform(Platform.GOG);

                return gameBuilder.build();
            } catch (IOException | GameRetrievalException e) {
                throw new RuntimeException(e);
            }

        }, CONFIG.getExecutorService());
    }

    private String getDescription(JsonNode cardProductNode) {
        String descriptionHTML = cardProductNode.get("description").asText();
        Document descDocument = Jsoup.parse(descriptionHTML);

        // Any paragraph element with the module class is a disclaimer from GOG and is not part of the description
        descDocument.select("p.module").remove();

        // This strips all HTML tags from the description, keeps the original formatting, and strips leading/trailing whitespace
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String descString = Jsoup.clean(descDocument.html(), "", Safelist.none(), outputSettings).strip();

        // This regex pattern removes restricts the number of sequential newlines to two
        return descString.replaceAll("(\\n(\\s+)?){3,}", "\n\n");
    }

    /**
     * Gets the productCard data for a GOG game
     *
     * @param url The URL to the GOG game page
     * @return A JsonNode containing all the productCard data
     * @throws IOException            If the URL is malformed or if the object mapper is unable to parse the retrieved json
     * @throws GameRetrievalException If the necessary data is not found
     */
    private JsonNode getProductCardData(@NonNull String url) throws IOException, GameRetrievalException {
        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/javascript")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful())
                throw new GameRetrievalException("Unable to connect to GOG game with URL " + url);

            ResponseBody body = response.body();
            if (body == null)
                throw new GameRetrievalException("Unable to retrieve response body for GOG game with URL " + url);

            String bodyString = body.string();

            Matcher cardProductMatcher = CARD_PRODUCT_PATTERN.matcher(bodyString);
            Matcher cardPromoEndMatcher = CARD_PROMO_END_PATTERN.matcher(bodyString);
            // Make sure that all data exists in the string
            if (!cardProductMatcher.find())
                throw new GameRetrievalException("Unable to retrieve cardProductData for GOG Game with URL " + url);
            if (!cardPromoEndMatcher.find())
                throw new GameRetrievalException("Unable to retrieve cardProductPromoEndDate for GOG Game with URL " + url);

            // Put data in json format
            String stringJson = "{" + "\"cardProduct\":" +
                    cardProductMatcher.group(1) +
                    ",\"cardProductPromoEndDate\":" +
                    cardPromoEndMatcher.group(1) + "}";

            return getObjectMapper().readTree(stringJson);
        }
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

    /**
     * Retrieves a list of all games with a 100% discount on GOG
     *
     * @return The JsonNode containing the game information
     * @throws IOException If the object mapper is unable to parse the retrieved data
     */
    private JsonNode retrieveGameList() throws IOException {
        String productTypes = "game,pack";
        productTypes = CONFIG.includeDLCs() ? productTypes + ",dlc,extras" : productTypes;

        Locale locale = CONFIG.getLocale();
        Currency currency = Currency.getInstance(locale);

        String catalogURL = "https://catalog.gog.com/v1/catalog?limit=1" +
                // "&price=between:0,0" +
                "&order=desc:trending" +
                "&discounted=eq:true" +
                "&productType=in:" + productTypes +
                "&page=1" +
                "&countryCode=" + locale.getCountry() +
                "&locale=" + locale +
                "&currencyCode=" + currency.getCurrencyCode();
        return Optional.of(getObjectMapper().readTree(new URL(catalogURL)))
                .map(node -> node.get("products"))
                .orElse(null);
    }

}
