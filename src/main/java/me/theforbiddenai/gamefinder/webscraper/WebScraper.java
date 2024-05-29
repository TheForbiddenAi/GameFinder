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
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for web scraping data from a game page
 *
 * @param <T> The type of object that will be used to store any retrieved data from the HTML of a game page
 * @author TheForbiddenAi
 */
public abstract class WebScraper<T> {

    protected static final GameFinderConfiguration CONFIG = GameFinderConfiguration.getInstance();
    private static final int TIMEOUT_SECONDS = 10;

    private final String cookies;
    private final OkHttpClient httpClient;

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper mapper;


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
        return CompletableFuture.supplyAsync(() -> getHTMLData(game.getUrl()), CONFIG.getExecutorService())
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
     * @param t    The data object containing the data required to complete a game object
     * @param game The game object being updated
     * @throws GameRetrievalException If there is some error updating the game
     * @throws IOException            If there is some issue parsing the html
     */
    protected abstract void modifyGameAttributes(T t, Game game) throws GameRetrievalException, IOException;

    /**
     * Retrieves the data needed to complete a game object from the HTML of a game page
     *
     * @param inputStream The HTML of the page
     * @param url         The url of the game page
     * @return An object containing the data required to complete a game object
     * @throws WebScrapeException If the required data is unable to be retrieved
     */
    protected abstract T processHTML(InputStream inputStream, String url) throws WebScrapeException;


    /**
     * Gets the required data from a website's HTML
     *
     * @param url The url of the website
     * @return A data object containing the information specified in {@link #processHTML(InputStream, String)}
     * @throws WebScrapeException If the request to connect to the website fails or the response body is null
     */
    private T getHTMLData(String url) throws WebScrapeException {
        Request request = new Request.Builder()
                .url(url)
                .header("cookie", this.cookies)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();

            if (responseBody == null)
                throw new WebScrapeException("Unable to retrieve HTML from " + url);


            return processHTML(responseBody.byteStream(), url);
        } catch (IOException ex) {
            throw new WebScrapeException("Unable to connect to " + url, ex);
        }
    }

}
