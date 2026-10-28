package data.scripts.mastery;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI.SkillLevelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.impl.campaign.ids.Skills;
import com.fs.starfarer.api.impl.campaign.plog.OfficerSkillGainRecord;
import com.fs.starfarer.api.impl.campaign.plog.PlaythroughLog;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.oee_Settings;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mastery experience and respecs.
 * - Officer mastery: experience an officer in the player's fleet would lose to the level cap is banked on the officer
 *   (person memory), times the "Officer mastery experience rate" setting.
 * - Global mastery: a fleet-wide pool (sector memory) that receives a fraction, on top, of all mastery experience
 *   officers bank. It pays whatever an officer's own mastery can't cover.
 * - A respec costs the experience from level 1 to the officer's maximum level, and lets the player pick a new set of
 *   vanilla combat skills: as many skills, and as many elite skills, as the officer has now. Skills from mods are
 *   left alone and don't count, since mods can put an officer's level and skill count out of step.
 */
public class oee_Mastery {

    private static final String KEY_OFFICER_XP = "$oee_masteryXP";
    private static final String KEY_GLOBAL_XP = "$oee_globalMasteryXP";
    private static final String VANILLA_SKILLS_PATH = "data/characters/skills/skill_data.csv";

    private static final Logger log = Global.getLogger(oee_Mastery.class);
    private static Set<String> vanillaSkills;

    // ---- pools ----

    public static long getOfficerXP(PersonAPI person) {
        return getLong(person.getMemoryWithoutUpdate(), KEY_OFFICER_XP);
    }

    public static long getGlobalXP() {
        return getLong(Global.getSector().getMemoryWithoutUpdate(), KEY_GLOBAL_XP);
    }

    public static void addOfficerXP(PersonAPI person, long xp) {
        MemoryAPI memory = person.getMemoryWithoutUpdate();
        memory.set(KEY_OFFICER_XP, Math.max(0L, getLong(memory, KEY_OFFICER_XP) + xp));
    }

    public static void addGlobalXP(long xp) {
        MemoryAPI memory = Global.getSector().getMemoryWithoutUpdate();
        memory.set(KEY_GLOBAL_XP, Math.max(0L, getLong(memory, KEY_GLOBAL_XP) + xp));
    }

    private static long getLong(MemoryAPI memory, String key) {
        Object value = memory.get(key);
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    // ---- experience gain, called from the patches ----

    /**
     * An officer is about to gain experience through the clamping addXP. Vanilla throws away whatever goes past the
     * experience for the maximum level; bank that part, and give the global pool its share of what was banked.
     */
    public static void onOfficerXP(OfficerDataAPI officer, long xp, TextPanelAPI textPanel) {
        PersonAPI person = officer.getPerson();
        // vanilla gives mercenaries no experience at all
        if (person == null || xp <= 0 || Misc.isMercenary(person)) return;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null || fleet.getFleetData().getOfficerData(person) != officer) return;

        OfficerLevelupPlugin plugin = getLevelPlugin();
        int maxLevel = plugin.getMaxLevel(person);
        long cap = plugin.getXPForLevel(maxLevel);
        long overflow;
        if (person.getStats().getLevel() >= maxLevel) {
            overflow = xp;
        } else {
            overflow = Math.max(0L, person.getStats().getXP() + xp - cap);
        }
        long banked = (long) (overflow * oee_Settings.MASTERY_XP_MULT);
        if (banked > 0) {
            addOfficerXP(person, banked);
            // vanilla reports nothing for an officer already at the cap, so say where the experience went
            if (textPanel != null) addMasteryLine(textPanel, person.getNameString() + " gained ", banked, " mastery experience");
            // the global pool's share comes on top; the officer keeps all of what they banked
            long share = (long) (banked * oee_Settings.GLOBAL_MASTERY_FRACTION);
            if (share > 0) {
                addGlobalXP(share);
                // reported once for the whole battle, after the officers (oee_BattleXPReportPatch)
                if (textPanel != null) pendingGlobalReport += share;
            }
        }
    }

