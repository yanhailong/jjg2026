package com.jjg.game.sim.service;

import com.jjg.game.sampledata.GameDataManager;
import com.jjg.game.sampledata.bean.EmployeeLevelCfg;
import com.jjg.game.sampledata.bean.EmployeeProfileCfg;
import com.jjg.game.sampledata.bean.EmployeeSkillConfigCfg;
import com.jjg.game.sampledata.bean.EmployeeStarCfg;
import com.jjg.game.sim.constant.BuildingOutputType;
import com.jjg.game.sim.data.SimAttributeBonus;
import com.jjg.game.sim.data.SimEmployeeData;
import com.jjg.game.sim.data.SimPlayerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按当前场景计算雇员属性及其技能加成，每次请求内共用一次快照。 */
@Service
public class SimEmployeeAttributeService {
    @Autowired
    private SimConfigCacheService configCache;

    public Snapshot snapshot(SimPlayerContext ctx) {
        Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> personal = new HashMap<>();
        Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> professions = new HashMap<>();
        Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> buildings = new HashMap<>();
        Map<Integer, Integer> employeeProfessions = new HashMap<>();
        Map<Integer, Map<Integer, EmployeeStarCfg>> starConfigs = configCache.getEmployeeStarCfgMap();
        for (SimEmployeeData employee : ctx.getEmployeeMap().values()) {
            int employeeId = employee.getEmployeeId();
            EmployeeProfileCfg profile = GameDataManager.getEmployeeProfileCfg(employeeId);
            if (profile == null) {
                continue;
            }
            employeeProfessions.put(employeeId, profile.getProfessionID());
            Map<BuildingOutputType, SimAttributeBonus> bonuses = personal.computeIfAbsent(employeeId,
                    key -> new EnumMap<>(BuildingOutputType.class));
            EmployeeLevelCfg level = configCache.getEmployeeLevelCfgByIdAndLevel(employeeId, employee.getLevel());
            if (level != null && level.getAttributeValue() != null) {
                long offset = (long) employee.getLevel() - level.getLevel();
                Map<Integer, Integer> additions = level.getAddAttribute();
                Map<Integer, Integer> breakthrough = configCache.getEmployeeBreakthroughStats(level);
                level.getAttributeValue().forEach((type, value) -> addEmployeeAttribute(bonuses, type,
                        value + offset * (additions == null ? 0 : additions.getOrDefault(type, 0))
                                + breakthrough.getOrDefault(type, 0)));
            }
            Map<Integer, EmployeeStarCfg> stars = starConfigs == null ? null : starConfigs.get(employeeId);
            EmployeeStarCfg star = stars == null ? null : stars.get(employee.getStar());
            if (star != null && star.getSupervisorBonus() != null) {
                star.getSupervisorBonus().forEach((type, value) -> addEmployeeAttribute(bonuses, type, value));
            }
            if (employee.getSkillLevels() == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> skill : employee.getSkillLevels().entrySet()) {
                EmployeeSkillConfigCfg cfg = configCache.getEmployeeSkillCfg(skill.getKey(), skill.getValue());
                if (cfg == null || !active(ctx, employeeId, profile.getProfessionID(), cfg.getSkillType())) {
                    continue;
                }
                if (cfg.getEmployee() != null) {
                    for (List<Integer> effect : cfg.getEmployee()) {
                        if (effect == null || effect.size() < 3) {
                            continue;
                        }
                        Map<BuildingOutputType, SimAttributeBonus> target = effect.getFirst() == 0 ? bonuses
                                : professions.computeIfAbsent(effect.getFirst(), key -> new EnumMap<>(BuildingOutputType.class));
                        addEmployeeAttribute(target, effect.get(1), effect.get(2));
                    }
                }
                addBuildingEffects(buildings, cfg.getBuff(), false);
                addBuildingEffects(buildings, cfg.getModifier(), true);
            }
        }

        Map<Integer, Map<BuildingOutputType, Long>> attributes = new HashMap<>();
        Map<Integer, Long> professionAttributes = new HashMap<>();
        long totalCombatPower = 0;
        for (Map.Entry<Integer, Integer> employee : employeeProfessions.entrySet()) {
            Map<BuildingOutputType, SimAttributeBonus> own = personal.get(employee.getKey());
            Map<BuildingOutputType, SimAttributeBonus> shared = professions.getOrDefault(employee.getValue(), Collections.emptyMap());
            EnumSet<BuildingOutputType> types = EnumSet.noneOf(BuildingOutputType.class);
            types.addAll(own.keySet());
            types.addAll(shared.keySet());
            Map<BuildingOutputType, Long> values = new EnumMap<>(BuildingOutputType.class);
            long combatPower = 0;
            for (BuildingOutputType type : types) {
                SimAttributeBonus bonus = new SimAttributeBonus();
                merge(bonus, own.get(type));
                merge(bonus, shared.get(type));
                long value = bonus.apply(0);
                values.put(type, value);
                combatPower += value;
            }
            attributes.put(employee.getKey(), Map.copyOf(values));
            professionAttributes.merge(employee.getValue(), combatPower, Long::sum);
            totalCombatPower += combatPower;
        }
        return new Snapshot(attributes, professionAttributes, buildings, totalCombatPower);
    }

