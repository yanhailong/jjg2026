package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_SIM_BLANK_PAGE, resp = true)
@ProtoDesc("上报客户端空白页面状态返回")
public class ResSimBlankPage extends AbstractResponse {
    @ProtoDesc("本次上报的客户端空白页面状态")
    public boolean blankPage;

    public ResSimBlankPage(int code) {
        super(code);
    }
}
