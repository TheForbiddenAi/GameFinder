package me.theforbiddenai.gamefinder;

import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.GameScraper;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.commons.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GameFinderTest {

    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private GameFinder gameFinder;

    private EpicGamesScraper mockEpicGamesScraper;
    private SteamScraper mockSteamScraper;

    private List<Game> expectedEpicGamesResults;
    private List<Game> expectedSteamResults;

    @BeforeAll
    void setupTests() throws IllegalAccessException {
        this.gameFinder = new GameFinder();
        this.mockEpicGamesScraper = mock(EpicGamesScraper.class);
        this.mockSteamScraper = mock(SteamScraper.class);

        injectMockScrapers();
    }

    /**
     * Injects the mockScraper objects into the scraper field of a GameFinder class
     *
     * @throws IllegalAccessException If the scraper field is unable to be set
     */
    private void injectMockScrapers() throws IllegalAccessException {
        // Pull out private gameScrapers field from GameFinder class
        Field field = ReflectionUtils.findFields(GameFinder.class, f -> f.getName().equals("gameScrapers"),
                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .get(0);

        List<GameScraper> mockGameScrapers = List.of(mockEpicGamesScraper, mockSteamScraper);

        // Set the field to accessible
        field.setAccessible(true);
        // Set the scrapers value to be the list of mockedScrapers
        field.set(gameFinder, mockGameScrapers);
        // Set the field to be inaccessible
        field.setAccessible(false);
    }

    @BeforeEach
    void setupScrapers() throws GameRetrievalException {
        List<ScraperResult> epicGamesResults = List.of(
                new ScraperResult(
                        Game.builder()
                                .title("Game 1")
                                .build()
                ),
                new ScraperResult(
                        CompletableFuture.completedFuture(
                                Game.builder()
                                        .title("Game 2")
                                        .build())
                )
        );

        List<ScraperResult> steamResults = List.of(
                new ScraperResult(
                        Game.builder()
                                .title("Game 3")
                                .build()
                ),
                new ScraperResult(
                        CompletableFuture.completedFuture(
                                Game.builder()
                                        .title("Game 4")
                                        .build())
                )
        );

        this.expectedEpicGamesResults = resultsToGame(epicGamesResults);
        this.expectedSteamResults = resultsToGame(steamResults);

        when(mockEpicGamesScraper.retrieveResults()).thenReturn(epicGamesResults);
        when(mockEpicGamesScraper.getPlatform()).thenReturn(Platform.EPIC_GAMES);

        when(mockSteamScraper.retrieveResults()).thenReturn(steamResults);
        when(mockSteamScraper.getPlatform()).thenReturn(Platform.STEAM);
    }

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        CONFIG.getEnabledPlatforms().addAll(List.of(Platform.EPIC_GAMES, Platform.STEAM));

        List<Game> expectedGames = new ArrayList<>();
        expectedGames.addAll(expectedEpicGamesResults);
        expectedGames.addAll(expectedSteamResults);

        List<Game> actualGames = gameFinder.retrieveGames();

        // Using this instead of assertEquals on the lists,
        // allows the returned list order to not be considered in the equality check
        assertEquals(expectedGames.size(), actualGames.size());
        assertTrue(expectedGames.containsAll(actualGames));
    }

    @Test
    void testRetrieveGamesAsync() throws InterruptedException {
        CONFIG.getEnabledPlatforms().addAll(List.of(Platform.EPIC_GAMES, Platform.STEAM));

        List<Game> expectedGames = new ArrayList<>();
        expectedGames.addAll(expectedEpicGamesResults);
        expectedGames.addAll(expectedSteamResults);

        List<Game> actualGames = new ArrayList<>();

        gameFinder.retrieveGamesAsync(actualGames::addAll);

        // Wait for retrieveGamesAsync to finish executing
        CONFIG.getExecutorService().awaitTermination(30, TimeUnit.SECONDS);

        // Confirm that the actualGames list is equal to expectGames (excluding order)
        assertEquals(expectedGames.size(), actualGames.size());
        assertTrue(expectedGames.containsAll(actualGames));

    }

    /**
     * Converts a list of ScraperResults into game objects
     *
     * @param results The ScraperResult list
     * @return A list of games
     */
    private List<Game> resultsToGame(List<ScraperResult> results) {
        return results.stream()
                .map(scraperResult -> {
                    // If game isn't null, return it
                    if (scraperResult.getGame() != null)
                        return scraperResult.getGame();
                    // Get result from future
                    return scraperResult.getFutureGame().join();
                }).toList();
    }

}
