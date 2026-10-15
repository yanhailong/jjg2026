package com.jjg.game.core.listener;

import com.jjg.game.core.data.Player;

public interface SimPlayerContextListener {
    int allLevel(Player player);

    default boolean isFunctionUnlocked(long playerId, int functionId) {
        return false;
    }
}