    private boolean active(SimPlayerContext ctx, int employeeId, int professionId, int skillType) {
        return skillType == 2 || skillType == 1 && ctx.getCurrentCasino() != null
                && ctx.getCurrentCasino().manageEmploy(professionId) == employeeId;
    }

    private void addEmployeeAttribute(Map<BuildingOutputType, SimAttributeBonus> bonuses, int code, long value) {
        BuildingOutputType type = BuildingOutputType.fromCode(code);
        BuildingOutputType attribute = type == null ? null : type.employeeAttribute();
        if (attribute == null) {
            return;
        }
        SimAttributeBonus bonus = bonuses.computeIfAbsent(attribute, key -> new SimAttributeBonus());
        if (type == attribute) {
            bonus.addFixed(value);
        } else {
            bonus.addPercent(value);
        }
    }

    private void addBuildingEffects(Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> buildings,
                                    List<List<Integer>> effects, boolean percent) {
        if (effects == null) {
            return;
        }
        for (List<Integer> effect : effects) {
            if (effect == null || effect.size() < 3) {
                continue;
            }
            BuildingOutputType type = BuildingOutputType.fromCode(effect.get(1));
            if (type == null || type.getCode() > 13) {
                continue;
            }
            SimAttributeBonus bonus = buildings.computeIfAbsent(effect.getFirst(), key -> new EnumMap<>(BuildingOutputType.class))
                    .computeIfAbsent(type, key -> new SimAttributeBonus());
            if (percent) {
                bonus.addPercent(effect.get(2));
            } else {
                bonus.addFixed(effect.get(2));
            }
        }
    }

    private static void merge(SimAttributeBonus target, SimAttributeBonus source) {
        if (source != null) {
            target.addFixed(source.fixed());
            target.addPercent(source.percent());
        }
    }

    public static final class Snapshot {
        private final Map<Integer, Map<BuildingOutputType, Long>> attributes;
        private final Map<Integer, Long> professionAttributes;
        private final Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> buildings;
        private final long totalCombatPower;

        private Snapshot(Map<Integer, Map<BuildingOutputType, Long>> attributes, Map<Integer, Long> professionAttributes,
                         Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> buildings, long totalCombatPower) {
            this.attributes = attributes;
            this.professionAttributes = professionAttributes;
            this.buildings = buildings;
            this.totalCombatPower = totalCombatPower;
        }

        public Map<BuildingOutputType, Long> attributes(int employeeId) {
            return attributes.getOrDefault(employeeId, Collections.emptyMap());
        }

        public long combatPower(int employeeId) {
            return attributes(employeeId).values().stream().mapToLong(Long::longValue).sum();
        }

        public long totalCombatPower() {
            return totalCombatPower;
        }

        public long professionAttribute(int professionId) {
            return professionAttributes.getOrDefault(professionId, 0L);
        }

        public void addBuildingBonus(int buildingId, BuildingOutputType outputType, SimAttributeBonus target) {
            addBuildingTarget(0, outputType, target);
            if (buildingId != 0) {
                addBuildingTarget(buildingId, outputType, target);
            }
        }

        private void addBuildingTarget(int buildingId, BuildingOutputType type, SimAttributeBonus target) {
            Map<BuildingOutputType, SimAttributeBonus> bonuses = buildings.get(buildingId);
            if (bonuses == null) {
                return;
            }
            merge(target, bonuses.get(type));
            if (type.bonusGroup() != type) {
                merge(target, bonuses.get(type.bonusGroup()));
            }
        }
    }
}
