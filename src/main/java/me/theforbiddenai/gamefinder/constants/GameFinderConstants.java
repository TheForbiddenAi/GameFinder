package me.theforbiddenai.gamefinder.constants;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Holds constants that are used throughout the project
 *
 * @author TheForbiddenAi
 */
public class GameFinderConstants {

    public static final long NO_EXPIRATION_EPOCH = -1L;

    public static final List<Locale> VALID_LOCALES = Arrays.stream(Locale.getAvailableLocales())
            .filter(locale -> {
                String[] localeParams = locale.toString().split("_");
                // Validate the locale only has a country code and a language code that are both 2 characters long
                return localeParams.length == 2
                        && localeParams[0].length() == 2
                        && localeParams[1].length() == 2;
            }).toList();
}
