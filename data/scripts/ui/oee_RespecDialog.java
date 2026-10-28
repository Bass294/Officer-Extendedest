package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.MutableCharacterStatsAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.characters.SkillSpecAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.CutStyle;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipLocation;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.mastery.oee_Mastery;
import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.lwjgl.input.Keyboard;
import patchlib.api.ref.MethodRef;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The respec menu, built to match vanilla's AI core skill picker ("Change skills" on an AI core in the officer
 * picker): the title "Select skills for ...", every pickable skill as a 72 px tile in two rows, a "Skills: x / y"
 * counter at the bottom left and Confirm/Cancel at the bottom right. Layout and tile drawing are translated from the
 * vanilla classes (0.98a-RC8: the dialog com.fs.starfarer.coreui.o0Oo..., the tile renderer
 * com.fs.starfarer.coreui.voidsuper; translations of obfuscated code), and each tile carries vanilla's own skill
 * tooltip, created by StandardTooltipV2.createSkillTooltip and attached with setTooltip, as vanilla does.
 *
 * Left-click selects or deselects a skill, right-click makes a selected skill elite or not. The officer must end up
 * with exactly as many skills and elite skills as they have now (vanilla combat skills only); Confirm stays disabled
 * until every slot, elite ones included, is filled.
 *
 * It is a modal overlay laid over the officer picker: added as the picker's last child, it gets input before the
 * officer rows, and whatever its own widgets leave unconsumed it swallows, so nothing behind it reacts.
 */
public class oee_RespecDialog extends BaseCustomUIPanelPlugin {

    // vanilla's constants
    private static final float OPAD = 10f;
    private static final float BUTTON_SIZE = 72f;
    private static final int ROWS = 2;
    private static final int MIN_COLUMNS = 7;
    private static final float CONTROL_W = 150f;
    private static final float CONTROL_H = 25f;
    private static final float TOOLTIP_W = 764f;
    private static final float TOOLTIP_H = 300f;
    private static final float BACKGROUND_DIM = 0.85f;

    private static final String CONFIRM = "oee_confirm";
    private static final String CANCEL = "oee_cancel";
    private static final Logger log = Global.getLogger(oee_RespecDialog.class);

    private final UIPanelAPI host;
    private final PersonAPI person;
    private final MethodRef recreateRow;
    private final List<String> pool;
    private final Map<String, Boolean> original;
    private final Map<String, Boolean> chosen;
    private final int maxSkills;
    private final int maxElite;
    private final long cost;
    private final Color base;
    private final Color dark;
    /** Stand-in stats holding the current selection; the skill tooltips read it, as vanilla's read its edited copy. */
    private final MutableCharacterStatsAPI preview;

    private CustomPanelAPI root;
    private UIPanelAPI box;
    private LabelAPI title;
    private LabelAPI skillsLabel;
    private ButtonAPI confirmButton;
    private boolean closed = false;

    private oee_RespecDialog(UIPanelAPI host, OfficerDataAPI officer, MethodRef recreateRow) {
        this.host = host;
        this.person = officer.getPerson();
        this.recreateRow = recreateRow;
        this.pool = oee_Mastery.getSkillPool(person);
        this.original = oee_Mastery.getCurrentSkills(person);
        this.chosen = new LinkedHashMap<String, Boolean>(original);
        this.maxSkills = original.size();
        int elite = 0;
        for (boolean e : original.values()) if (e) elite++;
        this.maxElite = elite;
        this.cost = oee_Mastery.getRespecCost(person);
        FactionAPI faction = Global.getSector().getPlayerFaction();
        this.base = faction.getBaseUIColor();
        this.dark = faction.getDarkUIColor();

        PersonAPI stand = Global.getFactory().createPerson();
        stand.setFaction(Factions.PLAYER);
        this.preview = stand.getStats();
        for (Map.Entry<String, Boolean> entry : chosen.entrySet()) {
            preview.setSkillLevel(entry.getKey(), entry.getValue() ? 2f : 1f);
        }
    }

    public static void show(UIPanelAPI host, OfficerDataAPI officer, MethodRef recreateRow) {
        oee_RespecDialog dialog = new oee_RespecDialog(host, officer, recreateRow);
        PositionAPI hostPos = host.getPosition();
        dialog.root = Global.getSettings().createCustom(hostPos.getWidth(), hostPos.getHeight(), dialog);
        host.addComponent(dialog.root).inTL(0f, 0f);
        dialog.build();
        oee_Sounds.play(oee_Sounds.BUTTON);
    }

