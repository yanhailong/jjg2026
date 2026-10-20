package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.CheaterHuntInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_QUICK_CLEAR_CHEATER_HUNT_GAME, resp = true)
@ProtoDesc("找老千一键通关结果")
public class ResQuickClearCheaterHuntGame extends AbstractResponse {
    @ProtoDesc("处理后的进度及单局状态；一键通关局不生成场景和点位")
    public CheaterHuntInfo info;
    @ProtoDesc("本次成功发放的通关奖励；发奖失败时不返回")
    public List<ItemInfo> rewards;

    public ResQuickClearCheaterHuntGame(int code) {
        super(code);
    }
}
