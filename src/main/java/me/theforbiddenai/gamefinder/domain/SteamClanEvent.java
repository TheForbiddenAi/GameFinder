package me.theforbiddenai.gamefinder.domain;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class SteamClanEvent {
    private String name;
    private long endEpoch;
    private Set<String> includedApps; //game/dlc
    private Set<String> includedPackages; //sub
    private Set<String> includedBundles; //bundle
}
