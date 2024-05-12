package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;

import java.util.List;

public abstract class Scraper {

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;

    @Getter
    private final Platform platform;

    public Scraper(ObjectMapper objectMapper, Platform platform) {
        this.objectMapper = objectMapper;
        this.platform = platform;
    }

    /**
     * Retrieves 100% off games/DLCs (depending on configuration) from a platform
     *
     * @return A list of 100% off games/DLCs
     * @throws GameRetrievalException If the mapper is unable to parse the JSON data
     */
    public abstract List<Game> retrieveGames() throws GameRetrievalException;

}
