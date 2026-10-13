package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

import java.util.List;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW_DAILY_REWARD, resp = true)
@ProtoDesc("每日福利领取返回；code=200成功，404功能未开启或drawId不匹配，406今日已领取，201每日奖励配置无效；发奖失败返回对应状态码")
public class ResSeasonSmallDrawDailyReward extends AbstractResponse {
    @ProtoDesc("本次成功领取的每日福利道具及数量，来自SmallDraw.DailyReward；仅code=200时返回")
    public List<ItemInfo> rewards;
    @ProtoDesc("领取成功后的最新功能状态，其中dailyRewardClaimed=true；仅code=200时返回")
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawDailyReward(int code) { super(code); }
}
