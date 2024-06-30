package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.GOGScraper;
import me.theforbiddenai.gamefinder.webscraper.GOGWebScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.commons.util.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GOGScraperTest {

    private GOGScraper scraper;
    private GOGWebScraper mockWebScraper;

    private JsonNode homePageSectionsNode;
    private JsonNode giveawaySectionsNode;
    private JsonNode catalogNode;

    private List<Game> expectedGames;
    private List<Game> expectedGamesWithoutDLCs;

    @BeforeAll
    void setupScraper() throws IOException, IllegalAccessException {
        ObjectMapper mapper = new ObjectMapper();
        this.homePageSectionsNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-home-page-sections.json"));
        this.giveawaySectionsNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-home-page-giveaway-section.json"));
        this.catalogNode = mapper.readTree(GOGScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-catalog-data.json"));

        // Inject return values into objectMapper map on readTree call
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class, answer -> {
            // Make sure the method being called is readTree, if not do not inject return values
            if (!answer.getMethod().getName().equals("readTree")) return answer.callRealMethod();

            // Ensure the argument passed is a URL object
            Object arg = answer.getArgument(0);
            if (!(arg instanceof URL url)) return answer.callRealMethod();

            // Return the correct value depending on the URL path
            switch (url.getPath()) {
                case "/v1/pages/2f" -> {
                    return this.homePageSectionsNode;
                }
                case "/v1/pages/2f/sections/2" -> {
                    return this.giveawaySectionsNode;
                }
                case "/v1/catalog" -> {
                    return this.catalogNode;
                }
            }

            return answer.callRealMethod();
        });

        this.scraper = new GOGScraper(mockObjectMapper);

        // Inject return values into mockWebScraper map on modifyGameAttributes call
        this.mockWebScraper = mock(GOGWebScraper.class, answer -> {
            // Make sure the method being called is modifyGameAttributes, if not do not inject return values
            if (!answer.getMethod().getName().equals("modifyGameAttributes")) return answer.callRealMethod();

            // Ensure the argument passed is a Game object
            Object arg = answer.getArgument(0);
            if (!(arg instanceof Game game)) return answer.callRealMethod();

            return CompletableFuture.completedFuture(game);
        });

        injectMockScraper();
    }

    /**
     * Injects the mockWebScraper objects into the webScraper field of a GOGScraper class
     *
     * @throws IllegalAccessException If the httpClient field is unable to be set
     */
    private void injectMockScraper() throws IllegalAccessException {
        // Pull out private httpClient field from WebScraper class
        Field field = ReflectionUtils.findFields(GOGScraper.class, f -> f.getName().equals("webScraper"),
                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .get(0);

        // Set the field to accessible
        field.setAccessible(true);
        // Set the webScraper value to be the mockWebScraper
        field.set(this.scraper, this.mockWebScraper);
        // Set the field to be inaccessible
        field.setAccessible(false);
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

        expectedGames = List.of(gameOne, gameTwo, gameThree);
        expectedGamesWithoutDLCs = List.of(gameOne, gameTwo);
    }

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);

        List<Game> actualGames = scraper.retrieveResults().stream()
                .map(ScraperResult::getFutureGame)
                .map(CompletableFuture::join)
                .toList();

        assertIterableEquals(expectedGames, actualGames);
    }

    @Test
    void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);

        List<Game> actualGames = scraper.retrieveResults().stream()
                .map(ScraperResult::getFutureGame)
                .map(CompletableFuture::join)
                .toList();

        assertIterableEquals(expectedGamesWithoutDLCs, actualGames);
    }

}
