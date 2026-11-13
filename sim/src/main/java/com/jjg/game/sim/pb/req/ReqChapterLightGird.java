package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_CHAPTER_LIGHT_GIRD)
@ProtoDesc("关卡游戏点亮格子")
public class ReqChapterLightGird extends AbstractMessage {
    @ProtoDesc("关卡id")
    public int levelId;
    @ProtoDesc("格子下标")
    public int girdIndex;
    @ProtoDesc("是否完成拼图")
    public boolean finishPuzzle;
}
