package io.github.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import io.github.theforbiddenai.gamefinder.callback.GameRetrievalErrorCallback;
import io.github.theforbiddenai.gamefinder.exception.GameRetrievalException;
import io.github.theforbiddenai.gamefinder.scraper.GameScraper;
import io.github.theforbiddenai.gamefinder.scraper.impl.GOGScraper;
import io.github.theforbiddenai.gamefinder.callback.GameRetrievalCallback;
import io.github.theforbiddenai.gamefinder.domain.Game;
import io.github.theforbiddenai.gamefinder.domain.ScraperResult;
import io.github.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import io.github.theforbiddenai.gamefinder.scraper.impl.SteamScraper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Main class for GameFinder; contains the functions that are used to retrieve
 * games both synchronously and asynchronously from all enabled platforms
 *
 * @author TheForbiddenAi
 */
public class GameFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();

    private final List<GameScraper> gameScrapers;

    public GameFinder() {
        this.gameScrapers = new ArrayList<>();

        this.gameScrapers.add(new SteamScraper(MAPPER));
        this.gameScrapers.add(new EpicGamesScraper(MAPPER));
        this.gameScrapers.add(new GOGScraper(MAPPER));
    }

    public GameFinder(List<GameScraper> gameScrapers) {
        this.gameScrapers = gameScrapers;
    }

    /**
     * Retrieves games with a 100% discount from all the platforms listed
     * in {@link GameFinderConfiguration#getEnabledPlatforms()}. This function
     * is synchronous. However, JSoup web scraping is done asynchronously in a
     * non-blocking manner until all other games have been processed
     *
     * @return A list of retrieved games
     * @throws GameRetrievalException If the games can not be retrieved for some reason
     */
    public List<Game> retrieveGames() throws GameRetrievalException {
        List<ScraperResult> scraperResults = new ArrayList<>();

        for (GameScraper gameScraper : gameScrapers) {
            // Makes sure that the platform is enabled before retrieving games
            if (CONFIG.getEnabledPlatforms().contains(gameScraper.getPlatform())) {
                scraperResults.addAll(gameScraper.retrieveResults());
            }
        }

        List<Game> readyGameList = new ArrayList<>();
        List<CompletableFuture<Game>> futureGameList = new ArrayList<>();

        // Sort ScraperResults into readyGameList and futureGameList list
        sortScraperResults(scraperResults, readyGameList, futureGameList);

        // No need to attempt to process an empty futureGameList list
        if (futureGameList.isEmpty()) return readyGameList;

        CompletableFuture<Void> allFutureGames = resolveFutureGames(readyGameList, futureGameList, null, null);

        try {
            // Wait for futureGameList to resolve; this will block the thread
            allFutureGames.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new GameRetrievalException("Failed to collect steam games with web scraped expiration epochs", e);
        }

        return readyGameList;
    }

    /**
     * Retrieves games with a 100% discount from all the platforms listed
     * in {@link GameFinderConfiguration#getEnabledPlatforms()}. This function
     * is asynchronous. As games are retrieved the callback function is called
     *
     * @param callback      The function that is called once a batch of games is ready
     * @param errorCallback The function that is called if an exception is thrown
     */
    public void retrieveGamesAsync(@NonNull GameRetrievalCallback callback, @NonNull GameRetrievalErrorCallback errorCallback) {
        CompletableFuture.runAsync(() -> {

            List<CompletableFuture<Collection<ScraperResult>>> scraperFutureList = new ArrayList<>();

            // Loop through scrapers
            for (GameScraper gameScraper : gameScrapers) {
                // Makes sure that  the platform is enabled before retrieving games
                if (CONFIG.getEnabledPlatforms().contains(gameScraper.getPlatform())) {
                    // Run scraper.retrieveGames async
                    scraperFutureList.add(getGamesFromScraperAsync(gameScraper, errorCallback));
                }
            }

            scraperFutureList.forEach(future -> {
                List<Game> readyGamesList = new ArrayList<>();
                List<CompletableFuture<Game>> futureGamesList = new ArrayList<>();

                future.thenAccept(scraperResultList -> {
                    // Disregard if null. This happens if there was an error thrown when retrieving the games
                    if (scraperResultList == null) return;

                    // Sort results into games and futureGamesList
                    sortScraperResults(scraperResultList, readyGamesList, futureGamesList);

                    // Send out all ready games, then clear readyGamesList
                    sendReadyGames(readyGamesList, callback);

                    // Resolve all CompletableFutures in futureGamesList and send the resolved games to the callback
                    resolveFutureGames(readyGamesList, futureGamesList, callback, errorCallback);
                }).exceptionally(throwable -> {
                    errorCallback.handleError(throwable);
                    return null;
                });

            });
        }, CONFIG.getExecutorService()).exceptionally(throwable -> {
            errorCallback.handleError(throwable);
            return null;
        });
    }

    /**
     * Sends a list of ready games to the callback
     *
     * @param readyGamesList The list of ready games
     * @param callback       The function that is called once a batch of games is ready
     */
    private void sendReadyGames(List<Game> readyGamesList, GameRetrievalCallback callback) {
        // Do not continue if readyGamesList is empty. Don't want to send an empty list to the callback
        if (readyGamesList.isEmpty()) return;

        // These games are ready, so they are emitted
        // A new ArrayList reference is passed in, so the old list can be reused
        callback.retrieveGame(new ArrayList<>(readyGamesList));

        // Clear list so it can be used for resolved futureGameList
        readyGamesList.clear();
    }

    /**
     * Sorts a ScraperResult list into a Game list and a CompletableFuture<Game> List
     *
     * @param results     The list of ScraperResults
     * @param readyGames  The list where the ready games will be sorted into
     * @param futureGames The list where the unresolved games will be sorted into
     */
    private void sortScraperResults(
            Collection<ScraperResult> results,
            List<Game> readyGames,
            List<CompletableFuture<Game>> futureGames) {
        results.forEach(scraperResult -> {
            if (scraperResult.getGame() != null) {
                readyGames.add(scraperResult.getGame());
            }
            if (scraperResult.getFutureGame() != null) {
                futureGames.add(scraperResult.getFutureGame());
            }
        });
    }

    /**
     * Merges all futureGame futures into one CompletableFuture, which will add retrieved game objects to the resultsList when the future completes
     *
     * @param resultsList     The list the resolved futureGamesList are added to
     * @param futureGamesList The list of unresolved games
     * @param callback        The function that is called once a batch of games is ready
     * @param errorCallback   The function that is called if an exception is thrown
     * @return A CompletableFuture compiled of all futureGame futures
     */
    private CompletableFuture<Void> resolveFutureGames(
            @NonNull List<Game> resultsList,
            @NonNull List<CompletableFuture<Game>> futureGamesList,
            GameRetrievalCallback callback,
            GameRetrievalErrorCallback errorCallback
    ) {
        int size = futureGamesList.size();
        // Don't continue if futureGamesList is empty
        if (size == 0) return CompletableFuture.completedFuture(null);

        // Compile all futureGamesList into one future
        CompletableFuture<Void> allFutureGames = CompletableFuture.allOf(futureGamesList.toArray(new CompletableFuture[size]))
                .exceptionally(throwable -> {
                    errorCallback.handleError(throwable);
                    return null;
                });

        // Once allFutureGames is completed, add all games in futureGamesList to games list
        allFutureGames.thenAccept(v -> {
            // Join the completed futures to get their game objects and add them to resultsList
            futureGamesList.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .forEach(resultsList::add);

            if (callback != null && !resultsList.isEmpty()) {
                callback.retrieveGame(resultsList);
            }
        });

        return allFutureGames;
    }

    /**
     * Wraps a retrieveGames call from a scraper in a CompletableFuture
     *
     * @param gameScraper   The scraper the games are being retrieved from
     * @param errorCallback The function that is called if an exception is thrown
     * @return A CompletableFuture containing the retrieve results
     */
    private CompletableFuture<Collection<ScraperResult>> getGamesFromScraperAsync(GameScraper gameScraper, GameRetrievalErrorCallback errorCallback) {
        return CompletableFuture.supplyAsync(gameScraper::retrieveResults, CONFIG.getExecutorService())
                .exceptionally(throwable -> {
                    errorCallback.handleError(throwable);
                    return null;
                });
    }

}
