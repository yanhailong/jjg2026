package com.jjg.game.sampledata.container;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.Generated;
import com.jjg.game.sampledata.bean.MinigameGodofGamblersRubbingCfg;

/**
 * MinigameGodofGamblersRubbing.xlsx配置管理容器
 *
 * @excelName MinigameGodofGamblersRubbing.xlsx
 * @sheetName MinigameGodofGamblersRubbing
 * @author auto_generator
 */
@Generated("com.eouna.configtool.generator.template.java.JavaTemplateGenerator")
public class MinigameGodofGamblersRubbingCfgContainer extends BaseCfgContainer<MinigameGodofGamblersRubbingCfg> {

  @Override
  public boolean hasRelatedTable() {
    return false;
  }

  @Override
  public boolean isParentConfigNode() {
    return false;
  }

  @Override
  public MinigameGodofGamblersRubbingCfgContainer getNewContainer(){
    return new MinigameGodofGamblersRubbingCfgContainer();
  }

  public MinigameGodofGamblersRubbingCfgContainer() {
    super();
  }

  @Override
  public List<String> getExcelNameList() {
    List<String> excelNameList = new ArrayList<>();
    excelNameList.add("MinigameGodofGamblersRubbing.xlsx");
    return excelNameList;
  }

  @Override
  protected MinigameGodofGamblersRubbingCfg createNewBean() {
    return new MinigameGodofGamblersRubbingCfg();
  }
}
