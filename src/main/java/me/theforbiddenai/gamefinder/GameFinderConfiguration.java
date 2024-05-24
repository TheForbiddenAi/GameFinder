package me.theforbiddenai.gamefinder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.exception.LocaleException;

import java.util.ArrayList;
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

    private static GameFinderConfiguration INSTANCE;

    private List<Platform> enabledPlatforms = new ArrayList<>();

    // Whether to emit free game DLCs
    @Accessors(fluent = true)
    private boolean includeDLCs = true;

    // When true, this will web scrape discount's end time on steam if it is unable to be retrieved any other way
    @Accessors(fluent = true)
    private boolean webScrapeExpirationEpoch = true;

    @Accessors(fluent = true)
    private boolean allowSteamMatureContentScreenshots = true;

    // By default, English will return if a game developer has not translated their description
    private Locale locale = Locale.US;

    // Controls what executorService is used to execute the CompletableFutures
    private ExecutorService executorService = ForkJoinPool.commonPool();

    public static GameFinderConfiguration getInstance() {
        if (INSTANCE == null) INSTANCE = new GameFinderConfiguration();
        return INSTANCE;
    }

    /**
     * @throws LocaleException If the provided locale does not have both a language code and a country code
     */
    public void setLocale(Locale locale) throws LocaleException {
        if (!locale.toString().contains("_"))
            throw new LocaleException("A locale must have both a language code and country code!");
        this.locale = locale;
    }

}
