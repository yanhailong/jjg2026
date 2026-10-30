package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;

import java.util.List;

@ProtobufMessage
@ProtoDesc("建筑区域皮肤状态")
public class BuildingAreaSkinInfo {
    @ProtoDesc("区域编号，对应 BuildingUpgradeArea.areaId")
    public int areaId;
    @ProtoDesc("区域多语言id")
    public int areaLanguageId;
    @ProtoDesc("区域是否已解锁")
    public boolean unlocked;
    @ProtoDesc("当前使用皮肤 id，0 表示未使用")
    public int usingSkinId;
    @ProtoDesc("所有的皮肤信息")
    public List<SkinOriginInfo> skinOriginInfos;
}
