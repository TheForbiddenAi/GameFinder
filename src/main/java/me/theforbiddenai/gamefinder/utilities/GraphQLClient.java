package me.theforbiddenai.gamefinder.utilities;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;

public class GraphQLClient {

    public static final String STORE_QUERY = "query searchStoreQuery($allowCountries: String, $category: String, $count: Int, $country: String! $locale: String, $itemNs: String, $sortBy: String, $sortDir: String, $start: Int $onSale: Boolean, $withPrice: Boolean = false, $withPromotions: Boolean = false) { Catalog { searchStore(allowCountries: $allowCountries, category: $category, count: $count, country: $country, locale: $locale, itemNs: $itemNs, sortBy: $sortBy, sortDir: $sortDir, start: $start, onSale: $onSale) { elements { title description offerType keyImages { type url } productSlug urlSlug catalogNs { mappings { pageSlug pageType } } price(country: $country) @include(if: $withPrice) { totalPrice { discountPrice originalPrice currencyInfo { decimals } } lineOffers { appliedRules { startDate endDate discountSetting { discountType discountPercentage } } } } promotions(category: $category) @include(if: $withPromotions) { promotionalOffers { promotionalOffers { startDate endDate discountSetting { discountType discountPercentage } } } } } } } }";


    private static final MediaType JSON = MediaType.get("application/json");
    private static final String EPIC_GAMES_GRAPHQL_URL = "https://graphql.epicgames.com/graphql";

    private final OkHttpClient httpClient;

    private final ObjectMapper mapper;
    private final String locale;
    private final String country;

    public GraphQLClient(ObjectMapper mapper, String locale, String country) {
        this.httpClient = new OkHttpClient();

        this.mapper = mapper;
        this.locale = locale;
        this.country = country;
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

        // Add locale and country to queryVariables
        queryVariables.put("locale", locale);
        queryVariables.put("country", country);

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
