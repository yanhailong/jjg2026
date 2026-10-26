package com.jjg.game.sim.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.sim.constant.SimConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SIM_GAME, cmd = SimConstant.MsgBean.REQ_USE_BUILDING_SKIN)
@ProtoDesc("使用建筑区域皮肤")
public class ReqUseBuildingSkin extends AbstractMessage {
    @ProtoDesc("建筑 id")
    public int buildingId;
    @ProtoDesc("区域编号，对应 BuildingUpgradeArea.areaId")
    public int areaId;
    @ProtoDesc("BuildingSkin 配置 id")
    public int skinId;
}
