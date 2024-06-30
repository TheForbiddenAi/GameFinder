package me.theforbiddenai.gamefinder.webscraper;

import me.theforbiddenai.gamefinder.domain.Game;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Mockito;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SteamWebScraperTest {

    private SteamWebScraper webScraper;

    private OkHttpClient mockHttpClient;

    @BeforeAll
    void setupTests() throws IOException, IllegalAccessException {
        this.webScraper = new SteamWebScraper();
        this.mockHttpClient = mock(OkHttpClient.class);

        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);


        when(mockHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.byteStream()).thenReturn(SteamWebScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-app-page-with-discount.html"));

        injectMockHttpClient();
    }

    /**
     * Injects the mockHttpClient objects into the httpClient field of a WebScraper class
     *
     * @throws IllegalAccessException If the httpClient field is unable to be set
     */
    private void injectMockHttpClient() throws IllegalAccessException {
        // Pull out private httpClient field from WebScraper class
        Field field = ReflectionUtils.findFields(WebScraper.class, f -> f.getName().equals("httpClient"),
                        ReflectionUtils.HierarchyTraversalMode.TOP_DOWN)
                .get(0);

        // Set the field to accessible
        field.setAccessible(true);
        // Set the httpClient value to be the mockHttpClient
        field.set(this.webScraper, this.mockHttpClient);
        // Set the field to be inaccessible
        field.setAccessible(false);
    }

    @Test
    void testWebScrapeExpirationEpoch() {
        Game game = Game.builder()
                .url("https://store.steampowered.com/")
                .build();

        this.webScraper.modifyGameAttributes(game).join();
        assertEquals(1715878800L, game.getExpirationEpoch());
    }

}
