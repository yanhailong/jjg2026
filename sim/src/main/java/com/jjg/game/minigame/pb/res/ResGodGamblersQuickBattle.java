package com.jjg.game.minigame.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.minigame.constant.MiniGameConstant;
import com.jjg.game.minigame.pb.struct.GodGamblersInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_MINI_GAME, cmd = MiniGameConstant.MsgBean.RES_GOD_GAMBLERS_QUICK_BATTLE, resp = true)
@ProtoDesc("快速比拼结果")
public class ResGodGamblersQuickBattle extends AbstractResponse {
    @ProtoDesc("本局双方牌及结算状态；待发奖时携局号请求确定比拼重试")
    public GodGamblersInfo info;
    @ProtoDesc("本次成功发放的奖励")
    public List<ItemInfo> rewards;
    @ProtoDesc("各消耗道具当前背包持有数量")
    public List<ItemInfo> ownedItems;

    public ResGodGamblersQuickBattle(int code) {
        super(code);
    }
}
