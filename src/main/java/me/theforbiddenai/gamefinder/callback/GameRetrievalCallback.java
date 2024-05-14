package me.theforbiddenai.gamefinder.callback;

import me.theforbiddenai.gamefinder.domain.Game;

import java.util.List;

public interface GameRetrievalCallback {

    void retrieveGame(List<Game> games);

}
