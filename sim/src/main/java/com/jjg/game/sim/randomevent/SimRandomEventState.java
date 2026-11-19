package com.jjg.game.sim.randomevent;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document
class SimRandomEventState {
    @Id
    final long playerId;
    long day;
    long receiveDay;
    int refills;
    int received;
    List<Long> pendingDonors = new ArrayList<>();
    List<SceneEvent> events = new ArrayList<>();

    SimRandomEventState(long playerId) {
        this.playerId = playerId;
    }

    int receivedOn(long currentDay) {
        return receiveDay == currentDay ? received : 0;
    }

    boolean startDay(long currentDay) {
        if (day == currentDay) {
            return false;
        }
        day = currentDay;
        refills = 0;
        return true;
    }

    record SceneEvent(int configId, int position, long donorId,
                      int progress, int auxiliary, long readyAt, List<Integer> values) {
        @PersistenceCreator
        SceneEvent {
        }

        SceneEvent(int configId, int position, long donorId) {
            this(configId, position, donorId, 0, 0, 0, List.of());
        }

        SceneEvent advance(int progress, int auxiliary, long readyAt, List<Integer> values) {
            return new SceneEvent(configId, position, donorId, progress, auxiliary, readyAt, values);
        }
    }
}
