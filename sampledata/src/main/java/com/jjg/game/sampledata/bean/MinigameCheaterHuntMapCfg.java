package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName MinigameCheaterHuntMap.xlsx
 * @sheetName MinigameCheaterHuntMap
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameCheaterHuntMapCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "MinigameCheaterHuntMap.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "MinigameCheaterHuntMap";

  /** 藏取总点位 */
  protected int TotalSpotCount;
  /** 美术资源 */
  protected String icon;

  /** 返回藏取总点位 */
  public int getTotalSpotCount() {
    return TotalSpotCount;
  }

  /** 返回美术资源 */
  public String getIcon() {
    return icon;
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
