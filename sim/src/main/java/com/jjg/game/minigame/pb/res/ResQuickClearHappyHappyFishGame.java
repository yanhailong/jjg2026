package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_QUICK_CLEAR_HAPPY_HAPPY_FISH_GAME, resp = true)
@ProtoDesc("一键通关欢乐捉鱼结果")
public class ResQuickClearHappyHappyFishGame extends AbstractResponse {
    @ProtoDesc("global 360配置的结算积分")
    public int score;
    @ProtoDesc("本次成功发放的奖励；发奖失败可重试本协议，不重复扣费")
    public List<ItemInfo> rewards;
    @ProtoDesc("结算后的可用技能积分")
    public long skillPoints;

    public ResQuickClearHappyHappyFishGame(int code) {
        super(code);
    }
}
