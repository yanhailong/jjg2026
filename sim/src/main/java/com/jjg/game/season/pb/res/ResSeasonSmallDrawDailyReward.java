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
@ProtoDesc("小额抽奖每日福利返回")
public class ResSeasonSmallDrawDailyReward extends AbstractResponse {
    public List<ItemInfo> rewards;
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawDailyReward(int code) { super(code); }
}
