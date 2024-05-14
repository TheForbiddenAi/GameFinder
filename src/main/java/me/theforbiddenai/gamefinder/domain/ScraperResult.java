package me.theforbiddenai.gamefinder.domain;

import lombok.Getter;

import java.util.concurrent.CompletableFuture;

@Getter
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
