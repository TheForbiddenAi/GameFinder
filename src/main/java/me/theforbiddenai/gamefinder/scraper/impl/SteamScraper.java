package me.theforbiddenai.gamefinder.scraper.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.domain.Platform;
import me.theforbiddenai.gamefinder.scraper.Scraper;

import java.io.IOException;
import java.util.List;

public class SteamScraper extends Scraper {

    public SteamScraper(ObjectMapper objectMapper) {
        super(objectMapper, Platform.STEAM);
    }

    @Override
    public List<Game> retrieveGames() throws IOException {
        return List.of();
    }

}
