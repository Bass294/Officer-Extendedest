package data.scripts.patches;

import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import data.scripts.mastery.oee_Mastery;
import data.scripts.ui.oee_RespecButton;
import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

/**
 * One officer row in the vanilla officer picker. recreate() rebuilds the row's children from scratch (it starts with
 * clearChildren); after it, officers at their maximum level get a "Respec..." button in the slot of the "Mentor..."
 * button, which vanilla hides at maximum level.
 */
// 0.98a-RC8 Windows: this is com.fs.starfarer.coreui.ooo0, the row class of CaptainPickerDialog. The class name
// will change; the package and the method names matched below should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = {
                @MethodMatch(methodName = "updateStatusString", parameterCount = 0),
                @MethodMatch(methodName = "isPastMax", parameterCount = 0),
                @MethodMatch(methodName = "getOfficerData", parameterCount = 0)
        }))
public class oee_OfficerRowPatch {

    private static final MethodQuerySpec GET_OFFICER_DATA = MethodQuery.create().methodName("getOfficerData").parameterCount(0).build();
    private static final MethodQuerySpec RECREATE = MethodQuery.create().methodName("recreate").parameterCount(0).build();
    // the picker that owns the row; the row's only CaptainPickerDialog field
    private static final FieldQuerySpec PICKER = FieldQuery.create().typeName("com.fs.starfarer.coreui.CaptainPickerDialog").build();

    @After(target = @MethodMatch(methodName = "recreate", parameterCount = 0))
    public static void afterRecreate(AfterContext context) {
        if (!context.isMostDerivedCall()) return;
        Object self = context.getSelf();
        if (!(self instanceof UIPanelAPI) || !context.hasMethod(GET_OFFICER_DATA) || !context.hasField(PICKER)) return;
        Object data = context.getMethod(GET_OFFICER_DATA).call();
        if (!(data instanceof OfficerDataAPI) || !oee_Mastery.isRespecCandidate((OfficerDataAPI) data)) return;
        Object picker = context.getField(PICKER).get();
        if (!(picker instanceof UIPanelAPI)) return;
        oee_RespecButton.add((UIPanelAPI) self, (OfficerDataAPI) data, (UIPanelAPI) picker, context.getMethod(RECREATE));
    }
}
