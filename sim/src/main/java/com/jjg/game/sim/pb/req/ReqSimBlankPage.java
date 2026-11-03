package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_SIM_BLANK_PAGE)
@ProtoDesc("上报客户端空白页面状态")
public class ReqSimBlankPage extends AbstractMessage {
    @ProtoDesc("客户端当前是否处于空白页面，true是，false否")
    public boolean blankPage;
}
