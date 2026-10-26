package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName BuildingUpgradeArea.xlsx
 * @sheetName BuildingUpgradeArea
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuildingUpgradeAreaCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "BuildingUpgradeArea.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "BuildingUpgradeArea";

  /** 相对-区域 默认中心的偏移量x、y */
  protected List<Integer> Offset;
  /** 解锁区域展示的图标 */
  protected String areaIcon;
  /** 区域编号 */
  protected int areaId;
  /** 建筑ID */
  protected int buildingId;
  /** 娱乐城ID */
  protected int casinId;
  /** 区域名称 */
  protected int languageId;
  /** 皮肤ID */
  protected List<Integer> skinId;
  /** 需要解锁的设备 */
  protected List<Integer> unlockEquipment;

  /** 返回相对-区域 默认中心的偏移量x、y */
  public List<Integer> getOffset() {
    return Offset;
  }

  /** 返回解锁区域展示的图标 */
  public String getAreaIcon() {
    return areaIcon;
  }

  /** 返回区域编号 */
  public int getAreaId() {
    return areaId;
  }

  /** 返回建筑ID */
  public int getBuildingId() {
    return buildingId;
  }

  /** 返回娱乐城ID */
  public int getCasinId() {
    return casinId;
  }

  /** 返回区域名称 */
  public int getLanguageId() {
    return languageId;
  }

  /** 返回皮肤ID */
  public List<Integer> getSkinId() {
    return skinId;
  }

  /** 返回需要解锁的设备 */
  public List<Integer> getUnlockEquipment() {
    return unlockEquipment;
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
