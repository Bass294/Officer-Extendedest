package data.scripts.patches;

import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import data.scripts.mastery.oee_Mastery;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

/**
 * Officer experience. Every officer gain goes through OfficerData.addXP(long, TextPanelAPI, boolean clampXP), which
 * caps the officer's experience at their maximum level when clampXP is set and drops the rest. Before it runs, bank
 * the part past the cap as the officer's mastery experience, and the global pool its share of that on top.
 * Calls with clampXP off (setting up a newly hired officer's starting experience) aren't gains and are skipped.
 */
// 0.98a-RC8: OfficerData (com.fs.starfarer.rpg, DoNotObfuscate) is the only OfficerDataAPI implementation; the
// two-argument and one-argument addXP overloads delegate to this one.
@Patch(target = @ClassMatch(subtype = OfficerDataAPI.class))
public class oee_OfficerXPPatch {

    @Before(target = @MethodMatch(methodName = "addXP", parameterCount = 3))
    public static void beforeAddXP(BeforeContext context) {
        if (!context.isMostDerivedCall() || context.isSkipOriginal()) return;
        Object self = context.getSelf();
        Object xp = context.getArg(0);
        Object clamp = context.getArg(2);
        if (!(self instanceof OfficerDataAPI) || !(xp instanceof Long) || !Boolean.TRUE.equals(clamp)) return;
        Object panel = context.getArg(1);
        oee_Mastery.onOfficerXP((OfficerDataAPI) self, (Long) xp, panel instanceof TextPanelAPI ? (TextPanelAPI) panel : null);
    }
}
