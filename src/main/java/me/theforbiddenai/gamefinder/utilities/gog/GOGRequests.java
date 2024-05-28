package me.theforbiddenai.gamefinder.utilities.gog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;

import java.io.IOException;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;

/**
 * Responsible for making requests to undocumented GOG API endpoints
 *
 * @author TheForbiddenAi
 */
public class GOGRequests {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private final ObjectMapper mapper;

    public GOGRequests(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * Retrieves a list of all games with a 100% discount on GOG
     *
     * @return The JsonNode containing the game information
     * @throws IOException If the URL is malformed or if the mapper is unable to parse the json data
     */
    public Optional<JsonNode> getGameList() throws IOException {
        String productTypes = "game,pack";
        productTypes = CONFIG.includeDLCs() ? productTypes + ",dlc,extras" : productTypes;

        String catalogURL = "https://catalog.gog.com/v1/catalog" +
                getLocaleURLParameters() +
                "&limit=1" +
                // TODO: Uncomment this line when done testing
                // "&price=between:0,0" +
                "&order=desc:trending" +
                "&discounted=eq:true" +
                "&productType=in:" + productTypes +
                "&page=1";
        return Optional.of(mapper.readTree(new URL(catalogURL)))
                .map(node -> node.get("products"));
    }

    /**
     * Gets the json section data for the GOG homepage
     *
     * @return The json data if found
     * @throws IOException If the URL is malformed or if the mapper is unable to parse the json data
     */
    public Optional<JsonNode> getHomePageSections() throws IOException {
        // I do not believe countryCode/currencyCode are required, but this is the request GOG itself makes
        // So, I'd rather keep it the same in case they make it required for some reason
        // NOTE: 2f is hexadecimal for / . You can retrieve page data for promo pages and the catalog page by encoding
        // the slug, excluding the language portion, (i.e. /promo/cool_promo) in hexadecimal. It does NOT work for individual game pages
        String url = "https://sections.gog.com/v1/pages/2f" +
                getLocaleURLParameters();
        return Optional.of(mapper.readTree(new URL(url)))
                .map(node -> node.get("sections"));
    }

    /**
     * Gets the json data about a GOG homepage section
     *
     * @param sectionId The id of the section
     * @return The json data if found
     * @throws IOException If the URL is malformed or if the mapper is unable to parse the json data
     */
    public Optional<JsonNode> getHomePageSection(String sectionId) throws IOException {
        String url = "https://sections.gog.com/v1/pages/2f/sections/" + sectionId +
                getLocaleURLParameters();
        return Optional.of(mapper.readTree(new URL(url)))
                .map(node -> node.get("properties"));
    }

    /**
     * Gets the json data for a game listing (does NOT contain discount end time)
     *
     * @param gameId The id of the game
     * @return THe json data if found
     * @throws IOException If the URL is malformed or if the mapper is unable to parse the json data
     */
    public Optional<JsonNode> getGame(String gameId) throws IOException {
        String url = "https://api.gog.com/v2/games/" + gameId +
                getLocaleURLParameters();
        return Optional.of(mapper.readTree(new URL(url)));
    }

    /**
     * Converts the locale into URL parameters
     *
     * @return The locale URL parameters
     */
    private String getLocaleURLParameters() {
        Locale locale = CONFIG.getLocale();
        Currency currency = Currency.getInstance(locale);

        return "?countryCode=" + locale.getCountry() +
                "&locale=" + locale +
                "&currencyCode=" + currency.getCurrencyCode();
    }

}
