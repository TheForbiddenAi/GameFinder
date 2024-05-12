package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class Game {

    private String title;
    private String description;
    private String url;
    private boolean isDLC;

    @Builder.Default
    private Platform platform = Platform.UNDEFINED;

    // storeImages are comprised of thumbnails, header images, and capsule images
    @Builder.Default
    private Map<String, String> storeMedia = new HashMap<>();

    // media includes screenshots and trailers
    @Builder.Default
    private List<String> media = new ArrayList<>();

    @Builder.Default
    private Long expirationEpoch = GameFinderConstants.NO_EXPIRATION_EPOCH;

    // This is true only when a steam clan event's end epoch is used as the end epoch for a game
    @Builder.Default
    private boolean isExpirationEpochEstimate = false;

}
