package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Game {

    private String name;
    private String url;
    private Platform platform;
    private String thumbnail;
    private Long expirationEpoch;

}
