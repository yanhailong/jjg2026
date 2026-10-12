package com.jjg.game.season.service;
import com.jjg.game.common.timer.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class LuckyDrawSettlementTask implements TimerListener<Void> {
    private final TimerCenter timers;
    private final LuckyDrawService service;
    public LuckyDrawSettlementTask(TimerCenter timers, LuckyDrawService service) { this.timers = timers; this.service = service; }
    @PostConstruct public void start() { timers.add(new TimerEvent<>(this, null, 60_000, false)); }
    @Override public void onTimer(TimerEvent<Void> event) { service.settleExpired(System.currentTimeMillis()); }
}