    // ---- layout, translated from vanilla ----

    private void build() {
        int columns = MIN_COLUMNS;
        while (pool.size() > ROWS * columns) columns++;
        float width = OPAD * 2f + BUTTON_SIZE * columns + OPAD * (columns - 1);

        title = Global.getSettings().createLabel("Select skills for " + person.getNameString(), Fonts.ORBITRON_20AABOLD);
        title.setColor(base);
        title.setAlignment(Alignment.MID);
        float titleH = title.autoSizeToWidth(width - OPAD * 2f).getHeight();
        float gridBottom = OPAD + titleH + OPAD + BUTTON_SIZE * ROWS + OPAD * (ROWS - 1);
        // vanilla's height, plus a row for the cost line between the grid and the controls
        float height = gridBottom + OPAD + CONTROL_H + OPAD + CONTROL_H + OPAD + OPAD;

        CustomPanelAPI content = Global.getSettings().createCustom(width, height, null);
        content.addComponent((UIComponentAPI) title).inTMid(OPAD);

        for (int i = 0; i < pool.size(); i++) {
            int row = i / columns;
            int col = i % columns;
            String id = pool.get(i);
            CustomPanelAPI tile = Global.getSettings().createCustom(BUTTON_SIZE, BUTTON_SIZE, new SkillTile(id));
            content.addComponent(tile).inTL(OPAD + col * (BUTTON_SIZE + OPAD),
                    OPAD + titleH + OPAD + row * (BUTTON_SIZE + OPAD));
            attachSkillTooltip(tile, id);
        }

        // the cost, as vanilla's retrain dialog states its own: a plain paragraph, the cost in the story point color,
        // shifted 5 px left to cancel the tooltip's indent so it lines up with the dialog's edge
        TooltipMakerAPI costText = content.createUIElement(width - OPAD * 2f, CONTROL_H, false);
        costText.addPara("Respeccing this officer requires %s.", 5f, Misc.getStoryOptionColor(),
                Misc.getWithDGS(cost) + " mastery experience");
        content.addUIElement(costText).inTL(OPAD - 5f, gridBottom + OPAD);

        // bottom row: the counter on the left, Confirm and Cancel on the right
        CustomPanelAPI controls = Global.getSettings().createCustom(width - OPAD * 2f, CONTROL_H, new ButtonRow());
        TooltipMakerAPI el = controls.createUIElement(width - OPAD * 2f, CONTROL_H, false);
        el.setButtonFontOrbitron20();
        ButtonAPI cancel = el.addButton("Cancel", CANCEL, base, dark, Alignment.MID, CutStyle.TL_BR,
                CONTROL_W, CONTROL_H, 0f);
        confirmButton = el.addButton("Confirm", CONFIRM, base, dark, Alignment.MID, CutStyle.TL_BR,
                CONTROL_W, CONTROL_H, 0f);
        cancel.getPosition().inTR(0f, 0f);
        confirmButton.getPosition().leftOfMid((UIComponentAPI) cancel, OPAD);

        skillsLabel = Global.getSettings().createLabel("Skills: ", Fonts.INSIGNIA_LARGE);
        skillsLabel.setColor(Global.getSettings().getColor("textGrayColor"));
        skillsLabel.setAlignment(Alignment.LMID);
        skillsLabel.setHighlightOnMouseover(true);
        ((UIComponentAPI) skillsLabel).getPosition().setSize(width - OPAD * 4f - CONTROL_W * 2f, CONTROL_H);
        el.addCustom((UIComponentAPI) skillsLabel, 0f);
        ((UIComponentAPI) skillsLabel).getPosition().inTL(0f, 0f);
        updateControls();

        // the skill counter and Confirm share one hover: what a valid selection is, and the controls
        TooltipCreator selectionHelp = new SimpleTooltip(450f) {
            @Override
            public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
                // the elite parts only for officers who have elite skills to keep
                if (maxElite > 0) {
                    tooltip.addPara("All %s skills and %s elite skills must be selected.", 0f,
                            Misc.getHighlightColor(), "" + maxSkills, "" + maxElite);
                    tooltip.addPara("Left-click a skill to select or deselect it. Right-click a selected skill to "
                            + "make it elite.", 10f);
                } else {
                    tooltip.addPara("All %s skills must be selected.", 0f, Misc.getHighlightColor(), "" + maxSkills);
                    tooltip.addPara("Left-click a skill to select or deselect it.", 10f);
                }
            }
        };
        el.addTooltipTo(selectionHelp, (UIComponentAPI) skillsLabel, TooltipLocation.ABOVE);
        el.addTooltipTo(selectionHelp, (UIComponentAPI) confirmButton, TooltipLocation.ABOVE);

