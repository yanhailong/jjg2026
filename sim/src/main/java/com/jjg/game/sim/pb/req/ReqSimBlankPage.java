package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_SIM_BLANK_PAGE)
@ProtoDesc("上报客户端当前页面")
public class ReqSimBlankPage extends AbstractMessage {
    @ProtoDesc("客户端页面编号  0.空白页  99999.未知页面")
    public int blankPage;
}
