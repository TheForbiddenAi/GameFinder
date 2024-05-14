package me.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        List<ScraperResult> scraperResults = retrieveScraperResults();

        List<Game> games = new ArrayList<>();
        List<CompletableFuture<Game>> futureGames = new ArrayList<>();

        // Sort ScraperResults
        scraperResults.forEach(result -> {
            if(result.getGame() != null) games.add(result.getGame());
            if(result.getFutureGame() != null) futureGames.add(result.getFutureGame());
        });

        int size = futureGames.size();

        // Compile all futureGames into one future
        CompletableFuture<Void> allFutureGames = CompletableFuture.allOf(futureGames.toArray(new CompletableFuture[size]));

        // Once allFutureGames is completed, add all games in futureGames to games list
        allFutureGames.thenAccept(v -> futureGames.stream()
                .map(CompletableFuture::join)
                .forEach(games::add));

        try {
            allFutureGames.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new GameRetrievalException("Failed to collect steam games with web scraped expiration epochs", e);
        }

        return games;
    }

    public void retrieveGamesAsync(GameRetrievalCallback callback) {
        //TODO: Each request would need to happen async. This requires change in how Scraper Works; add new function
    }

    private List<ScraperResult> retrieveScraperResults() throws GameRetrievalException {
        List<ScraperResult> scraperResults = new ArrayList<>();

        for (Scraper scraper : scrapers) {
            // Makes sure that the platform is enabled before retrieving games
            if (CONFIG.getEnabledPlatforms().contains(scraper.getPlatform())) {
                scraperResults.addAll(scraper.retrieveGames());
            }
        }
        return scraperResults;
    }
}
