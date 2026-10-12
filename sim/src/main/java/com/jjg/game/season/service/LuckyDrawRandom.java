package com.jjg.game.season.service;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class LuckyDrawRandom {
    private final SecureRandom random = new SecureRandom();
    /** 未配置概率权重，按剩余格子等概率抽取。 */
    public int nextIndex(int remaining) { return random.nextInt(remaining); }
}
