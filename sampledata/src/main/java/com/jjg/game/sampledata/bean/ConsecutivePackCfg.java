package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName ConsecutivePack.xlsx
 * @sheetName ConsecutivePack
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ConsecutivePackCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "ConsecutivePack.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "ConsecutivePack";

  /** 宝箱价格 */
  protected int ShopRechargeListID;
  /** 奖励道具列表 */
  protected Map<Integer,Long> getitem;
  /** 宝箱类型 */
  protected int type;

  /** 返回宝箱价格 */
  public int getShopRechargeListID() {
    return ShopRechargeListID;
  }

  /** 返回奖励道具列表 */
  public Map<Integer,Long> getGetitem() {
    return getitem;
  }

  /** 返回宝箱类型 */
  public int getType() {
    return type;
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
