package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName GoldTreasure.xlsx
 * @sheetName GoldTreasure
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class GoldTreasureCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "GoldTreasure.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "GoldTreasure";

  /** 渠道商品ID */
  protected int PayId;
  /** 奖励 */
  protected Map<Integer,Long> getItem;
  /** 奖励类型 */
  protected int rewardType;
  /** 超值 */
  protected int value;

  /** 返回渠道商品ID */
  public int getPayId() {
    return PayId;
  }

  /** 返回奖励 */
  public Map<Integer,Long> getGetItem() {
    return getItem;
  }

  /** 返回奖励类型 */
  public int getRewardType() {
    return rewardType;
  }

  /** 返回超值 */
  public int getValue() {
    return value;
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
