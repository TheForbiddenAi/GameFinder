package me.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.GameFinderConfiguration;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.domain.ScraperResult;
import me.theforbiddenai.gamefinder.exception.GameRetrievalException;
import me.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import me.theforbiddenai.gamefinder.utilities.SteamRequests;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.mock;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SteamScraperTest {

    private SteamScraper scraper;
    private JsonNode listTreeNode;
    private JsonNode itemTreeNode;

    private List<ScraperResult> expectedGamesWithDLCs;
    private List<ScraperResult> expectedGamesWithoutDLCs;
    private List<ScraperResult> expectedGamesWithoutMatureContent;

    @BeforeAll
    public void setupScraper() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.listTreeNode = mapper.readTree(SteamScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-games-list-test-data.json"));
        this.itemTreeNode = mapper.readTree(SteamScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-getitems-test-data.json"));

        // Inject return values into objectMapper map on readTree call
        ObjectMapper mockObjectMapper = mock(ObjectMapper.class, answer -> {
            // Make sure the method being called is readTree, if not do not inject return values
            if (!answer.getMethod().getName().equals("readTree")) return answer.callRealMethod();

            // Ensure the argument passed is a URL object
            Object arg = answer.getArgument(0);
            if (!(arg instanceof URL url)) return answer.callRealMethod();

            // Return the correct value depending on the URL path
            switch (url.getPath()) {
                case "/search/results/" -> {
                    return this.listTreeNode;
                }
                case "/IStoreBrowseService/GetItems/v1" -> {
                    return this.itemTreeNode;
                }
            }

            return answer.callRealMethod();
        });

        SteamRequests mockSteamRequests = new SteamRequests(mockObjectMapper);
        this.scraper = new SteamScraper(mockObjectMapper, mockSteamRequests);
    }

    @BeforeEach
    public void setupGameLists() {
        Game gameOne = Game.builder()
                .title("App")
                .description("Cool App!")
                .url("https://store.steampowered.com/app/1/Cool_App")
                .platform(Platform.STEAM)
                .isDLC(true)
                .storeMedia(Map.of(
                        "header", "https://cdn.cloudflare.steamstatic.com/steam/apps/1/header.jpg?t=1",
                        "main_capsule", "https://cdn.cloudflare.steamstatic.com/steam/apps/1/capsule_616x353.jpg?t=1"
                ))
                .media(List.of(
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/1/ss_1.jpg?t=1",
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/1/ss_2.jpg?t=1",
                        "https://cdn.cloudflare.steamstatic.com/steam/apps/1/ss_3.jpg?t=1"))
                .expirationEpoch(1716310800L)
                .build();

        Game gameTwo = Game.builder()
                .title("Package")
                .description("N/A")
                .url("https://store.steampowered.com/sub/2/Cool_Package")
                .isDLC(false)
                .platform(Platform.STEAM)
                .storeMedia(Map.of())
                .media(List.of(
                        "https://cdn.cloudflare.steamstatic.com/steam/subs/2/ss_1.jpg?t=1",
                        "https://cdn.cloudflare.steamstatic.com/steam/subs/2/ss_2.jpg?t=1",
                        "https://cdn.cloudflare.steamstatic.com/steam/subs/2/ss_3.jpg?t=1"
                ))
                .expirationEpoch(1716310800L)
                .build();

        Game gameThree = Game.builder()
                .title("Bundle")
                .description("N/A")
                .url("https://store.steampowered.com/bundle/3")
                .isDLC(false)
                .platform(Platform.STEAM)
                .storeMedia(Map.of("main_capsule", "https://cdn.cloudflare.steamstatic.com/steam/bundles/1/eeeee/capsule_616x353.jpg?t=1"))
                .media(List.of())
                .expirationEpoch(1716310800L)
                .build();

        Game gameTwoNoMatureContent = Game.builder()
                .title("Package")
                .description("N/A")
                .url("https://store.steampowered.com/sub/2/Cool_Package")
                .isDLC(false)
                .platform(Platform.STEAM)
                .storeMedia(Map.of())
                .media(List.of(
                        "https://cdn.cloudflare.steamstatic.com/steam/subs/2/ss_1.jpg?t=1"
                ))
                .expirationEpoch(1716310800L)
                .build();

        expectedGamesWithDLCs = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwo), new ScraperResult(gameThree));
        expectedGamesWithoutMatureContent = List.of(new ScraperResult(gameOne), new ScraperResult(gameTwoNoMatureContent), new ScraperResult(gameThree));
        expectedGamesWithoutDLCs = List.of(new ScraperResult(gameTwo), new ScraperResult(gameThree));
    }

    @Test
    public void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(true);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithDLCs, returnedGames);
    }

    @Test
    public void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(true);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithoutDLCs, returnedGames);
    }

    @Test
    public void testRetrieveGamesWithoutMatureContent() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(false);

        List<ScraperResult> returnedGames = scraper.retrieveResults();
        assertIterableEquals(expectedGamesWithoutMatureContent, returnedGames);
    }

}
