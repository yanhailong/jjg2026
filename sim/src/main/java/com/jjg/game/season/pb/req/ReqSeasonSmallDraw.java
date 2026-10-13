package com.jjg.game.season.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_SEASON_SMALL_DRAW)
@ProtoDesc("主动进行一次小额抽奖；无请求参数，当前赛季及奖池由服务器确定，需drawChances大于0；成功消耗一次已付款机会，按剩余奖池权重发奖，发奖失败不消耗机会")
public class ReqSeasonSmallDraw extends AbstractMessage {
}
