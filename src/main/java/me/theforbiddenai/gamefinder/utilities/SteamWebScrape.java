package me.theforbiddenai.gamefinder.utilities;

import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public class SteamWebScrape {

    /**
     * Web scrapes an expiration date from steam asynchronously
     *
     * @param game The game object
     * @return A CompletableFuture
     */
    public CompletableFuture<Game> webScrapeExpirationEpoch(Game game) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Connect to game page
                return Jsoup.connect(game.getUrl())
                        .maxBodySize(0)
                        // Set birthtime cookie to bypass age gate
                        .cookie("birthtime", "568022401")
                        .get();
            } catch (IOException e) {
                throw new CompletionException("Unable to web scrape expiration time for ", e);
            }
        }).thenApply(document -> {
            // Parse the document for the expiration epoch and set it in the game object
            game.setExpirationEpoch(getExpirationEpoch(document));
            return game;
        }).orTimeout(5, TimeUnit.SECONDS);
    }

    /**
     * Extracts expiration date from jsoup document and converts it to epoch second
     *
     * @param document The jsoup document
     * @return The expiration epoch second or GameFinderConstants.NO_EXPIRATION_EPOCH
     */
    private long getExpirationEpoch(Document document) {
        Elements elements = document.select("div.game_area_purchase_game");

        Optional<Element> purchaseSection = elements.stream()
                .filter(ele -> ele.selectFirst("div.discount_pct:contains(-100%)") != null)
                .findFirst();

        if (purchaseSection.isEmpty()) return GameFinderConstants.NO_EXPIRATION_EPOCH;

        Element paragraph = purchaseSection.get().selectFirst("p.game_purchase_discount_quantity");
        if (paragraph == null) return GameFinderConstants.NO_EXPIRATION_EPOCH;
        Matcher matcher = GameFinderConstants.STEAM_MONTH_DAY_TIME_REGEX.matcher(paragraph.text());

        if (!matcher.find()) return GameFinderConstants.NO_EXPIRATION_EPOCH;
        String expirationDate = matcher.group(0)
                .replace("am", "AM")
                .replace("pm", "PM");

        // Parse expirationDate using GameFinderConstants.STEAM_DATE_FORMAT
        // By default Steam will give a date in PST, so it is converted to the
        // timezone of the system then converted to an Instant and the epochSecond is extracted
        return LocalDateTime.parse(expirationDate, GameFinderConstants.STEAM_DATE_FORMAT)
                .atZone(ZoneId.of("America/Los_Angeles"))
                .withZoneSameInstant(ZoneId.systemDefault())
                .toInstant()
                .getEpochSecond();
    }

}
