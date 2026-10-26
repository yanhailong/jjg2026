package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameHappyHappyFishCfg;

/**
 * MinigameHappyHappyFish.xlsx配置管理容器
 *
 * @excelName MinigameHappyHappyFish.xlsx
 * @sheetName MinigameHappyHappyFish
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameHappyHappyFishCfgContainer extends BaseCfgContainer<MinigameHappyHappyFishCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameHappyHappyFishCfgContainer getNewContainer(){
    return new MinigameHappyHappyFishCfgContainer();
  }

  public MinigameHappyHappyFishCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameHappyHappyFish.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameHappyHappyFishCfg createNewBean() {
    return new MinigameHappyHappyFishCfg();
  }
}
