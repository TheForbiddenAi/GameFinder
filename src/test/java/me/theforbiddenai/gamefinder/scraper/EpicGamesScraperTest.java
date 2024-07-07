package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.TestHelper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.utilities.epicgames.GraphQLClient;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doReturn;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EpicGamesScraperTest {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private EpicGamesScraper epicGamesScraper;

    private List<ScraperResult> expectedGamesWithDLCsList;
    private List<ScraperResult> expectedGamesWithoutDLCsList;

    // Test Setup

    @BeforeAll
    void setupJson() throws IOException {
        // Must be spied due to how the object mapper is used in the GraphQLClient
        ObjectMapper mapper = Mockito.spy(new ObjectMapper());
        JsonNode graphQLNode = mapper.readTree(EpicGamesScraperTest.class.getResourceAsStream("/scraper/epic_games_data/epic-games-scraper-graphql-data.json"));
        JsonNode freeGamesPromotionsNode = mapper.readTree(EpicGamesScraperTest.class.getResourceAsStream("/scraper/epic_games_data/epic-games-scraper-freeGamesPromotions-data.json"));

        doReturn(freeGamesPromotionsNode).when(mapper).readTree(Mockito.any(URL.class));
        doReturn(graphQLNode).when(mapper).readTree(Mockito.anyString());

        OkHttpClient mockHttpClient = TestHelper.setupOkHttpMocks(null);

        GraphQLClient graphQLClient = new GraphQLClient(mapper, mockHttpClient);
        this.epicGamesScraper = new EpicGamesScraper(mapper, graphQLClient);
    }

    @BeforeEach
    void setupGameLists() {
        // These test will fail any time after Tuesday, May 16, 2924 11:00:00 AM Eastern. Oh, well.
        Game gameOne = Game.builder()
                .title("Game")
                .description("Cool game.")
                .url("https://store.epicgames.com/en-US/p/productSlug")
                .originalPrice("$10.99")
                .platform(Platform.EPIC_GAMES)
                .isDLC(false)
                .storeMedia(Map.of("DieselStoreFrontTall", "url-1", "DieselStoreFrontWide", "url-2"))
                .media(List.of("url-3", "url-4"))
                .expirationEpoch(30117106800L)
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
                .expirationEpoch(30117106800L)
                .build();

        Game gameThree = Game.builder()
                .title("Game 3")
                .description("Cool game.")
                .url("https://store.epicgames.com/en-US/p/productSlug")
                .originalPrice("$10.99")
                .platform(Platform.EPIC_GAMES)
                .isDLC(false)
                .storeMedia(Map.of("DieselStoreFrontTall", "url-1", "DieselStoreFrontWide", "url-2"))
                .media(List.of("url-3", "url-4"))
                .expirationEpoch(30117106800L)
                .build();

        expectedGamesWithDLCsList = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwo), new ScraperResult(gameThree));
        expectedGamesWithoutDLCsList = List.of(new ScraperResult(gameOne), new ScraperResult(gameThree));
    }

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        CONFIG.includeDLCs(true);

        Collection<ScraperResult> returnedGames = epicGamesScraper.retrieveResults();
        TestHelper.assertCollectionEquals(expectedGamesWithDLCsList, returnedGames);
    }

    @Test
    void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        CONFIG.includeDLCs(false);

        Collection<ScraperResult> returnedGames = epicGamesScraper.retrieveResults();
        TestHelper.assertCollectionEquals(expectedGamesWithoutDLCsList, returnedGames);
    }

}
