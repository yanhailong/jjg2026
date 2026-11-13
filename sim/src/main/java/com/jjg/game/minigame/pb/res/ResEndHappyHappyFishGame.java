package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_END_HAPPY_HAPPY_FISH_GAME, resp = true)
@ProtoDesc("结束欢乐捉鱼结果")
public class ResEndHappyHappyFishGame extends AbstractResponse {
    @ProtoDesc("服务器结算积分")
    public int score;
    @ProtoDesc("本次成功发放的奖励")
    public List<ItemInfo> rewards;
    @ProtoDesc("结算后的可用技能积分")
    public long skillPoints;

    public ResEndHappyHappyFishGame(int code) {
        super(code);
    }
}
