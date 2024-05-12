package me.theforbiddenai.gamefinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.scraper.Scraper;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import me.theforbiddenai.gamefinder.scraper.impl.SteamScraper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GameFinder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private final List<Scraper> scrapers;

    public GameFinder() {
        this.scrapers = new ArrayList<>();

        scrapers.add(new SteamScraper(MAPPER));
        scrapers.add(new EpicGamesScraper(MAPPER));
    }

    public List<Game> retrieveGames() throws IOException {
        List<Game> games = new ArrayList<>();

        for (Scraper scraper : scrapers) {
            // Makes sure that the platform is enabled before retrieving games
            if (CONFIG.getEnabledPlatforms().contains(scraper.getPlatform())) {
                games.addAll(scraper.retrieveGames());
            }
        }

        return games;
    }

    /**
     * Maybe event system?
     */

}
