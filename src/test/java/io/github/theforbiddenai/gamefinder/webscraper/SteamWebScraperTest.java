package io.github.theforbiddenai.gamefinder.webscraper;

import io.github.theforbiddenai.gamefinder.TestHelper;
import io.github.theforbiddenai.gamefinder.domain.Game;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SteamWebScraperTest {

    private SteamWebScraper steamWebScraper;

    @BeforeAll
    void setupTests() throws IOException {
        OkHttpClient mockHttpClient = TestHelper.setupOkHttpMocks(SteamWebScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-app-page-with-discount.html"));
        this.steamWebScraper = new SteamWebScraper(mockHttpClient);
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
