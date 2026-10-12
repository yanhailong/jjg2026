package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.NOTIFY_SEASON_SMALL_DRAW_RESULT, resp = true)
@ProtoDesc("充值成功后的小额抽奖结果")
public class NotifySeasonSmallDrawResult extends AbstractResponse {
    public String orderId;
    public int rewardId;
    public List<ItemInfo> rewards;
    public SeasonSmallDrawInfo info;
    public NotifySeasonSmallDrawResult(int code) { super(code); }
}
