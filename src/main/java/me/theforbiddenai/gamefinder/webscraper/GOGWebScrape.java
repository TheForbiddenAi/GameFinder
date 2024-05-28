package me.theforbiddenai.gamefinder.webscraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GOGWebScrape extends WebScraper {

    // Maps a json field name to it's corresponding group name in the PRODUCT_CARD_REGEX regex
    private static final Map<String, String> PRODUCT_CARD_FIELD_MAP = Map.ofEntries(
            Map.entry("cardProduct", "prodVal"),
            Map.entry("currency", "curVal"),
            Map.entry("cardProductPromoEndDate", "promoVal")
    );

    private static final Pattern PRODUCT_CARD_REGEX = Pattern.compile(
            "(?<prod>cardProduct):\\s(?<prodVal>\\{.*})|" +
                    "(?<cur>currency):\\s(?<curVal>\".*\")|" +
                    "window\\.productcardData\\.(?<promo>cardProductPromoEndDate)\\s=\\s(?<promoVal>\\{.*})"
            , Pattern.MULTILINE);

    public GOGWebScrape(ObjectMapper mapper) {
        super(mapper, "gog_wantsmaturecontent=9999");
    }

    @Override
    protected void modifyGameAttributes(String html, Game game) throws GameRetrievalException, IOException {
        JsonNode productCard = getProductCardData(html, game.getUrl());
    }

    /**
     * Gets the productCard data for a GOG game
     *
     * @param html The HTML for a GOG game page
     * @param url  The URL to the GOG game page
     * @return A JsonNode containing all the productCard data
     * @throws GameRetrievalException  If the necessary data is not found
     * @throws JsonProcessingException If the object mapper is unable to parse the retrieved json
     */
    protected JsonNode getProductCardData(@NonNull String html, String url) throws GameRetrievalException, IOException {

        // This reason I am using a StringBuilder over an ObjectNode is because I only need to call readTree once,
        // whereas with an ObjectNode, readTree would need to be called for almost every fieldValue
        StringBuilder jsonBuilder = new StringBuilder("{");

        // This set keeps track of all retrieved productCard fields
        Set<String> foundFields = new HashSet<>();

        int productCardMapSize = PRODUCT_CARD_FIELD_MAP.size();
        Matcher cardProductMatcher = PRODUCT_CARD_REGEX.matcher(html);

        while (cardProductMatcher.find()) {
            // All required fields have been retrieved, break the loop
            // This is necessary because sometimes these values are repeated
            if (foundFields.size() == productCardMapSize) break;

            // Pull out the fieldName from the first nonnull group: prod, cur, or promo
            String fieldName = Optional.ofNullable(cardProductMatcher.group("prod"))
                    .or(() -> Optional.ofNullable(cardProductMatcher.group("cur")))
                    .or(() -> Optional.ofNullable(cardProductMatcher.group("promo")))
                    .orElse(null);

            // Make sure that the fieldName isn't null and hasn't already been found
            if (fieldName == null || foundFields.contains(fieldName)) continue;

            String valueGroupName = PRODUCT_CARD_FIELD_MAP.get(fieldName);

            // Pull out the fieldValue for the found fieldName
            String fieldValue = cardProductMatcher.group(valueGroupName);

            // Make sure the field value isn't null
            if (fieldValue == null) continue;

            foundFields.add(fieldName);

            // Add the fieldName to jsonBuilder in proper json syntax
            jsonBuilder.append("\"").append(fieldName).append("\":");

            // Add fieldValue to jsonBuilder
            jsonBuilder.append(fieldValue).append(",");
        }

        // Make sure all data has been retrieved
        if (foundFields.size() != productCardMapSize) {
            throw new GameRetrievalException("Unable to retrieve required data for GOG game with URL " + url);
        }

        // Remove hanging comma
        jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);

        // Close Json object
        jsonBuilder.append("}");

        return super.getMapper().readTree(jsonBuilder.toString());
    }
}
