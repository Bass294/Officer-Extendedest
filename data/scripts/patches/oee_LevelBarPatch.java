package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.mastery.oee_Mastery;
import data.scripts.oee_Settings;
import patchlib.api.context.AfterContext;
import patchlib.api.context.HookContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.match.MethodType;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.FieldQuery;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.FieldQuerySpec;
import patchlib.api.spec.MethodQuerySpec;

import java.awt.Color;
import java.util.List;

/**
 * The officer level bar ("Level 7 (maximum)"), shown on officer rows in the officer picker and in the officer hover
 * tooltip. Vanilla draws it full at the maximum level; for the player's officers that can respec, it instead fills
 * from the left in the story point color (the color of an officer's bonus experience) as their mastery experience
 * grows toward the respec cost, and stops at full. Where vanilla gives the bar a hover tooltip (the picker rows: the
 * officer's experience, or "Maximum level reached"), it shows the mastery progress instead.
 *
 * Vanilla only refreshes the bar when the officer's experience or level changes, so the fill is set again after
 * every advance, and the tooltip replaced again whenever vanilla has put its own back.
 */
// 0.98a-RC8 Windows: the bar is com.fs.starfarer.coreui.null and its progress bar com.fs.starfarer.coreui.b. Both
// names will change; the package, getBar/getLabel, advanceImpl and setCurrWithFraction should not.
@Patch(target = @ClassMatch(
        subtype = UIPanelAPI.class,
        packageName = "com.fs.starfarer.coreui",
        methodMatches = {
                @MethodMatch(methodName = "getBar", parameterCount = 0),
                @MethodMatch(methodName = "getLabel", parameterCount = 0)
        }))
public class oee_LevelBarPatch {

    private static final String KEY_TOOLTIP = "oee_masteryTooltip";
    private static final float TOOLTIP_WIDTH = 360f;

    private static final FieldQuerySpec PERSON = FieldQuery.create().subtype(PersonAPI.class).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();
    private static final MethodQuerySpec SET_FRACTION = MethodQuery.create().methodName("setCurrWithFraction").parameterCount(3).build();
    private static final MethodQuerySpec GET_TOOLTIP = MethodQuery.create().methodName("getTooltip").parameterCount(0).build();

    // a throwaway tooltip maker: addTooltipTo attaches to whatever component it is given
    private static TooltipMakerAPI tooltipMaker;

    @After(target = @MethodMatch(methodType = MethodType.CONSTRUCTOR, parameterCount = 5))
    public static void afterConstruct(AfterContext context) {
        apply(context);
    }

    @After(target = @MethodMatch(methodName = "advanceImpl", parameterCount = 1))
    public static void afterAdvance(AfterContext context) {
        apply(context);
    }

    private static void apply(HookContext context) {
        if (!context.isMostDerivedCall() || !context.hasField(PERSON)) return;
        Object personObj = context.getField(PERSON).get();
        if (!(personObj instanceof PersonAPI) || !oee_Mastery.isRespecCandidate((PersonAPI) personObj)) return;
        final PersonAPI person = (PersonAPI) personObj;

        Object bar = findProgressBar(context);
        if (bar == null) return;

        long cost = oee_Mastery.getRespecCost(person);
        float fraction = cost <= 0 ? 1f : Math.min(1f, (float) oee_Mastery.getOfficerXP(person) / cost);
        // nothing filled normally, then the story point section from the left edge: vanilla's bonus experience look
        context.getMethod(SET_FRACTION, bar).call(0f, Misc.getStoryOptionColor(), fraction);

        // replace vanilla's hover tooltip, where it has one, and again whenever vanilla puts its own back
        if (!(bar instanceof UIComponentAPI) || !context.hasMethod(GET_TOOLTIP, bar)) return;
        Object current = context.getMethod(GET_TOOLTIP, bar).call();
        if (current == null || current == context.getData().get(KEY_TOOLTIP)) return;
        getTooltipMaker().addTooltipTo(new MasteryTooltip(person), (UIComponentAPI) bar, TooltipLocation.ABOVE);
        context.getData().putWeak(KEY_TOOLTIP, context.getMethod(GET_TOOLTIP, bar).call());
    }

    /** The progress bar: the bar's child with setCurrWithFraction(float, Color, float). */
    private static Object findProgressBar(HookContext context) {
        if (!context.hasMethod(GET_CHILDREN)) return null;
        Object children = context.getMethod(GET_CHILDREN).call();
        if (!(children instanceof List)) return null;
        for (Object child : (List<?>) children) {
            if (child != null && context.hasMethod(SET_FRACTION, child)) return child;
        }
        return null;
    }

    private static TooltipMakerAPI getTooltipMaker() {
        if (tooltipMaker == null) {
            tooltipMaker = Global.getSettings().createCustom(0f, 0f, null).createUIElement(0f, 0f, false);
        }
        return tooltipMaker;
    }

    /** "X / Y" mastery experience toward the respec cost, then how mastery experience is earned. */
    private static class MasteryTooltip implements TooltipCreator {
        private final PersonAPI person;

        MasteryTooltip(PersonAPI person) {
            this.person = person;
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return TOOLTIP_WIDTH;
        }

        @Override
        public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
            Color green = Misc.getStoryOptionColor();
            String progress = Misc.getWithDGS(oee_Mastery.getOfficerXP(person)) + " / "
                    + Misc.getWithDGS(oee_Mastery.getRespecCost(person));
            LabelAPI line = tooltip.addPara("%s", 0f, green, progress);
            line.setAlignment(com.fs.starfarer.api.ui.Alignment.MID);
            String percent = Math.round(oee_Settings.GLOBAL_MASTERY_FRACTION * 100f) + "%";
            tooltip.addPara("Officers continue earning exp at max level as %s.", 10f, green, "mastery exp");
            tooltip.addPara("An additional %s of %s earned is gained as %s.", 10f,
                    new Color[]{Misc.getHighlightColor(), green, green},
                    percent, "mastery exp", "global mastery exp");
        }
    }
}
