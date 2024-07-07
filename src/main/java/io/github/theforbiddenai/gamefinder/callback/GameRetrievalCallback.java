package io.github.theforbiddenai.gamefinder.callback;

import io.github.theforbiddenai.gamefinder.domain.Game;

import java.util.Collection;

/**
 * This interface is used to define the game callback method signature that is called
 * when {@code GameFinder#retrieveGamesAsync(GameRetrievalCallback, GameRetrievalErrorCallback)} is called
 */
public interface GameRetrievalCallback {

    void retrieveGame(Collection<Game> games);

}
