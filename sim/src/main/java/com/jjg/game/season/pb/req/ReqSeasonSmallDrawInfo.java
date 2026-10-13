package com.jjg.game.season.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_SEASON_SMALL_DRAW_INFO)
@ProtoDesc("查询当前赛季已开启的小额抽奖功能；无请求参数，当前赛季由服务器确定")
public class ReqSeasonSmallDrawInfo extends AbstractMessage {
}
