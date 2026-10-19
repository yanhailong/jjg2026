package com.jjg.game.sampledata.bean;

import java.util.*;


import javax.annotation.processing.Generated;
/**
 * 配置bean
 *
 * @excelName chapter.xlsx
 * @sheetName chapter
 * @author Auto.Generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ChapterCfg extends BaseCfgBean {

  /** 配置表名 */
  public static final String EXCEL_NAME = "chapter.xlsx";
  /** 配置表工作薄名 */
  public static final String SHEET_NAME = "chapter";

  /** 章节名称名称多语言ID */
  protected int chapterNameId;
  /** 关卡ID */
  protected List<Integer> levelId;

  /** 返回章节名称名称多语言ID */
  public int getChapterNameId() {
    return chapterNameId;
  }

  /** 返回关卡ID */
  public List<Integer> getLevelId() {
    return levelId;
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
