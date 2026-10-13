package com.jjg.game.season.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_SEASON_SMALL_DRAW_DAILY_REWARD)
@ProtoDesc("领取小额抽奖免费每日福利；功能开启且当天未领取即可，无需购买或抽奖，每个服务器自然日最多领取一次")
public class ReqSeasonSmallDrawDailyReward extends AbstractMessage {
    @ProtoDesc("当前已开启的小额抽奖配置ID，对应SmallDraw.id，取状态返回中的drawId")
    public int drawId;
}
