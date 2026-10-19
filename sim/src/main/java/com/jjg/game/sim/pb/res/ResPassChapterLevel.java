package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_PASS_CHAPTER_LEVEL, resp = true)
@ProtoDesc("通关关卡返回")
public class ResPassChapterLevel extends AbstractResponse {
    @ProtoDesc("通关奖励")
    public List<ItemInfo> rewards;

    public ResPassChapterLevel(int code) {
        super(code);
    }
}
