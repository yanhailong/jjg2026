package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntSimpleInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_ENTER_CHEAT_HUNT_GAME, resp = true)
@ProtoDesc("进入找老千结果")
public class ResEnterCheaterHuntGame extends AbstractResponse {
    @ProtoDesc("是否可以一键通关")
    public boolean openOneClickPass;
    @ProtoDesc("关卡列表信息")
    public List<CheaterHuntSimpleInfo> levelSimpleInfoList;
    @ProtoDesc("总共关卡数")
    public int allLevels;
    @ProtoDesc("已通关关卡数")
    public int passLevels;

    public ResEnterCheaterHuntGame(int code) {
        super(code);
    }
}
