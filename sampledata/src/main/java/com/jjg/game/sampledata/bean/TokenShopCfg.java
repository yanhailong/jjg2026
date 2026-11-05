package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName TokenShop.xlsx
 * @sheetName TokenShop
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class TokenShopCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "TokenShop.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "TokenShop";

  /** 折扣比例 */
  protected int DiscountRate;
  /** 结束时间 */
  protected String EndTime;
  /** 商品 */
  protected Map<Integer,Long> Goods;
  /** 是否开启 */
  protected boolean IsEnabled;
  /** 序列 */
  protected int Order;
  /** 开放等级 */
  protected int PurchaseLevel;
  /** Rechargeid */
  protected int Rechargeid;
  /** 限购次数 */
  protected int ResetDaily;
  /** 商品重置时间/day */
  protected int ResetTime;
  /** 开始时间 */
  protected String StartTime;
  /** 商店名称多语言 */
  protected int Storename;
  /** 购买价格 */
  protected Map<Integer,Long> cost;
  /** 客服端资源 */
  protected String icon;
  /** 多语言id */
  protected int language;
  /** 商店类型 */
  protected int type;

  /** 返回折扣比例 */
  public int getDiscountRate() {
    return DiscountRate;
  }

  /** 返回结束时间 */
  public String getEndTime() {
    return EndTime;
  }

  /** 返回商品 */
  public Map<Integer,Long> getGoods() {
    return Goods;
  }

  /** 返回是否开启 */
  public boolean getIsEnabled() {
    return IsEnabled;
  }

  /** 返回序列 */
  public int getOrder() {
    return Order;
  }

  /** 返回开放等级 */
  public int getPurchaseLevel() {
    return PurchaseLevel;
  }

  /** 返回Rechargeid */
  public int getRechargeid() {
    return Rechargeid;
  }

  /** 返回限购次数 */
  public int getResetDaily() {
    return ResetDaily;
  }

  /** 返回商品重置时间/day */
  public int getResetTime() {
    return ResetTime;
  }

  /** 返回开始时间 */
  public String getStartTime() {
    return StartTime;
  }

  /** 返回商店名称多语言 */
  public int getStorename() {
    return Storename;
  }

  /** 返回购买价格 */
  public Map<Integer,Long> getCost() {
    return cost;
  }

  /** 返回客服端资源 */
  public String getIcon() {
    return icon;
  }

  /** 返回多语言id */
  public int getLanguage() {
    return language;
  }

  /** 返回商店类型 */
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
