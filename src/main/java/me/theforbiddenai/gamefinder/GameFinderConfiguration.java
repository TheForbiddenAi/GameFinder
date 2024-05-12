package me.theforbiddenai.gamefinder;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import me.theforbiddenai.gamefinder.domain.Platform;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GameFinderConfiguration {

    private static GameFinderConfiguration INSTANCE;

    private List<Platform> enabledPlatforms = new ArrayList<>();

    // Whether to emit free game DLCs
    @Accessors(fluent = true)
    private boolean includeDLCs = true;

    public static GameFinderConfiguration getInstance() {
        if (INSTANCE == null) INSTANCE = new GameFinderConfiguration();
        return INSTANCE;
    }

}
