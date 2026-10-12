package com.jjg.game.season.pb.res;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractResponse;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;
import com.jjg.game.season.pb.struct.SeasonSmallDrawInfo;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.RES_SEASON_SMALL_DRAW_INFO, resp = true)
@ProtoDesc("当前赛季小额抽奖状态返回")
public class ResSeasonSmallDrawInfo extends AbstractResponse {
    public SeasonSmallDrawInfo info;
    public ResSeasonSmallDrawInfo(int code) { super(code); }
}
