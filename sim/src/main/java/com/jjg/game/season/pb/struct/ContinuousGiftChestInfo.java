package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import java.util.List;

@ProtobufMessage
public class ContinuousGiftChestInfo {
    public int chestId;
    public int dayIndex;
    public String dayLabel;
    public long price;
    @ProtoDesc("1人民币（分） 2钻石 3金币") public int priceType;
    public String channelProductId;
    public String chestIcon;
    public String purchasedIcon;
    public List<ItemInfo> rewards;
    @ProtoDesc("0前序未购买 1可购买 2已购买 3等待次日 4支付处理中 5消费结果待核对") public int status;
}
