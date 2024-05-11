package me.theforbiddenai.gamefinder.scraper;

import me.theforbiddenai.gamefinder.domain.Game;

import java.io.IOException;
import java.util.List;

public interface Scraper {

    List<Game> retrieveGames() throws IOException;

}
