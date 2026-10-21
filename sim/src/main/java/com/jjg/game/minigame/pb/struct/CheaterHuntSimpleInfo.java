package com.jjg.game.minigame.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("找老千关卡简要信息")
public class CheaterHuntSimpleInfo {
    @ProtoDesc("当前进度关卡ID，对应CheaterHuntLevel.id")
    public int levelId;
    @ProtoDesc("关卡名称多语言id")
    public int levelLangId;
    @ProtoDesc("是否解锁")
    public boolean unlock;
    @ProtoDesc("消耗道具")
    public List<ItemInfo> costItems;
    @ProtoDesc("解锁道具")
    public List<ItemInfo> unlockItems;
}
