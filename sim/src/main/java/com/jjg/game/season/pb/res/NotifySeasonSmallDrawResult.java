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
@ProtoDesc("充值到账并完成抽奖发奖后主动通知，code=200表示成功；客户端无需单独发起抽奖请求")
public class NotifySeasonSmallDrawResult extends AbstractResponse {
    @ProtoDesc("本次充值订单ID，对应服务器Order.id，用于关联付款与抽奖结果")
    public String orderId;
    @ProtoDesc("本次抽中的奖池格子ID，对应SmallDrawReward.id")
    public int rewardId;
    @ProtoDesc("本次实际发放的奖励道具及数量；抽中核心大奖时使用玩家当前持久化选择的候选，未选择则使用itemid[0]")
    public List<ItemInfo> rewards;
    @ProtoDesc("抽奖完成后的最新功能状态；中奖格子已标记获得，购买资格已扣减一次")
    public SeasonSmallDrawInfo info;
    public NotifySeasonSmallDrawResult(int code) { super(code); }
}
