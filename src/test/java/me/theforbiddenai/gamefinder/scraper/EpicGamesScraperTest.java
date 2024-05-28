package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.utilities.epicgames.GraphQLClient;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EpicGamesScraperTest {

    private ObjectMapper mapper;

    private EpicGamesScraper scraper;
    private JsonNode treeNode;

    private GraphQLClient graphQLClient;
    private OkHttpClient mockClient;

    private List<ScraperResult> expectedGamesWithDLCs;
    private List<ScraperResult> expectedGamesWithoutDLCs;

    // Test Setup

    @BeforeAll
    public void setupJson() throws IOException {
        this.mapper = Mockito.spy(new ObjectMapper());
        this.treeNode = mapper.readTree(EpicGamesScraperTest.class.getResourceAsStream("/scraper/epic_games_data/epic-games-scraper-test-data.json"));

        this.graphQLClient = new GraphQLClient(mapper);
        this.mockClient = Mockito.mock(OkHttpClient.class);
    }

    @BeforeEach
    public void setupTests() throws IOException, IllegalAccessException {
        doReturn(treeNode).when(this.mapper).readTree(Mockito.anyString());

        this.scraper = new EpicGamesScraper(this.mapper);

        setupOkHttpMocks();
        injectMocks();

        Game gameOne = Game.builder()
                .title("Game")
                .description("Cool game.")
                .url("https://store.epicgames.com/en-US/p/productSlug")
                .originalPrice("$10.99")
                .platform(Platform.EPIC_GAMES)
                .isDLC(false)
                .storeMedia(Map.of("DieselStoreFrontTall", "url-1", "DieselStoreFrontWide", "url-2"))
                .media(List.of("url-3", "url-4"))
                .expirationEpoch(1715871600L)
                .build();

        Game gameTwo = Game.builder()
                .title("DLC")
                .description("Cool DLC.")
                .url("https://store.epicgames.com/en-US/p/pageSlug")
                .originalPrice("$2.99")
                .isDLC(true)
                .platform(Platform.EPIC_GAMES)
                .storeMedia(Map.of("OfferImageWide", "url-1", "OfferImageTall", "url-2"))
                .media(List.of("url-3"))
                .expirationEpoch(1715871600L)
                .build();

        expectedGamesWithDLCs = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwo));
        expectedGamesWithoutDLCs = List.of(new ScraperResult(gameOne));
    }

    // Utilities

    /**
     * Injects the {@link this#graphQLClient} into {@link this#scraper} and {@link this#mockClient} into {@link this#graphQLClient}
     *
     * @throws IllegalAccessException If the fields are unable to be set
     */
    private void injectMocks() throws IllegalAccessException {
        // Using reflection is not ideal. However, this is done to ensure that these tests do not connect to the internet

        // Pull out private scrapers field from GameFinder class
        Field graphQlClientField = getField(EpicGamesScraper.class, "graphQLClient");
        Field okHttpClientField = getField(GraphQLClient.class, "httpClient");

        // Set the fields to accessible
        graphQlClientField.setAccessible(true);
        okHttpClientField.setAccessible(true);

        // Inject the objects
        graphQlClientField.set(this.scraper, graphQLClient);
        okHttpClientField.set(graphQLClient, mockClient);

        // Set the fields to be inaccessible
        graphQlClientField.setAccessible(false);
        okHttpClientField.setAccessible(false);
    }

    /**
     * Gets a field object from a given clazz and field name
     *
     * @param clazz     The clazz the field exists within
     * @param fieldName The name of the field
     * @return The found field object
     */
    private Field getField(Class<?> clazz, String fieldName) {
        return ReflectionUtils.findFields(clazz, f -> f.getName().equals(fieldName),
                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .get(0);
    }

    /**
     * Setups all the mock calls for {@link this#mockClient}
     *
     * @throws IOException This will never happen
     */
    private void setupOkHttpMocks() throws IOException {
        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        ResponseBody mockResponseBody = Mockito.mock(ResponseBody.class);

        when(this.mockClient.newCall(Mockito.any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponseBody.string()).thenReturn("");
    }

    // Tests

    @Test
    public void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithDLCs, returnedGames);
    }

    @Test
    public void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithoutDLCs, returnedGames);
    }

}
