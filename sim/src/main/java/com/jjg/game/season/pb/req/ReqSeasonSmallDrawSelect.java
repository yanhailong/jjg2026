package com.jjg.game.season.pb.req;

import com.jjg.game.common.constant.MessageConst;
import com.jjg.game.common.pb.AbstractMessage;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.season.constant.SeasonConstant;

@ProtobufMessage(messageType = MessageConst.MessageTypeDef.SEASON, cmd = SeasonConstant.MsgBean.REQ_SEASON_SMALL_DRAW_SELECT)
@ProtoDesc("选择并持久化核心大奖候选；不消耗资源，核心大奖抽中后仍可更换")
public class ReqSeasonSmallDrawSelect extends AbstractMessage {
    @ProtoDesc("当前已开启的小额抽奖配置ID，对应SmallDraw.id，取状态返回中的drawId")
    public int drawId;
    @ProtoDesc("要更换的核心大奖格子ID，对应SmallDrawReward.id；必须属于当前奖池且open=true")
    public int rewardId;
    @ProtoDesc("要选择的核心大奖候选下标，对应SeasonSmallDrawInfo.coreItems；从0开始，范围为0至coreItems.size()-1")
    public int optionIndex;
}
