package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_UNLOCK_NEXT_LEVEL, resp = true)
@ProtoDesc("解锁找老千下一关结果")
public class ResUnlockNextLevel extends AbstractResponse {
    @ProtoDesc("当前已解锁关卡ID；解锁失败时保持原值")
    public int levelId;

    public ResUnlockNextLevel(int code) {
        super(code);
    }
}
