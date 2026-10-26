package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName BuildingUpgradeTable.xlsx
 * @sheetName BuildingUpgradeTable
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuildingUpgradeTableCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "BuildingUpgradeTable.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "BuildingUpgradeTable";

  /** 建筑ID */
  protected int BuildingID;
  /** 升级消耗材料(最多1个) */
  protected Map<Integer,Long> CostPerLevel;
  /** 曝光度产出(起始值,增量值) */
  protected List<Long> Exposure;
  /** 交互次数（起始值,增量值） */
  protected List<Integer> InteractCount;
  /** 电子显示屏中的数量 */
  protected int MaxInteractionCount;
  /** 建筑区域等待点位 */
  protected List<Integer> MaxWaiting;
  /** 经营等级 */
  protected int NeedLevel;
  /** 相对-升级物品默认中心的偏移量x、y */
  protected List<Integer> Offset;
  /** 知名度产出(起始值,增量值) */
  protected List<Long> Popularity;
  /** 基础繁荣度(起始值,增量值) */
  protected List<Integer> Prosperity;
  /** 服务能力产出(起始值,增量值) */
  protected List<Long> Service;
  /** 解锁\升级装饰 */
  protected List<Integer> UnlockDecorationId;
  /** 解锁\升级设备 */
  protected List<Integer> UnlockEquipment;
  /** 交互点ID */
  protected List<Integer> UnlockInteractionId;
  /** 金币产出(起始值,增量值) */
  protected List<Long> UpgradeOutput;
  /** 等级段升级奖励(最多2个) */
  protected Map<Integer,Long> UpgradeReward;
  /** 建筑等级段(起始值 >=) */
  protected int level;

  /** 返回建筑ID */
  public int getBuildingID() {
    return BuildingID;
  }

  /** 返回升级消耗材料(最多1个) */
  public Map<Integer,Long> getCostPerLevel() {
    return CostPerLevel;
  }

  /** 返回曝光度产出(起始值,增量值) */
  public List<Long> getExposure() {
    return Exposure;
  }

  /** 返回交互次数（起始值,增量值） */
  public List<Integer> getInteractCount() {
    return InteractCount;
  }

  /** 返回电子显示屏中的数量 */
  public int getMaxInteractionCount() {
    return MaxInteractionCount;
  }

  /** 返回建筑区域等待点位 */
  public List<Integer> getMaxWaiting() {
    return MaxWaiting;
  }

  /** 返回经营等级 */
  public int getNeedLevel() {
    return NeedLevel;
  }

  /** 返回相对-升级物品默认中心的偏移量x、y */
  public List<Integer> getOffset() {
    return Offset;
  }

  /** 返回知名度产出(起始值,增量值) */
  public List<Long> getPopularity() {
    return Popularity;
  }

  /** 返回基础繁荣度(起始值,增量值) */
  public List<Integer> getProsperity() {
    return Prosperity;
  }

  /** 返回服务能力产出(起始值,增量值) */
  public List<Long> getService() {
    return Service;
  }

  /** 返回解锁\升级装饰 */
  public List<Integer> getUnlockDecorationId() {
    return UnlockDecorationId;
  }

  /** 返回解锁\升级设备 */
  public List<Integer> getUnlockEquipment() {
    return UnlockEquipment;
  }

  /** 返回交互点ID */
  public List<Integer> getUnlockInteractionId() {
    return UnlockInteractionId;
  }

  /** 返回金币产出(起始值,增量值) */
  public List<Long> getUpgradeOutput() {
    return UpgradeOutput;
  }

  /** 返回等级段升级奖励(最多2个) */
  public Map<Integer,Long> getUpgradeReward() {
    return UpgradeReward;
  }

  /** 返回建筑等级段(起始值 >=) */
  public int getLevel() {
    return level;
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode();
  }
}
