package com.jjg.game.sim.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;
import com.jjg.game.sim.pb.struct.BuildingInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.RES_USE_BUILDING_SKIN, resp = true)
@ProtoDesc("使用建筑区域皮肤返回")
public class ResUseBuildingSkin extends AbstractResponse {
    @ProtoDesc("建筑信息，包含最新皮肤状态及 Buff")
    public BuildingInfo buildingInfo;

    public ResUseBuildingSkin(int code) {
        super(code);
    }
}
