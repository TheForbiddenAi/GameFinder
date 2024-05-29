package me.theforbiddenai.gamefinder.webscraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.exception.WebScrapeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

public class GOGWebScrape extends WebScraper<JsonNode> {

    private static final String JSON_FIELD_FORMAT = "\"%s\":%s";
    private static final int PRODUCT_CARD_FIELD_COUNT = 3;

    public GOGWebScrape(ObjectMapper mapper) {
        super(mapper, "gog_wantsmaturecontent=9999");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGameAttributes(JsonNode jsonNode, Game game) throws GameRetrievalException, IOException {
        System.out.println(jsonNode);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JsonNode processHTML(InputStream inputStream, String url) throws WebScrapeException {
        StringBuilder jsonBuilder = new StringBuilder("{");
        Set<String> foundFields = new HashSet<>();

        Scanner scanner = new Scanner(inputStream);

        // Loop through the HTML lines
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();

            String jsonField = getJsonField(line, foundFields);
            if (jsonField == null) continue;

            jsonBuilder.append(jsonField).append(",");

            // All required fields have been found
            if (foundFields.size() == PRODUCT_CARD_FIELD_COUNT) break;
        }

        if (foundFields.size() != PRODUCT_CARD_FIELD_COUNT) {
            throw new WebScrapeException("Unable to retrieve all required product card data for GOG game with url " + url);
        }

        // Remove hanging comma and add closing curly brace
        jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);
        jsonBuilder.append("}");

        scanner.close();

        try {
            return getMapper().readTree(jsonBuilder.toString());
        } catch (JsonProcessingException ex) {
            throw new WebScrapeException("Unable to parse json data for GOG game with url " + url, ex);
        }
    }


    /**
     * Extracts a required product card json fields (cardProduct, currency, and cardProductPromoEndDate) from a line
     * if it is there
     *
     * @param line        The line the json field is being extracted from
     * @param foundFields A set containing the name of all found product card fields
     * @return A json string containing the field, or null if it cannot be found in the given line or if it has already been found
     */
    private String getJsonField(String line, Set<String> foundFields) {
        String name = "";
        String value = "";

        if (line.startsWith("cardProduct:")) {
            name = "cardProduct";
            value = getJsonValue(line, "cardProduct:", "}");
        }

        if (line.startsWith("currency:")) {
            name = "currency";
            value = getJsonValue(line, "currency:", "\"");
        }

        if (line.startsWith("window.productcardData.cardProductPromoEndDate =")) {
            name = "cardProductPromoEndDate";
            value = getJsonValue(line, "window.productcardData.cardProductPromoEndDate =", "}");
        }

        // Make sure that there is a valid name and value (name can only ever be empty not blank)
        if (name.isEmpty() || value.isBlank()) return null;

        // Return null if this field has already been retrieved
        if (foundFields.contains(name)) return null;

        foundFields.add(name);
        return String.format(JSON_FIELD_FORMAT, name, value);
    }

    /**
     * Extracts a json field value from a given line
     *
     * @param line         The line the value is being extracted from
     * @param removePrefix The prefix being removed from the line
     * @param endWith      The character that the string must end with
     * @return The found value or an empty string if not found
     */
    private String getJsonValue(String line, String removePrefix, String endWith) {
        try {
            // Removes the prefix
            String value = line.substring(removePrefix.length());
            if (value.endsWith(endWith)) return value;

            // Makes sure the value ends at the last instance of endWith
            return value.substring(0, value.lastIndexOf(endWith) + 1);
        } catch (IndexOutOfBoundsException ex) {
            return "";
        }
    }

}
