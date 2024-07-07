package io.github.theforbiddenai.gamefinder;

import io.github.theforbiddenai.gamefinder.domain.Game;
import io.github.theforbiddenai.gamefinder.domain.Platform;
import io.github.theforbiddenai.gamefinder.domain.ScraperResult;
import io.github.theforbiddenai.gamefinder.exception.GameRetrievalException;
import io.github.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import io.github.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
    void setupTests() {
        this.mockEpicGamesScraper = mock(EpicGamesScraper.class);
        this.mockSteamScraper = mock(SteamScraper.class);

        this.gameFinder = new GameFinder(List.of(mockEpicGamesScraper, mockSteamScraper));
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
        TestHelper.assertCollectionEquals(expectedGames, actualGames);
    }

    @Test
    void testRetrieveGamesAsync() throws InterruptedException {
        CONFIG.getEnabledPlatforms().addAll(List.of(Platform.EPIC_GAMES, Platform.STEAM));

        List<Game> expectedGames = new ArrayList<>();
        expectedGames.addAll(expectedEpicGamesResults);
        expectedGames.addAll(expectedSteamResults);

        List<Game> actualGames = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();

        gameFinder.retrieveGamesAsync(actualGames::addAll, errors::add);

        // Wait for retrieveGamesAsync to finish executing
        CONFIG.getExecutorService().awaitTermination(30, TimeUnit.SECONDS);

        assertTrue(errors.isEmpty());
        // Confirm that the actualGames list is equal to expectGames (excluding order)
        TestHelper.assertCollectionEquals(expectedGames, actualGames);

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
