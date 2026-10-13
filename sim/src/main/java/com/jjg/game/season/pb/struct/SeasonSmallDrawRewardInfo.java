package com.jjg.game.season.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("小额抽奖奖池格子；一条SmallDrawReward配置对应一个格子")
public class SeasonSmallDrawRewardInfo {
    @ProtoDesc("奖池格子ID，对应SmallDrawReward.id；用于标识抽中结果及选择指定核心大奖")
    public int rewardId;
    @ProtoDesc("是否核心大奖，对应SmallDrawReward.open；true可选择候选奖励，false为普通固定奖励")
    public boolean core;
    @ProtoDesc("该格子是否已抽中；true时不再参与后续抽奖，核心大奖的候选选择仍可更换")
    public boolean obtained;
    @ProtoDesc("该格子当前展示的奖励道具及数量；核心大奖按当前选中候选返回，普通奖励按固定配置返回；更换候选不会重新发放已获得的奖励")
    public List<ItemInfo> items;
}
