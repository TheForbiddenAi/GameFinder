package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;
import me.theforbiddenai.gamefinder.constants.GameFinderConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds information about a free game listing
 *
 * @author TheForbiddenAi
 */
@Data
@Builder
public class Game {

    private String title;
    private String description;
    private String url;
    private boolean isDLC;
    private int price;

    @Builder.Default
    private Platform platform = Platform.UNDEFINED;

    // storeImages are comprised of thumbnails, header images, and capsule images
    @Builder.Default
    private Map<String, String> storeMedia = new HashMap<>();

    // media includes screenshots
    @Builder.Default
    private List<String> media = new ArrayList<>();

    @Builder.Default
    private Long expirationEpoch = GameFinderConstants.NO_EXPIRATION_EPOCH;

}
