package me.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import me.theforbiddenai.gamefinder.callback.GameRetrievalCallback;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.Scraper;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import me.theforbiddenai.gamefinder.utilities.SteamRequests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class GameFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private final List<Scraper> scrapers;

    public GameFinder() {
        this.scrapers = new ArrayList<>();
        SteamRequests steamRequests = new SteamRequests(MAPPER);

        this.scrapers.add(new SteamScraper(MAPPER, steamRequests));
        this.scrapers.add(new EpicGamesScraper(MAPPER));
    }

    public List<Game> retrieveGames() throws GameRetrievalException {
        List<ScraperResult> scraperResults = new ArrayList<>();

        for (Scraper scraper : scrapers) {
            // Makes sure that the platform is enabled before retrieving games
            if (CONFIG.getEnabledPlatforms().contains(scraper.getPlatform())) {
                scraperResults.addAll(scraper.retrieveResults());
            }
        }

        List<Game> readyGames = new ArrayList<>();
        List<CompletableFuture<Game>> futureGames = new ArrayList<>();

        // Sort ScraperResults into readyGames and futureGames list
        sortScraperResults(scraperResults, readyGames, futureGames);

        // No need to attempt to process an empty futureGames list
        if (futureGames.isEmpty()) return readyGames;

        CompletableFuture<Void> allFutureGames = resolveFutureGames(readyGames, futureGames, null);

        try {
            // Wait for futureGames to resolve; this will block the thread
            allFutureGames.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new GameRetrievalException("Failed to collect steam games with web scraped expiration epochs", e);
        }

        return readyGames;
    }

    public void retrieveGamesAsync(GameRetrievalCallback callback) {
        CompletableFuture.runAsync(() -> {

            List<CompletableFuture<List<ScraperResult>>> scraperFutures = new ArrayList<>();

            // Loop through scrapers
            for (Scraper scraper : scrapers) {
                // Makes sure that the platform is enabled before retrieving games
                if (CONFIG.getEnabledPlatforms().contains(scraper.getPlatform())) {
                    // Run scraper.retrieveGames async
                    scraperFutures.add(getGamesFromScraperAsync(scraper));
                }
            }

            scraperFutures.forEach(future -> {
                List<Game> readyGames = new ArrayList<>();
                List<CompletableFuture<Game>> futureGames = new ArrayList<>();

                future.thenAccept(result -> {
                    // Sort results into games and futureGames
                    sortScraperResults(result, readyGames, futureGames);

                    if (!readyGames.isEmpty()) {
                        // These games are ready, so they are emitted
                        // A new ArrayList reference is passed in, so the old list can be reused
                        callback.retrieveGame(new ArrayList<>(readyGames));

                        // Clear list so it can be used for resolved futureGames
                        readyGames.clear();
                    }

                    // Resolve all CompletableFutures in futureGames and send the resolved games to the callback
                    if (!futureGames.isEmpty()) resolveFutureGames(readyGames, futureGames, callback);
                });
            });
        });
    }

    /**
     * Sorts a ScraperResult list into a Game list and a CompletableFuture<Game> List
     *
     * @param results     The list of ScraperResults
     * @param readyGames  The list where the ready games will be sorted into
     * @param futureGames The list where the unresolved games will be sorted into
     */
    private void sortScraperResults(
            List<ScraperResult> results,
            List<Game> readyGames,
            List<CompletableFuture<Game>> futureGames) {
        results.forEach(scraperResult -> {
            if (scraperResult.getGame() != null) readyGames.add(scraperResult.getGame());
            if (scraperResult.getFutureGame() != null) futureGames.add(scraperResult.getFutureGame());
        });
    }

    /**
     * Merges all futureGame futures into one CompletableFuture, which will add retrieved game objects to the results list when the future completes
     *
     * @param results     The list the resolved futureGames are added to
     * @param futureGames The list of unresolved games
     * @param callback    Object used to send ready games to a listener
     * @return A CompletableFuture compiled of all futureGame futures
     */
    private CompletableFuture<Void> resolveFutureGames(
            @NonNull List<Game> results,
            @NonNull List<CompletableFuture<Game>> futureGames,
            GameRetrievalCallback callback
    ) {
        int size = futureGames.size();

        // Compile all futureGames into one future
        CompletableFuture<Void> allFutureGames = CompletableFuture.allOf(futureGames.toArray(new CompletableFuture[size]));

        // Once allFutureGames is completed, add all games in futureGames to games list
        allFutureGames.thenAccept(v -> {
            // Join the completed futures to get their results and add them to results list
            futureGames.stream()
                    .map(CompletableFuture::join)
                    .forEach(results::add);

            if (callback != null) callback.retrieveGame(results);
        });

        return allFutureGames;
    }

    /**
     * Wraps a retrieveGames call from a scraper in a CompletableFuture
     *
     * @param scraper The scraper the games are being retrieved from
     * @return A CompletableFuture containing the retrieve results
     */
    private CompletableFuture<List<ScraperResult>> getGamesFromScraperAsync(Scraper scraper) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return scraper.retrieveResults();
            } catch (GameRetrievalException e) {
                throw new CompletionException(e);
            }
        });
    }

}
