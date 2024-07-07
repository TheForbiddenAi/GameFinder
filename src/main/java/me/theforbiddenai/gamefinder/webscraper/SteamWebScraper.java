package me.theforbiddenai.gamefinder.webscraper;

import me.theforbiddenai.gamefinder.constants.GameFinderConstants;
import me.theforbiddenai.gamefinder.domain.Game;
import okhttp3.OkHttpClient;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for web scraping expiration epochs from a steam listing page
 *
 * @author TheForbiddenAi
 */
public class SteamWebScraper extends WebScraper<Long> {

    // Matches strings like May 10 @ 1:00PM
    private static final Pattern STEAM_MONTH_DAY_TIME_REGEX = Pattern.compile("([A-z]{3} \\d{1,2}) (@ \\d{1,2}:\\d{2}(am|pm)?)");

    // DateTimeFormatter for dates like May 10 @ 1:00PM
    private static final DateTimeFormatter STEAM_DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("MMM d @ h:mma")
            .parseDefaulting(ChronoField.YEAR, Year.now().getValue())
            .toFormatter();

    public SteamWebScraper() {
        super("birthtime=568022401");
    }

    public SteamWebScraper(OkHttpClient httpClient) {
        super("birthtime=568022401", httpClient);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void modifyGameAttributes(Long expirationEpoch, Game game) {
        game.setExpirationEpoch(expirationEpoch);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long processHTML(InputStream inputStream, String url) {
        Scanner scanner = new Scanner(inputStream);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().stripLeading();

            // Get the line that potentially has the free to keep promotion details on it
            if (!line.contains("Free to keep")) continue;

            Matcher matcher = STEAM_MONTH_DAY_TIME_REGEX.matcher(line);

            if (!matcher.find()) {
                // It is possible for there to be no end date provided for some reason
                if (line.contains("limited-time promotion")) return GameFinderConstants.NO_EXPIRATION_EPOCH;
                continue;
            }

            // Pull out regex match and make am/pm uppercase so date parser will parse it
            String expirationDate = matcher.group(0)
                    .replace("am", "AM")
                    .replace("pm", "PM");

            // Parse expirationDate using GameFinderConstants.STEAM_DATE_FORMAT to get epochSecond
            return LocalDateTime.parse(expirationDate, STEAM_DATE_FORMAT)
                    // By default, Steam will give a date in PST/PDT
                    .atZone(ZoneId.of("America/Los_Angeles"))
                    .toInstant()
                    .getEpochSecond();
        }

        scanner.close();
        return GameFinderConstants.NO_EXPIRATION_EPOCH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLocaleCookie() {
        /*
        timezoneOffset=(UTC offset in seconds),0
        i.e. America/New_York would be timezoneOffset=-14400,0

        I choose not to use this because Valve defaults to PST/PDT,
        and the timezone only has to be known to process it.

        Setting this cookie would add unnecessary complexity
         */
        return null;
    }
}
