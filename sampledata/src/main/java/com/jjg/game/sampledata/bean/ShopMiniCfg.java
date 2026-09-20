package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName ShopMini.xlsx
 * @sheetName ShopMini
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ShopMiniCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "ShopMini.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "ShopMini";

  /** 商品 */
  protected Map<Integer,Long> Goods;
  /** 开放等级-总账号等级 */
  protected int PurchaseLevelMax;
  /** 开放等级-总账号等级 */
  protected int PurchaseLevelMin;
  /** 购买价格-类型1-直接支付 */
  protected int ShopRechargeListId;
  /** 购买价格-类型2-货币购买 */
  protected Map<Integer,Long> cost;
  /** 缺少此道具ID时触发 */
  protected int needItemId;

  /** 返回商品 */
  public Map<Integer,Long> getGoods() {
    return Goods;
  }

  /** 返回开放等级-总账号等级 */
  public int getPurchaseLevelMax() {
    return PurchaseLevelMax;
  }

  /** 返回开放等级-总账号等级 */
  public int getPurchaseLevelMin() {
    return PurchaseLevelMin;
  }

  /** 返回购买价格-类型1-直接支付 */
  public int getShopRechargeListId() {
    return ShopRechargeListId;
  }

  /** 返回购买价格-类型2-货币购买 */
  public Map<Integer,Long> getCost() {
    return cost;
  }

  /** 返回缺少此道具ID时触发 */
  public int getNeedItemId() {
    return needItemId;
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
