package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;

import java.util.List;


/**
 * Defines common functionality and abstract methods for classes that retrieve free games from a service
 *
 * @author TheForbiddenAi
 */
public abstract class GameScraper {

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;

    @Getter
    private final Platform platform;

    public GameScraper(ObjectMapper objectMapper, Platform platform) {
        this.objectMapper = objectMapper;
        this.platform = platform;
    }

    /**
     * Retrieves 100% off games/DLCs (depending on configuration) from a platform
     *
     * @return A list of 100% off games/DLCs
     * @throws GameRetrievalException If the mapper is unable to parse the JSON data
     */
    public abstract List<ScraperResult> retrieveResults() throws GameRetrievalException;

}
