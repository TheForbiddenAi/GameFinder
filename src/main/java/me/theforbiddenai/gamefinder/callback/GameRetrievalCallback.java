package me.theforbiddenai.gamefinder.callback;

import me.theforbiddenai.gamefinder.domain.Game;

import java.util.List;

/**
 * This interface is used to define the callback method signature that is called
 * when GameFinder#retrieveGamesAsync(GameRetrievalCallback) is called
 */
public interface GameRetrievalCallback {

    void retrieveGame(List<Game> games);

}
