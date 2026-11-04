package com.jjg.game.minigame.service;

import com.jjg.game.core.constant.AddType;
import com.jjg.game.core.constant.Code;
import com.jjg.game.core.data.CommonResult;
import com.jjg.game.core.data.ItemOperationResult;
import com.jjg.game.core.service.PlayerPackService;
import com.jjg.game.core.utils.ItemUtils;
import com.jjg.game.sim.data.SimPlayerContext;

import java.util.HashMap;
import java.util.Map;

/** 小游戏每日道具补足；日期去重与保存由各玩法负责，在玩家串行队列中调用。 */
final class MiniGameDailyItems {
    private MiniGameDailyItems() {
    }

    static CommonResult<ItemOperationResult> replenish(SimPlayerContext ctx, Map<Integer, Long> targets,
                                                       AddType addType, PlayerPackService packService) {
        Map<Integer, Long> supplements = new HashMap<>();
        for (Map.Entry<Integer, Long> entry : targets.entrySet()) {
            int itemId = entry.getKey();
            long count;
            if (itemId == ItemUtils.getGoldItemId()) {
                count = ctx.getPlayer().getGold();
            } else if (itemId == ItemUtils.getDiamondItemId()) {
                count = ctx.getPlayer().getDiamond();
            } else if (itemId == ItemUtils.getShellItemId()) {
                count = ctx.getPlayer().getShell();
            } else {
                count = packService.getItemCount(ctx.playerId(), itemId);
            }
            if (count < entry.getValue()) {
                supplements.put(itemId, entry.getValue() - count);
            }
        }
        return supplements.isEmpty() ? new CommonResult<>(Code.SUCCESS)
                : packService.addItems(ctx.playerId(), supplements, addType, true);
    }
}
