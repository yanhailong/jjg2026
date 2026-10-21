package com.jjg.game.minigame.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.REQ_GOD_GAMBLERS_SELF_POKER)
@ProtoDesc("获取自己的牌，首次免费，后续按配置消耗道具")
public class ReqGodGamblersSelfPoker extends AbstractMessage {
    @ProtoDesc("本次单局标识")
    public long roundId;
    @ProtoDesc("客户端已获取自己牌次数，首次传0；须与服务器一致")
    public int rubCount;
}