    /** Global mastery gained during the current post-battle officer XP pass, not yet shown. */
    private static long pendingGlobalReport = 0L;

    public static void resetGlobalReport() {
        pendingGlobalReport = 0L;
    }

    /** Shows "Gained X global mastery experience" for the global mastery gained since the reset, if any. */
    public static void showGlobalReport(TextPanelAPI textPanel) {
        long amount = pendingGlobalReport;
        pendingGlobalReport = 0L;
        if (textPanel != null && amount > 0) addMasteryLine(textPanel, "Gained ", amount, " global mastery experience");
    }

    /** A line styled like vanilla's officer experience lines, with the amount in the story point color. */
    private static void addMasteryLine(TextPanelAPI textPanel, String before, long amount, String after) {
        String value = Misc.getWithDGS(amount);
        textPanel.addParagraph(before + value + after, Misc.getTooltipTitleAndLightHighlightColor());
        textPanel.highlightLastInLastPara(value, Misc.getStoryOptionColor());
    }

    // ---- respec ----

    public static OfficerLevelupPlugin getLevelPlugin() {
        return (OfficerLevelupPlugin) Global.getSettings().getPlugin("officerLevelUp");
    }

    /** Officers that get a respec button: the player's own non-mercenary human officers at their maximum level. */
    public static boolean isRespecCandidate(OfficerDataAPI officer) {
        if (officer == null) return false;
        PersonAPI person = officer.getPerson();
        if (person == null || person.isPlayer() || person.isAICore() || Misc.isMercenary(person)) return false;
        return person.getStats().getLevel() >= getLevelPlugin().getMaxLevel(person);
    }

    /** The same, for a person: also checks that they are an officer in the player's fleet. */
    public static boolean isRespecCandidate(PersonAPI person) {
        if (person == null || Global.getSector() == null) return false;
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        return fleet != null && isRespecCandidate(fleet.getFleetData().getOfficerData(person));
    }

    /**
     * The experience from level 1 to the officer's level, times the cost setting. The officer's own level when it's
     * above their maximum: vanilla's exceptional cryopod officers are level 7 with the normal maximum (6 with Officer
     * Training), since they don't carry $officerMaxLevel.
     */
    public static long getRespecCost(PersonAPI person) {
        OfficerLevelupPlugin plugin = getLevelPlugin();
        int level = Math.max(person.getStats().getLevel(), plugin.getMaxLevel(person));
        return (long) (plugin.getXPForLevel(level) * oee_Settings.RESPEC_COST_MULT);
    }

    public static boolean canAfford(PersonAPI person) {
        return getOfficerXP(person) + getGlobalXP() >= getRespecCost(person);
    }

    /** Pays the cost from the officer's own mastery first, the rest from the global pool. False if short. */
    public static boolean spend(PersonAPI person, long cost) {
        long own = getOfficerXP(person);
        long global = getGlobalXP();
        if (own + global < cost) return false;
        long fromOwn = Math.min(own, cost);
        addOfficerXP(person, -fromOwn);
        addGlobalXP(-(cost - fromOwn));
        return true;
    }

