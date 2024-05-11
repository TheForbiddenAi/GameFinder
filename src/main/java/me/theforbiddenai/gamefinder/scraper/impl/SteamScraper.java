package me.theforbiddenai.gamefinder.scraper.impl;

import me.theforbiddenai.gamefinder.domain.Game;
import me.theforbiddenai.gamefinder.scraper.Scraper;

import java.io.IOException;
import java.util.List;

public class SteamScraper implements Scraper {

    @Override
    public List<Game> retrieveGames() throws IOException {
        return List.of();
    }

}
