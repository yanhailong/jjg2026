package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW, resp = true)
@ProtoDesc("主动抽奖返回；code=200成功，404当前赛季功能未开启，408没有已付款机会，201无剩余正权重奖励；发奖失败返回对应状态码")
public class ResSeasonSmallDraw extends AbstractResponse {
    @ProtoDesc("本次抽中的奖池格子ID，对应SmallDrawReward.id；仅code=200时有效")
    public int rewardId;
    @ProtoDesc("本次实际发放的奖励道具及数量；核心大奖取请求抽奖时玩家持久化选择的候选，未选择则取itemid[0]；仅code=200时返回")
    public List<ItemInfo> rewards;
    @ProtoDesc("抽奖成功后的最新状态；中奖格子已标记获得，drawChances已消耗一次，购买资格不再次扣减；仅code=200时返回")
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDraw(int code) { super(code); }
}
