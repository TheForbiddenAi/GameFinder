package me.theforbiddenai.gamefinder.webscraper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.exception.WebScrapeException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Responsible for game information from a GOG game page
 *
 * @author TheForbiddenAi
 */
public class GOGWebScraper extends WebScraper<JsonNode> {

    private static final String JSON_FIELD_FORMAT = "\"%s\":%s";
    private static final int PRODUCT_CARD_FIELD_COUNT = 3;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    // This set contains the names of the json fields that should be added to the storeMedia map in a game object
    private static final List<String> STORE_MEDIA_FIELDS = List.of(
            "backgroundImage",
            "boxArtImage",
            "galaxyBackgroundImage",
            "logo"
    );

    private static final Pattern THREE_OR_MORE_NEWLINES_REGEX = Pattern.compile("(\\n(\\s+)?){3,}");

    private final ObjectMapper mapper;

    public GOGWebScraper(ObjectMapper mapper) {
        super("gog_wantsmaturecontent=9999");
        this.mapper = mapper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGameAttributes(JsonNode jsonNode, Game game) throws WebScrapeException {
        JsonNode cardProductNode = jsonNode.get("cardProduct");
        JsonNode promoNode = jsonNode.get("cardProductPromoEndDate");
        String currencyCode = jsonNode.get("currency").asText();

        // Get baseAmount from cardProduct.price json if it exists
        Optional<Double> baseAmountOptional = Optional.ofNullable(cardProductNode.get("price"))
                .map(node -> node.get("baseAmount"))
                .map(JsonNode::asDouble);

        // If baseAmount exists, parse it.
        baseAmountOptional.ifPresent(baseAmount -> {
            if (baseAmount == 0) {
                game.setOriginalPrice("N/A (Unsupported Locale)");
            } else {
                game.setOriginalPrice(baseAmount, currencyCode);
            }
        });

        game.setDescription(getDescription(cardProductNode));
        game.setExpirationEpoch(getExpirationEpoch(promoNode));

        STORE_MEDIA_FIELDS.forEach(field -> insertStoreMediaEntry(game.getStoreMedia(), cardProductNode, field));
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
            String line = scanner.nextLine().stripLeading();

            String jsonField = getJsonField(line, foundFields);
            if (jsonField == null) continue;

            jsonBuilder.append(jsonField).append(",");

            // All required fields have been found
            if (foundFields.size() == PRODUCT_CARD_FIELD_COUNT) break;
        }

        if (foundFields.size() != PRODUCT_CARD_FIELD_COUNT) {
            throw new WebScrapeException("Unable to retrieve all required product card data for GOG game with url " + url);
        }

        scanner.close();

        // Remove hanging comma and add closing curly brace
        jsonBuilder.deleteCharAt(jsonBuilder.length() - 1);

        jsonBuilder.append("}");

        try {
            return mapper.readTree(jsonBuilder.toString());
        } catch (JsonProcessingException ex) {
            throw new WebScrapeException("Unable to parse json data for GOG game with url " + url, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLocaleCookie() {
        if(!CONFIG.useGOGLocaleCookie()) return null;

        Locale locale = CONFIG.getLocale();
        Currency currency = Currency.getInstance(locale);
        return "gog_lc=" + locale.getCountry() + "_" + currency.getCurrencyCode() + "_en-US";
    }

    /**
     * Adds a given field, and it's value, to the storeMedia map if it exists
     *
     * @param storeMedia      The storeMedia map
     * @param cardProductNode The JsonNode containing the jsonField
     * @param jsonField       The name of the json field
     */
    private void insertStoreMediaEntry(Map<String, String> storeMedia, JsonNode cardProductNode, String jsonField) {
        // Make sure the field exists
        if (!cardProductNode.has(jsonField)) return;

        // Get the url (default to blank string if null)
        String url = cardProductNode.get(jsonField).asText("");

        // Add to map if the url is not null
        if (url.isBlank()) return;
        storeMedia.put(jsonField, url);
    }

    /**
     * Gets the expiration epoch for a discount
     *
     * @param promoNode The JsonNode that contains the promotion end date information
     * @return The expiration epoch if found otherwise GameFinderConstants.NO_EXPIRATION_EPOCH
     */
    private long getExpirationEpoch(JsonNode promoNode) {
        // Get the date string (in form: yyyy-MM-dd HH:mm:ss.SSSSSS)
        Optional<String> dateStr = Optional.of(promoNode.get("date"))
                .map(JsonNode::asText);

        // Get the timezone (either a UTC offset, timezone abbreviation, or timezone identifier)
        Optional<String> timezone = Optional.of(promoNode.get("timezone"))
                .map(JsonNode::asText);

        if (dateStr.isEmpty() || timezone.isEmpty()) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        try {
            // Parse the dateStr, set the timezone, and then pull out the epoch second
            return LocalDateTime.parse(dateStr.get(), DATE_TIME_FORMATTER)
                    .atZone(ZoneId.of(timezone.get()))
                    .toInstant()
                    .getEpochSecond();
        } catch (Exception ex) {
            return GameFinderConstants.NO_EXPIRATION_EPOCH;
        }
    }

    /**
     * Gets the description of a game
     *
     * @param cardProductNode The JsonNode that contains the game information
     * @return The game's description
     */
    private String getDescription(JsonNode cardProductNode) {
        String descriptionHTML = Optional.ofNullable(cardProductNode.get("description"))
                .map(JsonNode::asText)
                // This is done to maintain newlines that would otherwise be stripped when parsed by Jsoup
                .map(string -> string.replace("\n", "<br>"))
                .orElse("N/A");

        Document descDocument = Jsoup.parse(descriptionHTML);

        // Any paragraph element with the module class is a disclaimer from GOG and is not part of the description
        descDocument.select("p.module").remove();

        // This causes a newline to be inserted after each header
        descDocument.select("h1, h2, h3, h4, h5, h6, h7").forEach(element -> element.after("<br>"));

        // This strips all HTML tags from the description, keeps the original formatting, and strips leading/trailing whitespace
        Document.OutputSettings outputSettings = new Document.OutputSettings().prettyPrint(false);
        String descString = Jsoup.clean(descDocument.html(), "", Safelist.none(), outputSettings).strip();

        // This regex pattern removes restricts the number of sequential newlines to two
        return THREE_OR_MORE_NEWLINES_REGEX.matcher(descString).replaceAll("\n\n");
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
