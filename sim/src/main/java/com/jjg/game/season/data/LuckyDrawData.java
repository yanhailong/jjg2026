package com.jjg.game.season.data;

import com.jjg.game.season.config.LuckyDrawDefinition;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.*;

@Document("seasonLuckyDraw")
public class LuckyDrawData {
    @Id public String id;
    public long playerId;
    public String seasonKey;
    public long startTime;
    public long endTime;
    public String zoneId;
    public LuckyDrawDefinition definition;
    public Set<Integer> obtainedRewards = new LinkedHashSet<>();
    public int selectedGrandId;
    public boolean grandObtained;
    public int availableDraws;
    public long lastLoginDay = Long.MIN_VALUE;
    public long signalRevision;
    public long seenRevision = -1;
    public Map<String, Receipt> receipts = new LinkedHashMap<>();
    public Reservation reservation;
    public boolean settled;

    public static class Reservation {
        public String token;
        public String requestId;
        public long createdAt;
        public String channelProductId;
    }
    public static class Receipt {
        /** DEBITING/DELIVERING 保留结果不确定的操作，不能盲目重试。 */
        public String status;
        public int rewardId;
        public int grandId;
        public Map<Integer, Long> items = new LinkedHashMap<>();
        public String orderId;
        public String token;
        public long createdAt;
    }
}
