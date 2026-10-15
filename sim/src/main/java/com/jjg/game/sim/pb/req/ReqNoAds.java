package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_NO_ADS_INFO)
@ProtoDesc("获取当前玩家的免广告礼包信息；购买走ReqGenerateOrder，rechargeType=22，无需productId")
public class ReqNoAds extends AbstractMessage {
}
