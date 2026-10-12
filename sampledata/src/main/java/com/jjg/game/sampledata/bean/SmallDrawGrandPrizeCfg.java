package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName SmallDrawGrandPrize.xlsx
 * @sheetName SmallDrawGrandPrize
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class SmallDrawGrandPrizeCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "SmallDrawGrandPrize.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "SmallDrawGrandPrize";

  /** 道具奖励 */
  protected Map<Integer,Long> ItemReward;
  /** 展示排序 */
  protected int SlotIndex;
  /** 活动ID */
  protected int SmallDrawId;

  /** 返回道具奖励 */
  public Map<Integer,Long> getItemReward() {
    return ItemReward;
  }

  /** 返回展示排序 */
  public int getSlotIndex() {
    return SlotIndex;
  }

  /** 返回活动ID */
  public int getSmallDrawId() {
    return SmallDrawId;
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
