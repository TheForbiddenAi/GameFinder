package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.EpicGamesScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EpicGamesScraperTest {

    private EpicGamesScraper scraper;
    private JsonNode treeNode;

    private List<ScraperResult> expectedGamesWithDLCs;
    private List<ScraperResult> expectedGamesWithoutDLCs;

    @BeforeAll
    public void setupJson() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.treeNode = mapper.readTree(EpicGamesScraperTest.class.getResourceAsStream("/scraper/epic-games-scraper-test-data.json"));
    }

    @BeforeEach
    public void setupTests() throws IOException {
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class);
        when(mockObjectMapper.readTree(Mockito.any(URL.class))).thenReturn(treeNode);

        this.scraper = new EpicGamesScraper(mockObjectMapper);

        Game gameOne = Game.builder()
                .title("Game")
                .description("Cool game.")
                .url("https://store.epicgames.com/en-US/p/pageSlug")
                .platform(Platform.EPIC_GAMES)
                .isDLC(false)
                .storeMedia(Map.of("DieselStoreFrontTall", "url-1", "DieselStoreFrontWide", "url-2"))
                .media(List.of("url-3", "url-4"))
                .expirationEpoch(1715871600L)
                .build();

        Game gameTwo = Game.builder()
                .title("DLC")
                .description("Cool DLC.")
                .url("https://store.epicgames.com/en-US/p/offerSlug")
                .isDLC(true)
                .platform(Platform.EPIC_GAMES)
                .storeMedia(Map.of("OfferImageWide", "url-1", "OfferImageTall", "url-2"))
                .media(List.of("url-3"))
                .expirationEpoch(1715871600L)
                .build();

        expectedGamesWithDLCs = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwo));
        expectedGamesWithoutDLCs = List.of(new ScraperResult(gameOne));
    }

    @Test
    public void testJsonToGame() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithDLCs, returnedGames);
    }

    @Test
    public void testJsonToGameWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithoutDLCs, returnedGames);
    }

}
