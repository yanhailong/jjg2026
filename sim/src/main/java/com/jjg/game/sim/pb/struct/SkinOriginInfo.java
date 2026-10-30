package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("皮肤信息")
public class SkinOriginInfo {
    @ProtoDesc("id")
    public int id;
    @ProtoDesc("多语言id")
    public int languageId;
    @ProtoDesc("皮肤名称")
    public int skinLanguageId;
    @ProtoDesc("是否已经解锁")
    public boolean unlock;
    @ProtoDesc("解锁类型  1.免费解锁  2.材料解锁  3.建筑等级解锁 4.娱乐城等级解锁  5.活动解锁  6.任务解锁")
    public int unlockType;
    @ProtoDesc("解锁的材料  当unlockType=2时，才有该值")
    public List<ItemInfo> unlockTypeItems;
    @ProtoDesc("解锁值 配合unlockType来理解")
    public int unlockTypeValue;
    @ProtoDesc("道具解锁需要的道具, 取自BuildingSkin.needItemId")
    public List<ItemInfo> needItems;
}
