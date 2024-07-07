package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.TestHelper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.GOGScraper;
import me.theforbiddenai.gamefinder.utilities.gog.GOGRequests;
import me.theforbiddenai.gamefinder.webscraper.GOGWebScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GOGScraperTest {

    private GOGScraper gogScraper;

    private List<Game> expectedGamesList;
    private List<Game> expectedGamesWithoutDLCsList;

    @BeforeAll
    void setupScraper() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        JsonNode homePageSectionsNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-home-page-sections.json"));
        JsonNode giveawaySectionsNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-home-page-giveaway-section.json"));
        JsonNode catalogNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-catalog-data.json"));

        // Inject return values into objectMapper map on readTree call

        ObjectMapper mockObjectMapper = TestHelper.createMockObjectMapper(urlPath -> switch (urlPath) {
            case "/v1/pages/2f" -> homePageSectionsNode;
            case "/v1/pages/2f/sections/2" -> giveawaySectionsNode;
            case "/v1/catalog" -> catalogNode;
            default -> null;
        });

        // Inject return values into mockWebScraper map on modifyGameAttributes call
        // Make sure the method being called is modifyGameAttributes, if not do not inject return values
        // Ensure the argument passed is a Game object
        GOGWebScraper mockGOGWebScraper = mock(GOGWebScraper.class, answer -> {
            // Make sure the method being called is modifyGameAttributes, if not do not inject return values
            if (!answer.getMethod().getName().equals("modifyGameAttributes")) return answer.callRealMethod();

            // Ensure the argument passed is a Game object
            Object arg = answer.getArgument(0);
            if (!(arg instanceof Game game)) return answer.callRealMethod();

            return CompletableFuture.completedFuture(game);
        });

        this.gogScraper = new GOGScraper(mockObjectMapper, new GOGRequests(mockObjectMapper), mockGOGWebScraper);
    }

    @BeforeEach
    void setupGameLists() {
        Game gameOne = Game.builder()
                .title("Game")
                .url("https://www.gog.com/en/game/slug")
                .isDLC(false)
                .platform(Platform.GOG)
                .storeMedia(Map.ofEntries(
                        Map.entry("coverHorizontal", "coverHorizontal.png"),
                        Map.entry("coverVertical", "coverVertical.jpg")
                ))
                .media(List.of("ss1.jpg", "ss2.jpg", "ss3.jpg"))
                .build();
        Game gameTwo = Game.builder()
                .title("Game 2")
                .url("https://www.gog.com/en/game/slug2")
                .isDLC(false)
                .platform(Platform.GOG)
                .storeMedia(Map.ofEntries(
                        Map.entry("coverHorizontal", "coverHorizontal2.png"),
                        Map.entry("coverVertical", "coverVertical2.jpg")
                ))
                .media(List.of("ss1.jpg"))
                .build();
        Game gameThree = Game.builder()
                .title("DLC")
                .url("https://www.gog.com/en/game/slug3")
                .isDLC(true)
                .platform(Platform.GOG)
                .build();

        expectedGamesList = List.of(gameOne, gameTwo, gameThree);
        expectedGamesWithoutDLCsList = List.of(gameOne, gameTwo);
    }

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);

        List<Game> actualGames = gogScraper.retrieveResults().stream()
                .map(ScraperResult::getFutureGame)
                .map(CompletableFuture::join)
                .toList();

        TestHelper.assertCollectionEquals(expectedGamesList, actualGames);
    }

    @Test
    void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);

        List<Game> actualGames = gogScraper.retrieveResults().stream()
                .map(ScraperResult::getFutureGame)
                .map(CompletableFuture::join)
                .toList();

        TestHelper.assertCollectionEquals(expectedGamesWithoutDLCsList, actualGames);
    }

}
