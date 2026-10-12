package com.jjg.game.season.config;

import java.util.List;

/** 后续 Excel 配置适配器实现此接口并注册为 Spring Bean；没有配置源时活动不开放。 */
public interface LuckyDrawConfigSource {
    List<LuckyDrawDefinition> definitions();
}
