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

    /**
     * Checks if an object is equal to this class
     * NOTE: Calling this method will cause futures to resolve in a blocking manner
     *
     * @param obj Object being checked for equality
     * @return True if both objects contain game and futureGame objects containing the same data
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ScraperResult other)) return false;

        // Check if both games are null. If not, check if they are equal to one another
        boolean gamesEqualOrNull = (this.game == null && other.game == null
                || this.game != null && this.game.equals(other.game));

        // Default this to null check so if one game is null and the other isn't, then this is false
        boolean futureGamesEqual = (this.futureGame == null && other.futureGame == null);

        // Compare futures if they are both not null
        if (this.futureGame != null && other.futureGame != null) {
            futureGamesEqual = this.futureGame
                    .thenCompose(gameOne -> other.futureGame.thenApply(gameOne::equals))
                    .join();
        }

        return gamesEqualOrNull && futureGamesEqual;

    }
}
