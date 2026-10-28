package data.scripts.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.ui.CustomPanelAPI;
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
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import patchlib.api.query.MethodQuery;
import patchlib.api.spec.MethodQuerySpec;

import java.awt.Color;
import java.util.List;

/**
 * The officer picker: a "Global mastery experience: X" line right above vanilla's "Officers: x / y" counter at the
 * bottom left, styled like it (small insignia font, gray), with the amount in the story point color and a tooltip.
 * sizeChanged rebuilds the picker's inner panel from scratch (clearChildren first), so the line is added after it.
 */
@Patch(target = @ClassMatch(typeName = "com.fs.starfarer.coreui.CaptainPickerDialog"))
public class oee_CaptainPickerMasteryPatch {

    private static final String OFFICERS_PREFIX = "Officers:";
    private static final String PREFIX = "Global mastery experience: ";
    private static final float WIDTH = 400f;
    private static final float HEIGHT = 20f;

    private static final MethodQuerySpec GET_INNER_PANEL = MethodQuery.create().methodName("getInnerPanel").parameterCount(0).build();
    private static final MethodQuerySpec GET_CHILDREN = MethodQuery.create().methodName("getChildrenCopy").parameterCount(0).build();

    @After(target = @MethodMatch(methodName = "sizeChanged", parameterCount = 2))
    public static void afterSizeChanged(AfterContext context) {
        if (!context.hasMethod(GET_INNER_PANEL)) return;
        Object innerObj = context.getMethod(GET_INNER_PANEL).call();
        if (!(innerObj instanceof UIPanelAPI) || !context.hasMethod(GET_CHILDREN, innerObj)) return;
        UIPanelAPI inner = (UIPanelAPI) innerObj;

        LabelAPI officers = null;
        Object children = context.getMethod(GET_CHILDREN, inner).call();
        if (children instanceof List) {
            for (Object child : (List<?>) children) {
                if (child instanceof LabelAPI && ((LabelAPI) child).getText() != null
                        && ((LabelAPI) child).getText().startsWith(OFFICERS_PREFIX)) {
                    officers = (LabelAPI) child;
                    break;
                }
            }
        }
        // vanilla hides the counter when the picker lists AI cores
        if (officers == null || officers.getOpacity() <= 0f || !(officers instanceof UIComponentAPI)) return;

        LiveValue live = new LiveValue();
        CustomPanelAPI holder = Global.getSettings().createCustom(WIDTH, HEIGHT, live);
        TooltipMakerAPI el = holder.createUIElement(WIDTH, HEIGHT, false);
        el.setParaSmallInsignia();
        live.shown = oee_Mastery.getGlobalXP();
        LabelAPI line = el.addPara(PREFIX + "%s", 0f,
                Global.getSettings().getColor("textGrayColor"), Misc.getStoryOptionColor(),
                Misc.getWithDGS(live.shown));
        live.line = line;
        // a tooltip shifts its first component 5 px right; undo that so the line lines up with the counter
        ((UIComponentAPI) line).getPosition().setXAlignOffset(0f);
        line.setHighlightOnMouseover(true);
        el.addTooltipToPrevious(new TooltipCreator() {
            @Override
            public boolean isTooltipExpandable(Object tooltipParam) {
                return false;
            }

            @Override
            public float getTooltipWidth(Object tooltipParam) {
                return 450f;
            }

            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                Color green = Misc.getStoryOptionColor();
                tooltip.addPara("An additional %s of %s earned is gained as %s.", 0f,
                        new Color[]{Misc.getHighlightColor(), green, green},
                        Math.round(oee_Settings.GLOBAL_MASTERY_FRACTION * 100f) + "%",
                        "mastery exp", "global mastery exp");
                tooltip.addPara("%s may substitute for officer %s.", 10f,
                        new Color[]{green, green}, "Global mastery exp", "mastery exp");
            }
        }, TooltipLocation.ABOVE);
        holder.addUIElement(el).inTL(0f, 0f);
        inner.addComponent(holder).aboveLeft((UIComponentAPI) officers, 0f);
    }

    /**
     * Keeps the number current: a respec spends global mastery while the picker stays open, and only the officer's
     * row is rebuilt then, not the picker.
     */
    private static class LiveValue extends BaseCustomUIPanelPlugin {
        LabelAPI line;
        long shown;

        @Override
        public void advance(float amount) {
            if (line == null) return;
            long global = oee_Mastery.getGlobalXP();
            if (global == shown) return;
            shown = global;
            String value = Misc.getWithDGS(global);
            line.setText(PREFIX + value);
            line.setHighlight(value);
            line.setHighlightColor(Misc.getStoryOptionColor());
        }
    }
}
