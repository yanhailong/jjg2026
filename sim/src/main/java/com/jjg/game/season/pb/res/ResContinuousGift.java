package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.ContinuousGiftInfo;
import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_CONTINUOUS_GIFT, resp = true)
public class ResContinuousGift extends AbstractResponse {
    /** 1列表/进入 2购买 3大奖领取 */
    public int action;
    public List<ContinuousGiftInfo> activities = List.of();
    public List<ItemInfo> rewards = List.of();
    /** 已支付成功但背包交付尚未完成，到期补发或等待对账。 */
    public boolean rewardPending;
    public ResContinuousGift(int code) { super(code); }
}
