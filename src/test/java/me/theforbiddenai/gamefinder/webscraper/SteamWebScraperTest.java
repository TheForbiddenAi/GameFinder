package me.theforbiddenai.gamefinder.webscraper;

import me.theforbiddenai.gamefinder.domain.Game;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SteamWebScraperTest {

    private SteamWebScraper steamWebScraper;

    @BeforeAll
    void setupTests() throws IOException {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        this.steamWebScraper = new SteamWebScraper(mockHttpClient);

        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockBody = mock(ResponseBody.class);


        when(mockHttpClient.newCall(Mockito.any())).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.body()).thenReturn(mockBody);
        when(mockBody.byteStream()).thenReturn(SteamWebScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-app-page-with-discount.html"));
    }

    @Test
    void testSteamModifyGameAttributes() {
        Game game = Game.builder()
                .url("https://store.steampowered.com/")
                .build();

        this.steamWebScraper.modifyGameAttributes(game).join();
        assertEquals(1715878800L, game.getExpirationEpoch());
    }

}
