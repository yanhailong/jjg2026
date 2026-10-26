package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.BuildingSkinCfg;

/**
 * BuildingSkin.xlsx配置管理容器
 *
 * @excelName BuildingSkin.xlsx
 * @sheetName BuildingSkin
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class BuildingSkinCfgContainer extends BaseCfgContainer<BuildingSkinCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public BuildingSkinCfgContainer getNewContainer(){
    return new BuildingSkinCfgContainer();
  }

  public BuildingSkinCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("BuildingSkin.xlsx");
    return excelNameList;
  }

  @Override
  protected BuildingSkinCfg createNewBean() {
    return new BuildingSkinCfg();
  }
}
