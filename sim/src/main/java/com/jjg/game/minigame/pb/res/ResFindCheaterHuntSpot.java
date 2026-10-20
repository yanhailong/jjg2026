package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_FIND_CHEATER_HUNT_SPOT, resp = true)
@ProtoDesc("寻找作弊点位结果")
public class ResFindCheaterHuntSpot extends AbstractResponse {
    @ProtoDesc("本次处理后的进度及单局状态")
    public CheaterHuntInfo info;
    @ProtoDesc("本次是否新命中作弊点位；待发奖重试时为false")
    public boolean hit;
    @ProtoDesc("本次成功发放的通关奖励；无奖励时不返回")
    public List<ItemInfo> rewards;

    public ResFindCheaterHuntSpot(int code) {
        super(code);
    }
}
