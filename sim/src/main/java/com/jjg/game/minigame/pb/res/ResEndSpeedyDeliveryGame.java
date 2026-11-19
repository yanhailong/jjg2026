package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_END_SPEEDY_DELIVERY_GAME, resp = true)
@ProtoDesc("飞驰快送结算结果；超时自动结算也推送此协议")
public class ResEndSpeedyDeliveryGame extends AbstractResponse {
    @ProtoDesc("服务器结算积分")
    public int score;
    @ProtoDesc("结算奖励；发奖失败可重试结束协议")
    public List<ItemInfo> rewards;
    @ProtoDesc("结算后的可用技能积分")
    public long skillPoints;
    @ProtoDesc("额外获得， itemId是Minigamegoods.id")
    public List<ItemInfo> extraRewards;

    public ResEndSpeedyDeliveryGame(int code) {
        super(code);
    }
}
