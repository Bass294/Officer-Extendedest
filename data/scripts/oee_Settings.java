package data.scripts;

import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;

/** LunaLib settings, data/config/LunaSettings.csv. Read once at startup and again whenever the player changes them. */
public class oee_Settings implements LunaSettingsListener {

    public static final String MOD_ID = "officer_extendedest";

    /** Mastery experience banked per point of experience an officer gains past their level cap. */
    public static float MASTERY_XP_MULT = 1f;
    /** Fraction of the mastery experience officers bank that the global pool also gains, on top. */
    public static float GLOBAL_MASTERY_FRACTION = 0.1f;
    /** Multiplier on the respec cost (experience from level 1 to the officer's level). */
    public static float RESPEC_COST_MULT = 1f;

    public static void init() {
        oee_Settings listener = new oee_Settings();
        LunaSettings.addSettingsListener(listener);
        listener.settingsChanged(MOD_ID);
    }

    @Override
    public void settingsChanged(String modId) {
        if (!MOD_ID.equals(modId)) return;
        MASTERY_XP_MULT = getFloat("oee_masteryXPMult", 1f);
        GLOBAL_MASTERY_FRACTION = getFloat("oee_globalMasteryFraction", 0.1f);
        RESPEC_COST_MULT = getFloat("oee_respecCostMult", 1f);
    }

    private static float getFloat(String id, float ifNull) {
        Float value = LunaSettings.getFloat(MOD_ID, id);
        return value == null ? ifNull : value;
    }
}
