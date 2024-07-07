package me.theforbiddenai.gamefinder.callback;

import me.theforbiddenai.gamefinder.domain.Game;

import java.util.Collection;

/**
 * This interface is used to define the game callback method signature that is called
 * when {@code GameFinder#retrieveGamesAsync(GameRetrievalCallback, GameRetrievalErrorCallback)} is called
 */
public interface GameRetrievalCallback {

    void retrieveGame(Collection<Game> games);

}
