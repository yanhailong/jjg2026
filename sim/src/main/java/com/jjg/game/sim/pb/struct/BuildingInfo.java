package com.jjg.game.sim.pb.struct;

import com.jjg.game.common.pb.ItemInfo;
import com.jjg.game.common.proto.ProtoDesc;
import com.jjg.game.common.proto.ProtobufMessage;
import com.jjg.game.core.pb.KVInfo;

import java.util.List;

/**
 * @author 11
 * @date 2026/5/27
 */
@ProtobufMessage
@ProtoDesc("建筑信息")
public class BuildingInfo {
    @ProtoDesc("建筑id")
    public int id;
    @ProtoDesc("等级")
    public int level;
    @ProtoDesc("交互次数  如果是运营部则为游客生成，其他建筑为交互次数")
    public int interactCount;
    @ProtoDesc("游客品质出现概率")
    public List<KVInfo> guestQualityList;
    @ProtoDesc("按产出类型区分的总值、固定值及比例加成来源")
    public List<BonusInfo> bonusInfos;
    @ProtoDesc("建筑基础属性  对应 BuildingUpgradeTable.xlsx 的upgradeOutput")
    public long upgradeOutput;
    @ProtoDesc("建筑基础属性  对应 BuildingUpgradeTable.xlsx 的upgradeExp")
    public long upgradeExp;
    @ProtoDesc("建筑属性分类  对应 BuildingAreaTable.xlsx 的typeValue")
    public List<Integer> buileTypeValue;
    @ProtoDesc("建筑各区域的皮肤解锁与使用状态")
    public List<BuildingAreaSkinInfo> areaSkinInfos;
    @ProtoDesc("目标可奖励的等级， 为0可视为已达到最大等级")
    public int targetLevel;
    @ProtoDesc("达到目标后的奖励")
    public List<ItemInfo> nextRewards;
    @ProtoDesc("升1级的条件")
    public BuildingUpgradeConditonInfo buildingUpgrade1ConditonInfo;
    @ProtoDesc("升10级的条件")
    public BuildingUpgradeConditonInfo buildingUpgrade10ConditonInfo;
}
