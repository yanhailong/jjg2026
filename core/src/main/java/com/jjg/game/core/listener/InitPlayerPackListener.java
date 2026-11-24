package com.jjg.game.core.listener;

import com.jjg.game.common.baselogic.IGameSysFuncInterface;
import com.jjg.game.core.data.PlayerPack;

public interface InitPlayerPackListener extends IGameSysFuncInterface {
    void initPack(PlayerPack playerPack);
}
