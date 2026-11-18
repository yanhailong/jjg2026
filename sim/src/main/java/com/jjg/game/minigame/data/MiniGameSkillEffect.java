package com.jjg.game.minigame.data;

import com.jjg.game.core.constant.GameConstant;

public final class MiniGameSkillEffect {
    private MiniGameSkillEffect() {
    }

    public static long scaleByPermille(long base, long permille) {
        return base * (permille / GameConstant.TEN_THOUSAND)
                + base * (permille % GameConstant.TEN_THOUSAND) / GameConstant.TEN_THOUSAND;
    }
}
