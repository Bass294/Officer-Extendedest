package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * UI sounds for the respec menu. Picking a skill plays its aptitude's sound for the skill's tier, as vanilla's AI core
 * skill picker does (sound1..sound5 in aptitude_data.csv); making it elite plays the aptitude's story point sound.
 * A missing sound set throws in the sound player, so every sound here is played through play(), which logs instead.
 */
public class oee_Sounds {

    public static final String BUTTON = "ui_button_pressed";
    public static final String DESELECT = "ui_char_decrease_skill";
    public static final String REFUSE = "ui_char_can_not_increase_skill_or_aptitude";
    public static final String CONFIRM = "ui_char_reset";

    private static final String APTITUDES_PATH = "data/characters/skills/aptitude_data.csv";
    private static final Logger log = Global.getLogger(oee_Sounds.class);
    private static Map<String, JSONObject> aptitudes;

    public static void play(String id) {
        if (id == null || id.isEmpty()) return;
        try {
            Global.getSoundPlayer().playUISound(id, 1f, 1f);
        } catch (RuntimeException e) {
            log.warn("Officer Extendedest: couldn't play sound " + id, e);
        }
    }

    public static void playSelect(SkillSpecAPI spec) {
        JSONObject aptitude = getAptitude(spec);
        int tier = Math.max(1, Math.min(5, spec.getTier()));
        play(aptitude != null ? aptitude.optString("sound" + tier, BUTTON) : BUTTON);
    }

    public static void playElite(SkillSpecAPI spec) {
        JSONObject aptitude = getAptitude(spec);
        play(aptitude != null ? aptitude.optString("sound_story", BUTTON) : BUTTON);
    }

    /** The skill's row of aptitude_data.csv (sounds, elite overlay sprites), or null. */
    public static JSONObject getAptitude(SkillSpecAPI spec) {
        if (aptitudes == null) {
            aptitudes = new HashMap<String, JSONObject>();
            try {
                // merged by id across the game and every mod: loadCSV(path, true) reads a single file, so with mods
                // that ship their own aptitude_data.csv it can miss the vanilla aptitudes entirely
                JSONArray rows = Global.getSettings().getMergedSpreadsheetData("id", APTITUDES_PATH);
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String id = row.optString("id", "").trim();
                    if (!id.isEmpty() && !id.startsWith("#")) aptitudes.put(id, row);
                }
            } catch (Exception e) {
                log.error("Officer Extendedest: couldn't read " + APTITUDES_PATH, e);
            }
        }
        return aptitudes.get(spec.getGoverningAptitudeId());
    }
}
