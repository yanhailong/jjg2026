package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

@ProtobufMessage
@ProtoDesc("温馨酒馆货架格子库存")
public class SpeedyDeliveryShelfGoods {
    @ProtoDesc("货架格子ID")
    public int slotId;
    @ProtoDesc("Minigamegoods配置ID")
    public int goodsId;
    @ProtoDesc("剩余数量")
    public int count;
}
