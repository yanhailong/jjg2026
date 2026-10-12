package com.jjg.game.season.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_SEASON_SMALL_DRAW_SELECT)
@ProtoDesc("选择核心大奖，抽中后仍可更换")
public class ReqSeasonSmallDrawSelect extends AbstractMessage {
    public int drawId;
    public int rewardId;
    @ProtoDesc("SmallDrawReward.itemid的元素索引，从0开始")
    public int optionIndex;
}
