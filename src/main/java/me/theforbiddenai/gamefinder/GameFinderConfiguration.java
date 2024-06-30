package me.theforbiddenai.gamefinder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.exception.LocaleException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

/**
 * Singleton class that contains the configuration settings for GameFinder
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GameFinderConfiguration {

    private static GameFinderConfiguration instance;

    private List<Platform> enabledPlatforms = new ArrayList<>();

    // Whether to emit free game DLCs
    @Accessors(fluent = true)
    private boolean includeDLCs = true;

    @Accessors(fluent = true)
    private boolean allowSteamMatureContentScreenshots = true;

    @Accessors(fluent = true)
    private boolean useGOGLocaleCookie = false;

    // By default, English will return if a game developer has not translated their description
    private Locale locale = Locale.US;

    // Controls what executorService is used to execute the CompletableFutures
    private ExecutorService executorService = ForkJoinPool.commonPool();

    @Getter(AccessLevel.PRIVATE)
    private final List<Locale> validLocales = Arrays.stream(Locale.getAvailableLocales())
            .filter(availableLocale -> {
                String[] localeParams = availableLocale.toString().split("_");
                // Validate the locale only has a country code and a language code that are both 2 characters long
                return localeParams.length == 2
                        && localeParams[0].length() == 2
                        && localeParams[1].length() == 2;
            }).toList();

    public static synchronized GameFinderConfiguration getInstance() {
        if (instance == null) instance = new GameFinderConfiguration();
        return instance;
    }

    /**
     * @throws LocaleException If the provided locale does not have both a language code and a country code
     */
    public void setLocale(Locale locale) throws LocaleException {
        if(!validLocales.contains(locale)) {
            throw new LocaleException("A locale must be a valid combination of a two letter language code and a two letter country code!");
        }
        this.locale = locale;
    }

}
