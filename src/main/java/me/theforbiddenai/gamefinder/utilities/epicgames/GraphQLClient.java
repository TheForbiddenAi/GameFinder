package me.theforbiddenai.gamefinder.utilities.epicgames;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import okhttp3.*;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class GraphQLClient {

    // See https://github.com/SD4RK/epicstore_api and https://github.com/Tectors/EpicGraphQL/tree/main for more info
    public static final String STORE_QUERY = "query searchStoreQuery($allowCountries: String, $category: String, $count: Int, $country: String! $locale: String, $itemNs: String, $sortBy: String, $sortDir: String, $start: Int $onSale: Boolean, $freeGame: Boolean, $pageType: String, $withPrice: Boolean = false, $withPromotions: Boolean = false) { Catalog { searchStore(allowCountries: $allowCountries, category: $category, count: $count, country: $country, locale: $locale, itemNs: $itemNs, sortBy: $sortBy, sortDir: $sortDir, start: $start, onSale: $onSale, freeGame: $freeGame) { elements { title description offerType keyImages { type url } productSlug urlSlug catalogNs { mappings(pageType: $pageType) { pageSlug pageType } } price(country: $country) @include(if: $withPrice) { totalPrice { discountPrice originalPrice currencyCode currencyInfo { decimals } } lineOffers { appliedRules { startDate endDate discountSetting { discountType discountPercentage } } } } promotions(category: $category) @include(if: $withPromotions) { promotionalOffers { promotionalOffers { startDate endDate discountSetting { discountType discountPercentage } } } } } } } }";

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private static final MediaType JSON = MediaType.get("application/json");
    private static final String EPIC_GAMES_GRAPHQL_URL = "https://graphql.epicgames.com/graphql";

    private final OkHttpClient httpClient;

    private final ObjectMapper mapper;

    public GraphQLClient(ObjectMapper mapper) {
        this.mapper = mapper;
        this.httpClient = new OkHttpClient();
    }

    public GraphQLClient(ObjectMapper mapper, OkHttpClient httpClient) {
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    /**
     * Executes a query on the EpicGames GraphQL API
     *
     * @param queryString    The data being requested from the GraphQL API
     * @param queryVariables The values of the variables defined in the queryString
     * @return All the information found in the form of a JsonNode
     * @throws IOException If the query fails for any reason
     */
    public JsonNode executeQuery(String queryString, Map<String, Object> queryVariables) throws IOException {

        Locale locale = CONFIG.getLocale();
        String localeString = locale.toString().replace("_", "-");

        // Add locale and country to queryVariables
        queryVariables.put("locale", localeString);
        queryVariables.put("country", locale.getCountry());

        ObjectNode requestBodyNode = mapper.createObjectNode();

        // Add query and variables field to requestBody
        requestBodyNode.put("query", queryString);
        requestBodyNode.put("variables", mapper.writeValueAsString(queryVariables));

        return executeHttpRequest(requestBodyNode);
    }

    /**
     * Executes a POST request on the GraphQL API endpoint with a given requestBody
     *
     * @param requestBodyNode The information being requested
     * @return The information return from the GraphQL API
     * @throws IOException If the HTTP request fails
     */
    private JsonNode executeHttpRequest(ObjectNode requestBodyNode) throws IOException {
        // Convert the requestBodyNode to a string and add it to a RequestBody
        RequestBody body = RequestBody.create(mapper.writeValueAsString(requestBodyNode), JSON);

        // Create the request
        Request request = new Request.Builder()
                .url(EPIC_GAMES_GRAPHQL_URL)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        // Execute the request
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (!response.isSuccessful() || responseBody == null) return mapper.createObjectNode();

            // Convert the responseBody to a JsonNode
            return mapper.readTree(responseBody.string());
        }

    }

}
