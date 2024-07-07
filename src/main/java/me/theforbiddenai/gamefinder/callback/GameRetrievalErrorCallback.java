package me.theforbiddenai.gamefinder.callback;

/**
 * This interface is used to define the error callback method signature that is called
 * when {@code GameFinder#retrieveGamesAsync(GameRetrievalCallback, GameRetrievalErrorCallback)} is called
 */
public interface GameRetrievalErrorCallback {

    void handleError(Throwable throwable);

}
