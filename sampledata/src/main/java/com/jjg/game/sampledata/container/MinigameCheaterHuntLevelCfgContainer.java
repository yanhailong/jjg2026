package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameCheaterHuntLevelCfg;

/**
 * MinigameCheaterHuntLevel.xlsx配置管理容器
 *
 * @excelName MinigameCheaterHuntLevel.xlsx
 * @sheetName MinigameCheaterHuntLevel
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameCheaterHuntLevelCfgContainer extends BaseCfgContainer<MinigameCheaterHuntLevelCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameCheaterHuntLevelCfgContainer getNewContainer(){
    return new MinigameCheaterHuntLevelCfgContainer();
  }

  public MinigameCheaterHuntLevelCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameCheaterHuntLevel.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameCheaterHuntLevelCfg createNewBean() {
    return new MinigameCheaterHuntLevelCfg();
  }
}
