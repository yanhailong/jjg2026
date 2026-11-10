package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName BuildingSkin.xlsx
 * @sheetName BuildingSkin
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuildingSkinCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "BuildingSkin.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "BuildingSkin";

  /** 区域编号 */
  protected int areaId;
  /** 加成Buff */
  protected List<Integer> buffId;
  /** 跳转ID */
  protected int junmpId;
  /** 多语言ID */
  protected int languageId;
  /** 解锁所需道具(与"解锁条件"任选其一) */
  protected int needItemId;
  /** 区域皮肤名称 */
  protected String skinFieldName;
  /** 皮肤名称 */
  protected int skinLanguageId;
  /** 解锁条件 */
  protected List<Integer> unlockCondition;

  /** 返回区域编号 */
  public int getAreaId() {
    return areaId;
  }

  /** 返回加成Buff */
  public List<Integer> getBuffId() {
    return buffId;
  }

  /** 返回跳转ID */
  public int getJunmpId() {
    return junmpId;
  }

  /** 返回多语言ID */
  public int getLanguageId() {
    return languageId;
  }

  /** 返回解锁所需道具(与"解锁条件"任选其一) */
  public int getNeedItemId() {
    return needItemId;
  }

  /** 返回区域皮肤名称 */
  public String getSkinFieldName() {
    return skinFieldName;
  }

  /** 返回皮肤名称 */
  public int getSkinLanguageId() {
    return skinLanguageId;
  }

  /** 返回解锁条件 */
  public List<Integer> getUnlockCondition() {
    return unlockCondition;
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
