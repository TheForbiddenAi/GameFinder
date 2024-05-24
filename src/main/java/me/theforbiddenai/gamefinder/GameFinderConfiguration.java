package me.theforbiddenai.gamefinder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.theforbiddenai.gamefinder.domain.Platform;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;

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

    private String locale = "en-US";

    private String countryCode = "US";

    // Controls what executorService is used to execute the CompletableFutures
    private ExecutorService executorService = ForkJoinPool.commonPool();

    public static GameFinderConfiguration getInstance() {
        if (INSTANCE == null) INSTANCE = new GameFinderConfiguration();
        return INSTANCE;
    }

}
