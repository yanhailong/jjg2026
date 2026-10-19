package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.ChapterCfg;

/**
 * chapter.xlsx配置管理容器
 *
 * @excelName chapter.xlsx
 * @sheetName chapter
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class ChapterCfgContainer extends BaseCfgContainer<ChapterCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public ChapterCfgContainer getNewContainer(){
    return new ChapterCfgContainer();
  }

  public ChapterCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("chapter.xlsx");
    return excelNameList;
  }

  @Override
  protected ChapterCfg createNewBean() {
    return new ChapterCfg();
  }
}
