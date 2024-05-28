package me.theforbiddenai.gamefinder.webscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.exception.WebScrapeException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public abstract class WebScraper {

    protected static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private static final int TIMEOUT_SECONDS = 10;
    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper mapper;
    private final String cookies;
    private final OkHttpClient httpClient;

    public WebScraper(ObjectMapper mapper, String cookies) {
        this.mapper = mapper;
        this.cookies = cookies;
        this.httpClient = new OkHttpClient();
    }

    /**
     * Web scrapes the remaining data for a game object. The data being web scraped depends on the implementation of
     * updateGame
     *
     * @param game The game being updated
     * @return A CompletableFuture containing the updated game
     */
    public CompletableFuture<Game> modifyGameAttributes(Game game) {
        return CompletableFuture.supplyAsync(() -> getHTML(game.getUrl()), CONFIG.getExecutorService())
                .thenApply(html -> {
                    try {
                        modifyGameAttributes(html, game);
                    } catch (GameRetrievalException | IOException e) {
                        throw new CompletionException(e);
                    }
                    return game;
                }).orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Adds the remaining information to a game object. What data needs to be added is dependent on the game's platform
     *
     * @param html The html of the game's listing page
     * @param game The game object being updated
     * @throws GameRetrievalException If there is some error updating the game
     * @throws IOException            If there is some issue parsing the html
     */
    protected abstract void modifyGameAttributes(String html, Game game) throws GameRetrievalException, IOException;

    /**
     * Gets a website's HTML
     *
     * @param url The url of the website
     * @return A string containing the page's HTML
     * @throws WebScrapeException If the request to connect to the website fails or the response body is null
     */
    private String getHTML(String url) throws WebScrapeException {
        Request request = new Request.Builder()
                .url(url)
                .header("cookie", this.cookies)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();

            if (responseBody == null)
                throw new WebScrapeException("Unable to retrieve HTML from " + url);

            return responseBody.string();
        } catch (IOException ex) {
            throw new WebScrapeException("Unable to connect to " + url, ex);
        }
    }

}
