package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("随机事件中的单个宝箱")
public class RandomEventBoxInfo {
    @ProtoDesc("宝箱是否已开启")
    public boolean opened;
    @ProtoDesc("打乱前展示的预览奖励，空列表表示预览为空箱")
    public List<ItemInfo> previewRewards;
    @ProtoDesc("此位置实际开出的奖励。opened为true且列表为空时表示开出空箱")
    public List<ItemInfo> actualRewards;
}
