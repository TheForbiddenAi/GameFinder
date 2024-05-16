package me.theforbiddenai.gamefinder.scraper;

import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.utilities.SteamWebScrape;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SteamWebScraperTest {

    private SteamWebScrape webScraper;

    private MockedStatic<Jsoup> mockJsoup;

    @SuppressWarnings("rawtypes")
    private MockedStatic<CompletableFuture> mockCompletableFuture;

    @BeforeAll
    public void setupTests() throws IOException {
        this.webScraper = new SteamWebScrape();
        mockJsoup = Mockito.mockStatic(Jsoup.class);

        Document document = Jsoup.parse(SteamWebScraperTest.class.getResourceAsStream("/scraper/steam_data/steam-app-page-with-discount.html"), "UTF-8", "");

        Connection mockConnection = mock(Connection.class);
        // Setup mockConnection
        when(mockConnection.maxBodySize(Mockito.anyInt())).thenReturn(mockConnection);
        when(mockConnection.cookie(Mockito.anyString(), Mockito.anyString())).thenReturn(mockConnection);
        when(mockConnection.get()).thenReturn(document);

        // Intercept CompletableFuture.supplyAsync calls and inject static mock for jsoup
        mockCompletableFuture = mockStatic(CompletableFuture.class, invoker -> {
            // Continue executing CompletableFuture without inject if supplyAsync isn't called
            if (!invoker.getMethod().getName().equals("supplyAsync")) return invoker.callRealMethod();

            // Inject static mock inside CompletableFuture
            mockJsoup.when(() -> Jsoup.connect(Mockito.anyString())).thenReturn(mockConnection);

            // Continue executing completableFuture
            Supplier<?> supplier = invoker.getArgument(0);
            return CompletableFuture.completedFuture(supplier.get());
        });

    }

    @AfterAll
    public void closeStaticMocks() {
        mockJsoup.close();
        mockCompletableFuture.close();
    }

    @Test
    public void testWebScrapeExpirationEpoch() {
        Game game = Game.builder()
                .url("")
                .build();

        webScraper.webScrapeExpirationEpoch(game).join();
        assertEquals(1715878800L, game.getExpirationEpoch());
    }

}
