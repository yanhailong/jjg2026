package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_CLAIM_BUILDING_GUEST_REWARDS, resp = true)
@ProtoDesc("领取建筑游客奖励返回")
public class ResClaimBuildingGuestRewards extends AbstractResponse {
    @ProtoDesc("建筑id，0表示领取当前场景所有建筑的游客奖励")
    public int buildingId;
    @ProtoDesc("本次实际领取的道具，领取全部时按道具合并，无待领奖励时为空")
    public List<ItemInfo> rewards;

    public ResClaimBuildingGuestRewards(int code) {
        super(code);
    }
}
