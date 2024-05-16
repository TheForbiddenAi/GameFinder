package me.theforbiddenai.gamefinder.exception;

/**
 * Thrown whenever there is an issue retrieving games from a service
 *
 * @author TheForbiddenAi
 */
public class GameRetrievalException extends Exception {

    public GameRetrievalException(String message) {
        super(message);
    }

    public GameRetrievalException(String message, Throwable cause) {
        super(message, cause);
    }

}
