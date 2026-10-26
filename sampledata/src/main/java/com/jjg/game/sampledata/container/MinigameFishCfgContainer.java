package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameFishCfg;

/**
 * MinigameFish.xlsx配置管理容器
 *
 * @excelName MinigameFish.xlsx
 * @sheetName MinigameFish
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameFishCfgContainer extends BaseCfgContainer<MinigameFishCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameFishCfgContainer getNewContainer(){
    return new MinigameFishCfgContainer();
  }

  public MinigameFishCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameFish.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameFishCfg createNewBean() {
    return new MinigameFishCfg();
  }
}
