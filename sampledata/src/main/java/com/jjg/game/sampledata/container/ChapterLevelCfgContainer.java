package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.ChapterLevelCfg;

/**
 * Chapterlevel.xlsx配置管理容器
 *
 * @excelName Chapterlevel.xlsx
 * @sheetName chapterLevel
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ChapterLevelCfgContainer extends BaseCfgContainer<ChapterLevelCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public ChapterLevelCfgContainer getNewContainer(){
    return new ChapterLevelCfgContainer();
  }

  public ChapterLevelCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("Chapterlevel.xlsx");
    return excelNameList;
  }

  @Override
  protected ChapterLevelCfg createNewBean() {
    return new ChapterLevelCfg();
  }
}
