package com.partida.library;

import java.util.List;

/** A group of games shown in the sidebar: usually one system, or "Recently played". */
public record GameCollection(String name, List<Game> games) {

    @Override
    public String toString() {
        return name;
    }
}
