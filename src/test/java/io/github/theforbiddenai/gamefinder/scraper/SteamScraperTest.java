package io.github.theforbiddenai.gamefinder.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.theforbiddenai.gamefinder.GameFinderConfiguration;
import io.github.theforbiddenai.gamefinder.TestHelper;
import io.github.theforbiddenai.gamefinder.domain.Game;
import io.github.theforbiddenai.gamefinder.domain.Platform;
import io.github.theforbiddenai.gamefinder.domain.ScraperResult;
import io.github.theforbiddenai.gamefinder.exception.GameRetrievalException;
import io.github.theforbiddenai.gamefinder.scraper.impl.SteamScraper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SteamScraperTest {

    private SteamScraper steamScraper;

    private List<ScraperResult> expectedGamesWithDLCsList;
    private List<ScraperResult> expectedGamesWithoutDLCsList;
    private List<ScraperResult> expectedGamesWithoutMatureContentList;

    @BeforeAll
    void setupScraper() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode listTreeNode = mapper.readTree(SteamScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-games-list-test-data.json"));
        JsonNode itemTreeNode = mapper.readTree(SteamScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-getitems-test-data.json"));


        ObjectMapper mockObjectMapper = TestHelper.createMockURLObjectMapper(urlPath -> switch (urlPath) {
            case "/search/results/" -> listTreeNode;
            case "/IStoreBrowseService/GetItems/v1" -> itemTreeNode;
            default -> null;
        });

        this.steamScraper = new SteamScraper(mockObjectMapper);
    }

    @BeforeEach
    void setupGameLists() {
        Game gameApp = Game.builder()
                .title("App")
                .description("Cool App!")
                .originalPrice("$1.99")
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

        Game gamePackage = Game.builder()
                .title("Package")
                .description("N/A")
                .originalPrice("$2.99")
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

        Game gameBundle = Game.builder()
                .title("Bundle")
                .description("N/A")
                .originalPrice("$3.99")
                .url("https://store.steampowered.com/bundle/3")
                .isDLC(false)
                .platform(Platform.STEAM)
                .storeMedia(Map.of("main_capsule", "https://cdn.cloudflare.steamstatic.com/steam/bundles/3/eeeee/capsule_616x353.jpg?t=1"))
                .media(List.of())
                .expirationEpoch(1716310800L)
                .build();

        Game gameTwoNoMatureContent = Game.builder()
                .title("Package")
                .description("N/A")
                .originalPrice("$2.99")
                .url("https://store.steampowered.com/sub/2/Cool_Package")
                .isDLC(false)
                .platform(Platform.STEAM)
                .storeMedia(Map.of())
                .media(List.of("https://cdn.cloudflare.steamstatic.com/steam/subs/2/ss_1.jpg?t=1"))
                .expirationEpoch(1716310800L)
                .build();

        expectedGamesWithDLCsList = List.of(new ScraperResult(gameApp), new ScraperResult(gamePackage), new ScraperResult(gameBundle));
        expectedGamesWithoutMatureContentList = List.of(new ScraperResult(gameApp), new ScraperResult(gameTwoNoMatureContent), new ScraperResult(gameBundle));
        expectedGamesWithoutDLCsList = List.of(new ScraperResult(gamePackage), new ScraperResult(gameBundle));
    }

    @Test
    void testRetrieveGames() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(true);

        Collection<ScraperResult> returnedGames = steamScraper.retrieveResults();
        TestHelper.assertCollectionEquals(expectedGamesWithDLCsList, returnedGames);
    }

    @Test
    void testRetrieveGamesWithoutDLCs() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(false);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(true);

        Collection<ScraperResult> returnedGames = steamScraper.retrieveResults();
        TestHelper.assertCollectionEquals(expectedGamesWithoutDLCsList, returnedGames);
    }

    @Test
    void testRetrieveGamesWithoutMatureContent() throws GameRetrievalException {
        GameFinderConfiguration.getInstance().includeDLCs(true);
        GameFinderConfiguration.getInstance().allowSteamMatureContentScreenshots(false);

        Collection<ScraperResult> returnedGames = steamScraper.retrieveResults();
        TestHelper.assertCollectionEquals(expectedGamesWithoutMatureContentList, returnedGames);
    }

}
