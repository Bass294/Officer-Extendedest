package data.scripts.patches;

import com.fs.starfarer.api.impl.campaign.FleetEncounterContext;
import data.scripts.mastery.oee_Mastery;
import patchlib.api.context.AfterContext;
import patchlib.api.context.BeforeContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Before;
import patchlib.api.patch.Patch;

/**
 * The post-battle "Gained X global mastery experience" line. After a battle, FleetEncounterContext.gainXP pays the
 * officers (gainOfficerXP), then the player; oee_OfficerXPPatch totals the global share while the officers are paid,
 * and the total is shown here, after the officers' lines and before the player's.
 */
@Patch(target = @ClassMatch(subtype = FleetEncounterContext.class))
public class oee_BattleXPReportPatch {

    @Before(target = @MethodMatch(methodName = "gainOfficerXP", parameterCount = 2))
    public static void beforeGainOfficerXP(BeforeContext context) {
        if (!context.isMostDerivedCall()) return;
        oee_Mastery.resetGlobalReport();
    }

    @After(target = @MethodMatch(methodName = "gainOfficerXP", parameterCount = 2))
    public static void afterGainOfficerXP(AfterContext context) {
        // a subclass calling super would get here twice
        if (!context.isMostDerivedCall()) return;
        Object self = context.getSelf();
        if (self instanceof FleetEncounterContext) {
            oee_Mastery.showGlobalReport(((FleetEncounterContext) self).textPanelForXPGain);
        }
    }
}
