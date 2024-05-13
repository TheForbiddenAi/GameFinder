package me.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.Scraper;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import me.theforbiddenai.gamefinder.utilities.SteamRequests;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GameFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private final List<Scraper> scrapers;
    private final SteamRequests steamRequests;

    public GameFinder() {
        this.scrapers = new ArrayList<>();
        this.steamRequests = new SteamRequests(MAPPER);

        this.scrapers.add(new SteamScraper(MAPPER, this.steamRequests));
        this.scrapers.add(new EpicGamesScraper(MAPPER));
    }

    public List<Game> retrieveGames() throws GameRetrievalException {
        List<Game> games = new ArrayList<>();

        for (Scraper scraper : scrapers) {
            // Makes sure that the platform is enabled before retrieving games
            if (CONFIG.getEnabledPlatforms().contains(scraper.getPlatform())) {
                games.addAll(scraper.retrieveGames());
            }
        }

        List<CompletableFuture<Void>> webscrapeFutures = steamRequests.getWebscrapeFutures();
        CompletableFuture<Void> futures = CompletableFuture.allOf(webscrapeFutures.toArray(new CompletableFuture[0]));
        // Clear webscrape futures as they are now in futures
        webscrapeFutures.clear();

        try {
            futures.join();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return games;
    }

    /**
     * Maybe event system?
     */

}
