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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

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
            if (employee.getSkillLevels() == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> skill : employee.getSkillLevels().entrySet()) {
                EmployeeSkillConfigCfg cfg = configCache.getEmployeeSkillCfg(skill.getKey(), skill.getValue());
                if (cfg == null || !active(ctx, employeeId, profile.getProfessionID(), cfg.getSkillType())) {
                    continue;
                }
                addEmployeeEffects(cfg, profession -> profession == 0
                        ? personal.computeIfAbsent(employeeId, key -> new EnumMap<>(BuildingOutputType.class))
                        : professions.computeIfAbsent(profession, key -> new EnumMap<>(BuildingOutputType.class)));
                addBuildingEffects(buildings, cfg.getBuff(), false);
                addBuildingEffects(buildings, cfg.getModifier(), true);
            }
        }

        Map<Integer, Map<BuildingOutputType, Long>> attributes = new HashMap<>();
        Map<Integer, Long> professionAttributes = new HashMap<>();
        long totalCombatPower = 0;
        for (SimEmployeeData employee : ctx.getEmployeeMap().values()) {
            int employeeId = employee.getEmployeeId();
            Integer profession = employeeProfessions.get(employeeId);
            if (profession == null) {
                continue;
            }
            Map<Integer, EmployeeStarCfg> stars = starConfigs == null ? null : starConfigs.get(employeeId);
            EmployeeStarCfg star = stars == null ? null : stars.get(employee.getStar());
            var bonuses = growthBonuses(employeeId, employee.getLevel(), star);
            var values = attributeValues(bonuses, personal.get(employeeId), professions.get(profession));
            long combatPower = 0;
            for (long value : values.values()) {
                combatPower += value;
            }
            attributes.put(employeeId, Map.copyOf(values));
            professionAttributes.merge(profession, combatPower, Long::sum);
            totalCombatPower += combatPower;
        }
        return new Snapshot(attributes, professionAttributes, buildings, totalCombatPower,
                employeeProfessions, personal, professions);
    }

    record GrowthPreview(Map<BuildingOutputType, Long> nextStar, Map<BuildingOutputType, Long> nextLevel,
                         Map<BuildingOutputType, Long> nextTenLevels) {
    }

    GrowthPreview preview(SimPlayerContext ctx, SimEmployeeData employee, Snapshot snapshot,
                          int profession, int maxLevel) {
        int employeeId = employee.getEmployeeId();
        int level = employee.getLevel();
        Map<Integer, Map<Integer, EmployeeStarCfg>> starConfigs = configCache.getEmployeeStarCfgMap();
        Map<Integer, EmployeeStarCfg> stars = starConfigs == null ? null : starConfigs.get(employeeId);
        EmployeeStarCfg star = stars == null ? null : stars.get(employee.getStar());
        var current = snapshot.attributes(employeeId);
        var own = snapshot.personalSkills.get(employeeId);
        var shared = snapshot.professionSkills.get(profession);

        var nextStar = current;
        EmployeeStarCfg nextStarCfg = star == null || star.getStarUpCost() <= 0
                ? null : stars.get(employee.getStar() + 1);
        if (nextStarCfg != null) {
            var bonuses = growthBonuses(employeeId, level, nextStarCfg);
            int groupId = nextStarCfg.getUnlockSkill();
            if (groupId > 0 && !employee.getSkillLevels().containsKey(groupId)) {
                EmployeeSkillConfigCfg skill = configCache.getEmployeeSkillCfg(groupId, 0);
                if (skill != null && active(ctx, employeeId, profession, skill.getSkillType())) {
                    addEmployeeEffects(skill, target -> target == 0 || target == profession ? bonuses : null);
                }
            }
            nextStar = attributeValues(bonuses, own, shared);
        }

        int starLimit = star == null ? 0 : star.getLevelCap();
        int levelLimit = starLimit > 0 ? Math.min(maxLevel, starLimit) : maxLevel;
        if (level >= levelLimit) {
            return new GrowthPreview(nextStar, current, current);
        }
        var nextLevel = attributeValues(growthBonuses(employeeId, level + 1, star), own, shared);
        int target = configCache.getEmployeeTenLevelPreviewTarget(employeeId, level, levelLimit);
        var nextTenLevels = target == level ? current : target == level + 1 ? nextLevel
                : attributeValues(growthBonuses(employeeId, target, star), own, shared);
        return new GrowthPreview(nextStar, nextLevel, nextTenLevels);
    }

    private Map<BuildingOutputType, SimAttributeBonus> growthBonuses(int employeeId, int level, EmployeeStarCfg star) {
        Map<BuildingOutputType, SimAttributeBonus> bonuses = new EnumMap<>(BuildingOutputType.class);
        EmployeeLevelCfg cfg = configCache.getEmployeeLevelCfgByIdAndLevel(employeeId, level);
        if (cfg != null && cfg.getAttributeValue() != null) {
            long offset = (long) level - cfg.getLevel();
            Map<Integer, Integer> additions = cfg.getAddAttribute();
            Map<Integer, Integer> breakthrough = configCache.getEmployeeBreakthroughStats(cfg);
            cfg.getAttributeValue().forEach((type, value) -> addEmployeeAttribute(bonuses, type,
                    value + offset * (additions == null ? 0 : additions.getOrDefault(type, 0))
                            + breakthrough.getOrDefault(type, 0)));
        }
        if (star != null && star.getSupervisorBonus() != null) {
            star.getSupervisorBonus().forEach((type, value) -> addEmployeeAttribute(bonuses, type, value));
        }
        return bonuses;
    }

    private Map<BuildingOutputType, Long> attributeValues(Map<BuildingOutputType, SimAttributeBonus> bonuses,
                                                         Map<BuildingOutputType, SimAttributeBonus> own,
                                                         Map<BuildingOutputType, SimAttributeBonus> shared) {
        mergeAttributes(bonuses, own);
        mergeAttributes(bonuses, shared);
        Map<BuildingOutputType, Long> values = new EnumMap<>(BuildingOutputType.class);
        bonuses.forEach((type, bonus) -> values.put(type, bonus.apply(0)));
        return values;
    }

    private void mergeAttributes(Map<BuildingOutputType, SimAttributeBonus> bonuses,
                                 Map<BuildingOutputType, SimAttributeBonus> source) {
        if (source != null) {
            source.forEach((type, bonus) -> merge(bonuses.computeIfAbsent(type, key -> new SimAttributeBonus()), bonus));
        }
    }

    private void addEmployeeEffects(EmployeeSkillConfigCfg skill,
                                    IntFunction<Map<BuildingOutputType, SimAttributeBonus>> targetForProfession) {
        List<List<Integer>> effects = skill.getEmployee();
        if (effects == null) {
            return;
        }
        for (List<Integer> effect : effects) {
            if (effect == null || effect.size() < 3) {
                continue;
            }
            var target = targetForProfession.apply(effect.getFirst());
            if (target != null) {
                addEmployeeAttribute(target, effect.get(1), effect.get(2));
            }
        }
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
        private final Map<Integer, Integer> employeeProfessions;
        private final Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> personalSkills;
        private final Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> professionSkills;

        private Snapshot(Map<Integer, Map<BuildingOutputType, Long>> attributes, Map<Integer, Long> professionAttributes,
                         Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> buildings, long totalCombatPower,
                         Map<Integer, Integer> employeeProfessions,
                         Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> personalSkills,
                         Map<Integer, Map<BuildingOutputType, SimAttributeBonus>> professionSkills) {
            this.attributes = attributes;
            this.professionAttributes = professionAttributes;
            this.buildings = buildings;
            this.totalCombatPower = totalCombatPower;
            this.employeeProfessions = employeeProfessions;
            this.personalSkills = personalSkills;
            this.professionSkills = professionSkills;
        }

        public Map<BuildingOutputType, Long> attributes(int employeeId) {
            return attributes.getOrDefault(employeeId, Collections.emptyMap());
        }

        Integer profession(int employeeId) {
            return employeeProfessions.get(employeeId);
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
