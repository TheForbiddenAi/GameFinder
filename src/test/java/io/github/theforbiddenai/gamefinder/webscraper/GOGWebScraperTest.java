package io.github.theforbiddenai.gamefinder.webscraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.theforbiddenai.gamefinder.TestHelper;
import io.github.theforbiddenai.gamefinder.domain.Game;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GOGWebScraperTest {

    private GOGWebScraper gogWebScraper;
    private Game expectedGame;

    @BeforeAll
    void setupTests() throws IOException {
        OkHttpClient mockHttpClient = TestHelper.setupOkHttpMocks(GOGWebScraperTest.class.getResourceAsStream("/scraper/gog_data/gog-game-page.html"));
        this.gogWebScraper = new GOGWebScraper(mockHttpClient, new ObjectMapper());
    }

    @BeforeEach
    void setupExpectedGame() {
        expectedGame = Game.builder()
                .description("Cool Game")
                .url("https://gog.com/")
                .originalPrice("$49.99")
                .storeMedia(Map.ofEntries(
                        Map.entry("galaxyBackgroundImage", "https://images.gog-statics.com/1.jpg"),
                        Map.entry("backgroundImage", "https://images.gog-statics.com/2.jpg"),
                        Map.entry("image", "https://images.gog-statics.com/3.jpg"),
                        Map.entry("boxArtImage", "https://images.gog-statics.com/4.jpg"),
                        Map.entry("logo", "https://images.gog-statics.com/5.png")
                ))
                .expirationEpoch(1720681199L)
                .build();
    }

    @Test
    void testGOGModifyGameAttributes() {
        Game actualGame = Game.builder()
                .url("https://gog.com/")
                .build();

        this.gogWebScraper.modifyGameAttributes(actualGame).join();
        assertEquals(this.expectedGame, actualGame);
        assertEquals(this.expectedGame.hashCode(), actualGame.hashCode());
    }

}