        controls.addUIElement(el).inTL(0f, 0f);
        content.addComponent(controls).inBL(OPAD, OPAD);

        TooltipMakerAPI holder = root.createUIElement(width, height, false);
        holder.addCustom(content, 0f);
        root.updateUIElementSizeAndMakeItProcessInput(holder);
        box = root.wrapTooltipWithBox(holder);
        root.addComponent(box).inMid();
    }

    /**
     * Vanilla's skill tooltip on a tile: StandardTooltipV2.createSkillTooltip(spec, stats, 764, 300, true, false, 0,
     * listener), set with the component's setTooltip, and placed at the dialog title when shown. Without it the tile
     * still works, so a failure is only logged.
     */
    private void attachSkillTooltip(final CustomPanelAPI tile, String skillId) {
        try {
            Class<?> tooltipClass = oee_Reflection.gameClass("com.fs.starfarer.ui.impl.StandardTooltipV2");
            Object tooltip = oee_Reflection.invokeStatic(tooltipClass, "createSkillTooltip",
                    Global.getSettings().getSkillSpec(skillId), preview, TOOLTIP_W, TOOLTIP_H, true, false, 0, null);
            oee_Reflection.invoke("setTooltip", tile, 0f, tooltip);
            oee_Reflection.invoke("setBeforeShowing", tooltip, new Runnable() {
                @Override
                public void run() {
                    oee_Reflection.invoke("setTooltipPositionRelativeToAnchor", tile, 0f, 0f, title);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Officer Extendedest: couldn't attach the skill tooltip for " + skillId, e);
        }
    }

    private void updateControls() {
        String skills = chosen.size() + " / " + maxSkills;
        Color skillsColor = chosen.size() < maxSkills ? Misc.getNegativeHighlightColor() : Misc.getHighlightColor();
        if (maxElite > 0) {
            String elites = countElite() + " / " + maxElite;
            skillsLabel.setText("Skills: " + skills + "     Elite: " + elites);
            skillsLabel.setHighlight(skills, elites);
            // red while short, like the skill count
            skillsLabel.setHighlightColors(skillsColor,
                    countElite() < maxElite ? Misc.getNegativeHighlightColor() : Misc.getStoryOptionColor());
        } else {
            // no elite skills to keep, so no "Elite: 0 / 0"
            skillsLabel.setText("Skills: " + skills);
            skillsLabel.setHighlight(skills);
            skillsLabel.setHighlightColors(skillsColor);
        }
        confirmButton.setEnabled(canConfirm());
    }

    // ---- state ----

    private int countElite() {
        int n = 0;
        for (boolean e : chosen.values()) if (e) n++;
        return n;
    }

    private int levelOf(String id) {
        Boolean elite = chosen.get(id);
        return elite == null ? 0 : elite ? 2 : 1;
    }

    private boolean canConfirm() {
        // every slot must be filled, elite ones included: a respec never loses skills or elite levels (some modded
        // officers misbehave with fewer elite skills than their maximum)
        return chosen.size() == maxSkills && countElite() == maxElite
                && !chosen.equals(original) && oee_Mastery.canAfford(person);
    }

    private void setLevel(String id, int level) {
        if (level <= 0) chosen.remove(id); else chosen.put(id, level > 1);
        preview.setSkillLevel(id, level);
        updateControls();
    }

    private void toggleSelected(String id) {
        SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
        if (chosen.containsKey(id)) {
            setLevel(id, 0);
            oee_Sounds.play(oee_Sounds.DESELECT);
        } else if (chosen.size() >= maxSkills) {
            refuse();
        } else {
            setLevel(id, 1);
            oee_Sounds.playSelect(spec);
        }
    }

    private void toggleElite(String id) {
        SkillSpecAPI spec = Global.getSettings().getSkillSpec(id);
        if (!chosen.containsKey(id)) {
            refuse();
        } else if (chosen.get(id)) {
            setLevel(id, 1);
            oee_Sounds.play(oee_Sounds.DESELECT);
        } else if (countElite() >= maxElite
                // a skill that was elite before the respec can be again, so the elite count can always be refilled
                || (!oee_Mastery.canBeElite(spec) && !Boolean.TRUE.equals(original.get(id)))) {
            refuse();
        } else {
            setLevel(id, 2);
            oee_Sounds.playElite(spec);
        }
    }

    /** Vanilla's refusal: the sound, and the skill counter flashes. */
    private void refuse() {
        oee_Sounds.play(oee_Sounds.REFUSE);
        skillsLabel.flash(0f, 0.5f);
    }

    private void confirm() {
        if (!canConfirm() || !oee_Mastery.spend(person, cost)) {
            refuse();
            return;
        }
        oee_Mastery.applySkills(person, chosen);
        oee_Sounds.play(oee_Sounds.CONFIRM);
        Global.getSector().getCampaignUI().getMessageDisplay().addMessage(
                person.getNameString() + "'s skills were reassigned", Misc.getBasePlayerColor());
        close();
        if (recreateRow != null) recreateRow.call();
    }

    private void close() {
        if (closed) return;
        closed = true;
        host.removeComponent(root);
    }

    // ---- root plugin: background and modal input ----

    @Override
    public void renderBelow(float alphaMult) {
        oee_Draw.quad(0f, 0f, Global.getSettings().getScreenWidth(), Global.getSettings().getScreenHeight(),
                Color.black, BACKGROUND_DIM * alphaMult);
        if (box != null) {
            PositionAPI b = box.getPosition();
            oee_Draw.quad(b.getX(), b.getY(), b.getWidth(), b.getHeight(), Color.black, alphaMult);
        }
        if (title != null) {
            // vanilla gives the title the faction's dark color as its background
            PositionAPI t = ((UIComponentAPI) title).getPosition();
            oee_Draw.quad(t.getX(), t.getY(), t.getWidth(), t.getHeight(), dark, alphaMult);
        }
    }

    @Override
    public void processInput(List<InputEventAPI> events) {
        for (InputEventAPI event : events) {
            if (event.isConsumed()) continue;
            if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_ESCAPE) {
                oee_Sounds.play(oee_Sounds.BUTTON);
                close();
            } else if (event.isKeyDownEvent() && event.getEventValue() == Keyboard.KEY_RETURN) {
                confirm();
            }
            // modal: nothing reaches the officer picker behind
            event.consume();
        }
    }

    /** The Confirm and Cancel buttons; a panel's buttons report to that panel's plugin. */
    private class ButtonRow extends BaseCustomUIPanelPlugin {
        @Override
        public void buttonPressed(Object buttonId) {
            if (CONFIRM.equals(buttonId)) {
                confirm();
            } else if (CANCEL.equals(buttonId)) {
                oee_Sounds.play(oee_Sounds.BUTTON);
                close();
            }
        }
    }

    private abstract static class SimpleTooltip implements TooltipCreator {
        private final float width;

        SimpleTooltip(float width) {
            this.width = width;
        }

        @Override
        public boolean isTooltipExpandable(Object tooltipParam) {
            return false;
        }

        @Override
        public float getTooltipWidth(Object tooltipParam) {
            return width;
        }
    }

    /**
     * One skill tile, drawn as vanilla's skill icon (translation of obfuscated code, com.fs.starfarer.coreui.voidsuper
     * on 0.98a-RC8): black backing; the icon, tinted dark when the skill isn't selected; a cut-corner frame in the
     * aptitude color, doubled when selected, with bright additive edges; the aptitude's elite overlay at level 2; and
     * the button glow on hover.
     */
    private class SkillTile implements CustomUIPanelPlugin {
        private final String skillId;
        private final Color color;
        private final SpriteAPI icon;
        private final SpriteAPI eliteOverlay;
        private PositionAPI pos;
        private boolean hovered;
        private float glow;

        SkillTile(String skillId) {
            this.skillId = skillId;
            SkillSpecAPI spec = Global.getSettings().getSkillSpec(skillId);
            // vanilla: the aptitude color mixed 35% toward the light highlight color
            Color light = Global.getSettings().getColor("tooltipTitleAndLightHighlightColor");
            this.color = Misc.interpolateColor(spec.getGoverningAptitudeColor(),
                    new Color(light.getRed(), light.getGreen(), light.getBlue(), 255), 0.35f);
            this.icon = oee_Draw.loadSprite(spec.getSpriteName());
            JSONObject aptitude = oee_Sounds.getAptitude(spec);
            this.eliteOverlay = aptitude != null ? oee_Draw.loadSprite(aptitude.optString("elite_overlay", null)) : null;
            if (eliteOverlay == null) {
                log.warn("Officer Extendedest: no elite overlay for skill " + skillId + " (aptitude "
                        + spec.getGoverningAptitudeId() + (aptitude == null ? ", not in aptitude_data.csv)" : ")"));
            }
        }

        @Override
        public void positionChanged(PositionAPI position) {
            pos = position;
        }

        @Override
        public void renderBelow(float alphaMult) {
        }

        @Override
        public void render(float alphaMult) {
            if (pos == null) return;
            int level = levelOf(skillId);
            float x = (int) pos.getX(), y = (int) pos.getY(), w = (int) pos.getWidth(), h = (int) pos.getHeight();
            float cx = x + w / 2f, cy = y + h / 2f;

            oee_Draw.quad(x, y, w, h, Color.black, alphaMult);
            Color tint = level <= 0 ? Misc.interpolateColor(color, Color.black, 0.5f) : Color.white;
            oee_Draw.sprite(icon, cx, cy, w - 8f, h - 8f, tint, alphaMult, false);

            // cut-corner frame: one pass when unselected, two (inset by 1 px) when selected
            float a = alphaMult * 0.7f;
            float cut = 3f;
            int passes = level <= 0 ? 1 : 2;
            float fx = x, fy = y, fw = w, fh = h;
            for (int i = 0; i < passes; i++) {
                frame(fx, fy, fw, fh, cut, a);
                if (level <= 0) a *= 0.5f;
                fx++;
                fy++;
                fw -= 2f;
                fh -= 2f;
                cut--;
            }
            if (level >= 1) {
                fancyRect(x, y, w, h, alphaMult);
                fancyRect(x + 1f, y + 1f, w - 2f, h - 2f, alphaMult);
            }
            if (level > 1) oee_Draw.sprite(eliteOverlay, cx, cy, -1f, -1f, Color.white, alphaMult, false);

            if (glow > 0f) {
                // vanilla's button glow: the icon again, additive, the overlay at a quarter of it
                float g = glow * 0.25f * alphaMult;
                oee_Draw.sprite(icon, cx, cy, w - 8f, h - 8f, Color.white, g, true);
                if (level > 1) oee_Draw.sprite(eliteOverlay, cx, cy, -1f, -1f, Color.white, g * 0.25f, true);
            }
        }

        private void frame(float x, float y, float w, float h, float cut, float alpha) {
            oee_Draw.line(x + w - cut, y, x + cut, y, color, alpha);
            oee_Draw.line(x + cut, y, x, y + cut, color, alpha);
            oee_Draw.line(x, y + cut, x, y + h - cut, color, alpha);
            oee_Draw.line(x, y + h - cut, x + cut, y + h, color, alpha);
            oee_Draw.line(x + w - cut, y + h - 1f, x + cut, y + h - 1f, color, alpha);
            oee_Draw.line(x + w - cut, y, x + w, y + cut, color, alpha);
            oee_Draw.line(x + w - 1f, y + cut, x + w - 1f, y + h - cut, color, alpha);
            oee_Draw.line(x + w, y + h - cut, x + w - cut, y + h, color, alpha);
        }

        /** Vanilla's renderFancyRect: additive edges, brightest at their middles. */
        private void fancyRect(float x, float y, float w, float h, float alpha) {
            oee_Draw.gradientLine(x, y, x, y + h, color, alpha);
            oee_Draw.gradientLine(x + w - 1f, y, x + w - 1f, y + h, color, alpha);
            oee_Draw.gradientLine(x, y, x + w, y, color, alpha);
            oee_Draw.gradientLine(x, y + h - 1f, x + w, y + h - 1f, color, alpha);
        }

        @Override
        public void advance(float amount) {
            // quick fade, like a vanilla button in quick mode
            float target = hovered ? 1f : 0f;
            float step = amount * 8f;
            glow = glow < target ? Math.min(target, glow + step) : Math.max(target, glow - step);
        }

        @Override
        public void processInput(List<InputEventAPI> events) {
            if (pos == null) return;
            for (InputEventAPI event : events) {
                if (event.isConsumed()) continue;
                if (event.isMouseMoveEvent()) {
                    hovered = pos.containsEvent(event);
                } else if (event.isLMBDownEvent() && pos.containsEvent(event)) {
                    toggleSelected(skillId);
                    event.consume();
                } else if (event.isRMBDownEvent() && pos.containsEvent(event)) {
                    toggleElite(skillId);
                    event.consume();
                }
            }
        }

        @Override
        public void buttonPressed(Object buttonId) {
        }
    }
}