    public static boolean isVanillaSkill(String id) {
        if (vanillaSkills == null) {
            vanillaSkills = new HashSet<String>();
            try {
                // the game's own copy only, so a mod that adds rows doesn't make its skills count as vanilla
                JSONArray rows = Global.getSettings().loadCSV(VANILLA_SKILLS_PATH, false);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String skillId = row.optString("id", "").trim();
                    if (!skillId.isEmpty() && !skillId.startsWith("#")) vanillaSkills.add(skillId);
                }
            } catch (Exception e) {
                log.error("Officer Extendedest: couldn't read " + VANILLA_SKILLS_PATH, e);
            }
        }
        return vanillaSkills.contains(id);
    }

    /** A vanilla combat skill, the kind a respec counts and replaces. */
    public static boolean isRespecSkill(SkillSpecAPI spec) {
        return spec != null && spec.isCombatOfficerSkill() && isVanillaSkill(spec.getId());
    }

    /** A respec skill an officer may pick: the same tags vanilla's level-up picks exclude, plus NPC- and core-only. */
    public static boolean isPickable(SkillSpecAPI spec) {
        return isRespecSkill(spec)
                && !spec.hasTag(Skills.TAG_DEPRECATED)
                && !spec.hasTag(Skills.TAG_PLAYER_ONLY)
                && !spec.hasTag(Skills.TAG_NPC_ONLY)
                && !spec.hasTag(Skills.TAG_AI_CORE_ONLY);
    }

    /** Whether the officer may have this skill at the elite level. */
    public static boolean canBeElite(SkillSpecAPI spec) {
        return !spec.hasTag(Skills.TAG_ELITE_PLAYER_ONLY);
    }

    /** The officer's respec skills in the game's skill order, mapped to whether each is elite. */
    public static Map<String, Boolean> getCurrentSkills(PersonAPI person) {
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        for (String id : Global.getSettings().getSortedSkillIds()) {
            float level = person.getStats().getSkillLevel(id);
            if (level <= 0) continue;
            if (!isRespecSkill(Global.getSettings().getSkillSpec(id))) continue;
            result.put(id, level > 1f);
        }
        return result;
    }

    /** The skills the respec menu shows: every pickable skill, plus any respec skill the officer already has. */
    public static List<String> getSkillPool(PersonAPI person) {
        List<String> result = new ArrayList<String>();
        for (String id : Global.getSettings().getSortedSkillIds()) {
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
            if (isPickable(spec) || (isRespecSkill(spec) && person.getStats().getSkillLevel(id) > 0)) {
                result.add(id);
            }
        }
        return result;
    }

    /** Replaces the officer's respec skills with the chosen ones (id to elite). Other skills are untouched. */
    public static void applySkills(PersonAPI person, Map<String, Boolean> chosen) {
        Map<String, Boolean> before = getCurrentSkills(person);
        for (SkillLevelAPI skill : person.getStats().getSkillsCopy()) {
            if (skill.getLevel() > 0 && isRespecSkill(skill.getSkill())) {
                person.getStats().setSkillLevel(skill.getSkill().getId(), 0f);
            }
        }
        for (Map.Entry<String, Boolean> entry : chosen.entrySet()) {
            person.getStats().setSkillLevel(entry.getKey(), entry.getValue() ? 2f : 1f);
        }
        updateSkillLog(person, before, chosen);
    }

    /**
     * Keeps the playthrough log's officer skill records in step, as vanilla does: a record when an officer learns a
     * skill (level-up), a separate elite record when it becomes elite (make elite, retrain). Skills that didn't change
     * keep their records, so their order of gain is kept; other mods read that order (JaydeePiracy's Personal
     * Discretion picks the most recently gained skills to make inactive).
     */
    private static void updateSkillLog(PersonAPI person, Map<String, Boolean> before, Map<String, Boolean> after) {
        PlaythroughLog log = PlaythroughLog.getInstance();
        if (log == null) return;
        String id = person.getId();
        for (Map.Entry<String, Boolean> entry : before.entrySet()) {
            String skill = entry.getKey();
            if (!after.containsKey(skill)) {
                log.removeOfficerSkillRecord(id, skill, false);
                log.removeOfficerSkillRecord(id, skill, true);
            } else if (entry.getValue() && !after.get(skill)) {
                log.removeOfficerSkillRecord(id, skill, true);
            }
        }
        for (Map.Entry<String, Boolean> entry : after.entrySet()) {
            String skill = entry.getKey();
            Boolean wasElite = before.get(skill);
            if (wasElite == null) log.addOfficerSkillRecord(new OfficerSkillGainRecord(id, skill, false));
            if (entry.getValue() && !Boolean.TRUE.equals(wasElite)) {
                log.addOfficerSkillRecord(new OfficerSkillGainRecord(id, skill, true));
            }
        }
    }
}
