package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.mastery.oee_Mastery;
import patchlib.api.ref.MethodRef;


/** The "Respec..." button on an officer row, in the slot vanilla uses for "Mentor...". */
public class oee_RespecButton extends BaseCustomUIPanelPlugin {

    // vanilla's row layout: 7 px inset, buttons 100 px wide with 3 px gaps, "Mentor..." spans two of them and sits
    // 3 px under "Make skills elite..."/"Retrain...", whose bottom edge is 43 px above the row's
    private static final float WIDTH = 203f;
    private static final float HEIGHT = 20f;
    private static final float RIGHT = 7f;
    private static final float BOTTOM = 20f;

    private final OfficerDataAPI officer;
    private final UIPanelAPI picker;
    private final MethodRef recreateRow;
    private ButtonAPI button;

    private oee_RespecButton(OfficerDataAPI officer, UIPanelAPI picker, MethodRef recreateRow) {
        this.officer = officer;
        this.picker = picker;
        this.recreateRow = recreateRow;
    }

    public static void add(UIPanelAPI row, OfficerDataAPI officer, UIPanelAPI picker, MethodRef recreateRow) {
        final PersonAPI person = officer.getPerson();
        FactionAPI faction = Global.getSector().getPlayerFaction();
        oee_RespecButton plugin = new oee_RespecButton(officer, picker, recreateRow);
        CustomPanelAPI panel = Global.getSettings().createCustom(WIDTH, HEIGHT, plugin);
        TooltipMakerAPI element = panel.createUIElement(WIDTH, HEIGHT, false);

        // disabled (the dimmed look vanilla gives "Level up!" when an officer can't level) when the officer's mastery
        // and the global pool together fall short; kept current in advance()
        ButtonAPI button = element.addButton("Respec...", null, faction.getBaseUIColor(), faction.getDarkUIColor(),
                Alignment.MID, CutStyle.ALL, WIDTH, HEIGHT, 0f);
        // a tooltip shifts its first component 5 px right; undo that so the button lines up with "Retrain..."
        button.getPosition().setXAlignOffset(0f);
        button.setEnabled(oee_Mastery.canAfford(person));
        plugin.button = button;
        element.addTooltipToPrevious(new TooltipCreator() {
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
                addCostInfo(tooltip, person);
            }
        }, TooltipLocation.LEFT);
        panel.addUIElement(element).inTL(0f, 0f);
        row.addComponent(panel).inBR(RIGHT, BOTTOM);
    }

    /** The button's tooltip: what a respec does, then this officer's cost. */
    public static void addCostInfo(TooltipMakerAPI tooltip, PersonAPI person) {
        float opad = 10f;
        long cost = oee_Mastery.getRespecCost(person);
        tooltip.addPara("Change the combat skills for this officer.", 0f);
        tooltip.addPara("Costs %s", opad, Misc.getStoryOptionColor(), Misc.getWithDGS(cost) + " mastery experience");
        if (!oee_Mastery.canAfford(person)) {
            tooltip.addPara("Not enough mastery experience", Misc.getNegativeHighlightColor(), opad);
        }
    }

    /**
     * Another officer's respec can spend the global pool while the picker stays open, and only that officer's row is
     * rebuilt then, so whether this one is still affordable is checked every frame.
     */
    @Override
    public void advance(float amount) {
        if (button == null) return;
        boolean affordable = oee_Mastery.canAfford(officer.getPerson());
        if (button.isEnabled() != affordable) button.setEnabled(affordable);
    }

    @Override
    public void buttonPressed(Object buttonId) {
        if (!oee_Mastery.canAfford(officer.getPerson())) return;
        oee_RespecDialog.show(picker, officer, recreateRow);
    }
}
