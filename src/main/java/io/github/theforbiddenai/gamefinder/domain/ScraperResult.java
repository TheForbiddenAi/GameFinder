package io.github.theforbiddenai.gamefinder.domain;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.concurrent.CompletableFuture;

/**
 * Holds information that is returned by Scraper classes
 *
 * @author TheForbiddenAi
 */
@Getter
@ToString
@EqualsAndHashCode
public class ScraperResult {

    private final Game game;
    private final CompletableFuture<Game> futureGame;

    public ScraperResult(Game game) {
        this.game = game;
        this.futureGame = null;
    }

    public ScraperResult(CompletableFuture<Game> futureGame) {
        this.game = null;
        this.futureGame = futureGame;
    }

}
