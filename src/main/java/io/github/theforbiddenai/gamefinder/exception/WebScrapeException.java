package io.github.theforbiddenai.gamefinder.exception;

import java.util.concurrent.CompletionException;

/**
 * Thrown anytime there is an issue web scraping
 *
 * @author TheForbiddenAi
 */
public class WebScrapeException extends CompletionException {

    public WebScrapeException(String message) {
        super(message);
    }

    public WebScrapeException(String message, Throwable cause) {
        super(message, cause);
    }
}
