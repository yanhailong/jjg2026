package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName SmallDrawReward.xlsx
 * @sheetName SmallDrawReward
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawRewardCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "SmallDrawReward.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "SmallDrawReward";

  /** 格子序号 */
  protected int SlotIndex;
  /** 活动ID */
  protected int SmallDrawId;
  /** 道具奖励 */
  protected Map<Integer,Long> itemid;
  /** 是否核心大奖 */
  protected boolean open;

  /** 返回格子序号 */
  public int getSlotIndex() {
    return SlotIndex;
  }

  /** 返回活动ID */
  public int getSmallDrawId() {
    return SmallDrawId;
  }

  /** 返回道具奖励 */
  public Map<Integer,Long> getItemid() {
    return itemid;
  }

  /** 返回是否核心大奖 */
  public boolean getOpen() {
    return open;
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
