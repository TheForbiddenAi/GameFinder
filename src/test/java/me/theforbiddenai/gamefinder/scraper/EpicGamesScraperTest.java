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
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EpicGamesScraperTest {

    private EpicGamesScraper epicGamesScraper;

    private List<ScraperResult> expectedGamesWithDLCsList;
    private List<ScraperResult> expectedGamesWithoutDLCsList;

    // Test Setup

    @BeforeAll
    void setupJson() throws IOException {
        ObjectMapper mapper = Mockito.spy(new ObjectMapper());
        JsonNode treeNode = mapper.readTree(EpicGamesScraperTest.class.getResourceAsStream("/scraper/epic_games_data/epic-games-scraper-test-data.json"));
        doReturn(treeNode).when(mapper).readTree(Mockito.anyString());

        OkHttpClient mockHttpClient = Mockito.mock(OkHttpClient.class);
        setupOkHttpMocks(mockHttpClient);

        GraphQLClient graphQLClient = new GraphQLClient(mapper, mockHttpClient);
        this.epicGamesScraper = new EpicGamesScraper(mapper, graphQLClient);
    }

    @BeforeEach
    void setupGameLists() {
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

        expectedGamesWithDLCsList = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwo));
        expectedGamesWithoutDLCsList = List.of(new ScraperResult(gameOne));
    }

    // Utilities

    /**
     * Setups all the mock calls for a mock OkHttpClient object
     *
     * @throws IOException This will never happen
     */
    private void setupOkHttpMocks(OkHttpClient mockHttpClient) throws IOException {
        Call mockCall = Mockito.mock(Call.class);
        Response mockResponse = Mockito.mock(Response.class);
        ResponseBody mockResponseBody = Mockito.mock(ResponseBody.class);

        when(mockHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponseBody.string()).thenReturn("");
    }

    // Tests

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);

        List<ScraperResult> returnedGames = epicGamesScraper.retrieveResults();
        assertIterableEquals(expectedGamesWithDLCsList, returnedGames);
    }

    @Test
    void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);

        List<ScraperResult> returnedGames = epicGamesScraper.retrieveResults();
        assertIterableEquals(expectedGamesWithoutDLCsList, returnedGames);
    }

}
