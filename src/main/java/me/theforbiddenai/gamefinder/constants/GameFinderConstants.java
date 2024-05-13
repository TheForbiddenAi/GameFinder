package me.theforbiddenai.gamefinder.constants;

import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.regex.Pattern;

public class GameFinderConstants {

    public static final long NO_EXPIRATION_EPOCH = -1L;
    public static final String STEAM_STORE_URL = "https://store.steampowered.com/";

    public static final Pattern STEAM_MONTH_DAY_TIME_REGEX = Pattern.compile("([a-zA-z]{3} \\d{1,2}) (@ \\d{1,2}:\\d{1,2}(pm)?(am)?)");

    public static final DateTimeFormatter STEAM_DATE_FORMAT = new DateTimeFormatterBuilder()
            .appendPattern("MMM dd @ h:mma")
            .parseDefaulting(ChronoField.YEAR, Year.now().getValue())
            .toFormatter();

}
