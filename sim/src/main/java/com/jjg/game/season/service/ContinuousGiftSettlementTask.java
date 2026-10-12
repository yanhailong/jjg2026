package com.jjg.game.season.service;

import com.jjg.game.common.timer.TimerCenter;
import com.jjg.game.common.timer.TimerEvent;
import com.jjg.game.common.timer.TimerListener;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** 每分钟扫描已过期记录，启动后同样重试；玩家锁及唯一邮件键保证多节点不会重复补发。 */
@Component
public class ContinuousGiftSettlementTask implements TimerListener<Void> {
    private final TimerCenter timers;
    private final ContinuousGiftService gifts;
    public ContinuousGiftSettlementTask(TimerCenter timers, ContinuousGiftService gifts) {
        this.timers = timers; this.gifts = gifts;
    }
    @PostConstruct public void start() { timers.add(new TimerEvent<>(this, null, 60_000, false)); }
    @Override public void onTimer(TimerEvent<Void> event) { gifts.settleExpired(System.currentTimeMillis()); }
}
