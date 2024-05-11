package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;

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

    @Builder.Default
    private Platform platform = Platform.UNDEFINED;

    // storeImages are comprised of thumbnails, header images, and capsule images
    @Builder.Default
    private Map<String, String> storeImages = new HashMap<>();

    // media includes screenshots and trailers
    @Builder.Default
    private List<String> media = new ArrayList<>();

    // This is -1 if it can't be found
    @Builder.Default
    private Long expirationEpoch = -1L;

}
