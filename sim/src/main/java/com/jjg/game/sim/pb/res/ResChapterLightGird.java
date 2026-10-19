package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_CHAPTER_LIGHT_GIRD, resp = true)
@ProtoDesc("关卡游戏点亮格子返回")
public class ResChapterLightGird extends AbstractResponse {
    public ResChapterLightGird(int code) {
        super(code);
    }
}
