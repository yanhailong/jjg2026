package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName Buff.xlsx
 * @sheetName Buff
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuffCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "Buff.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "Buff";

  /** 条件参数值 */
  protected int buffParam;
  /** 多语言ID */
  protected int languageId;
  /** Buff类型 */
  protected int typeId;
  /** 加成类型 */
  protected int valueType;

  /** 返回条件参数值 */
  public int getBuffParam() {
    return buffParam;
  }

  /** 返回多语言ID */
  public int getLanguageId() {
    return languageId;
  }

  /** 返回Buff类型 */
  public int getTypeId() {
    return typeId;
  }

  /** 返回加成类型 */
  public int getValueType() {
    return valueType;
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
