package data.scripts.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.plugins.OfficerLevelupPlugin;
import com.fs.starfarer.api.util.Misc;
import data.scripts.mastery.oee_Mastery;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

import java.util.ArrayList;
import java.util.List;

/**
 * Gives experience to one officer or all of them, like Console Commands' AddXP does for the player. The experience
 * goes through vanilla's OfficerDataAPI.addXP, so whatever goes past an officer's level cap is banked as their
 * mastery experience (and the global pool gets its usual share of that). A negative amount removes mastery experience.
 * Without arguments it lists the officers with their numbers.
 */
public class oee_AddOfficerXPCommand implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        List<OfficerDataAPI> officers = new ArrayList<OfficerDataAPI>();
        for (OfficerDataAPI officer : fleet.getFleetData().getOfficersCopy()) {
            if (officer.getPerson() != null && !officer.getPerson().isPlayer()) officers.add(officer);
        }

        String[] parts = args.trim().isEmpty() ? new String[0] : args.trim().split("\\s+", 2);
        if (parts.length == 0) {
            showList(officers);
            return CommandResult.SUCCESS;
        }

        // "<amount> <target>", "<target>" (enough for the next level), or "levelup <target>"
        String amountArg = null;
        String target;
        if (CommandUtils.isLong(parts[0]) || parts[0].equalsIgnoreCase("levelup")) {
            if (parts.length < 2) return CommandResult.BAD_SYNTAX;
            if (CommandUtils.isLong(parts[0])) amountArg = parts[0];
            target = parts[1].trim();
        } else {
            target = args.trim();
        }

        List<OfficerDataAPI> targets = findTargets(officers, target);
        if (targets == null) return CommandResult.ERROR;

        for (OfficerDataAPI officer : targets) {
            apply(officer, amountArg == null ? null : Long.parseLong(amountArg));
        }
        return CommandResult.SUCCESS;
    }

    /** null amount: enough experience for the officer's next level, or one level's worth past the cap. */
    private static void apply(OfficerDataAPI officer, Long amount) {
        PersonAPI person = officer.getPerson();
        String name = person.getNameString();
        if (Misc.isMercenary(person)) {
            Console.showMessage(name + " is a mercenary and can't gain experience.");
            return;
        }
        if (amount == null) amount = getLevelUpAmount(person);

        if (amount < 0) {
            long removed = Math.min(-amount, oee_Mastery.getOfficerXP(person));
            oee_Mastery.addOfficerXP(person, -removed);
            Console.showMessage("Removed " + Misc.getWithDGS(removed) + " mastery experience from " + name + ".");
            return;
        }

        long xpBefore = person.getStats().getXP();
        long masteryBefore = oee_Mastery.getOfficerXP(person);
        officer.addXP(amount);
        long gained = person.getStats().getXP() - xpBefore;
        long banked = oee_Mastery.getOfficerXP(person) - masteryBefore;
        Console.showMessage("Added " + Misc.getWithDGS(amount) + " experience to " + name + ": "
                + Misc.getWithDGS(gained) + " experience, " + Misc.getWithDGS(banked) + " mastery experience"
                + (officer.canLevelUp() ? " (ready to level up)." : "."));
    }

    private static long getLevelUpAmount(PersonAPI person) {
        OfficerLevelupPlugin plugin = oee_Mastery.getLevelPlugin();
        int level = person.getStats().getLevel();
        int maxLevel = plugin.getMaxLevel(person);
        if (level >= maxLevel) {
            return Math.max(1L, plugin.getXPForLevel(maxLevel) - plugin.getXPForLevel(maxLevel - 1));
        }
        return Math.max(0L, plugin.getXPForLevel(level + 1) - person.getStats().getXP());
    }

    /** "all", a number from the list, or part of a name that matches exactly one officer. Null after an error. */
    private static List<OfficerDataAPI> findTargets(List<OfficerDataAPI> officers, String target) {
        List<OfficerDataAPI> result = new ArrayList<OfficerDataAPI>();
        if (target.equalsIgnoreCase("all")) {
            result.addAll(officers);
            if (result.isEmpty()) Console.showMessage("You have no officers.");
            return result;
        }
        if (CommandUtils.isInteger(target)) {
            int index = Integer.parseInt(target) - 1;
            if (index < 0 || index >= officers.size()) {
                Console.showMessage("No officer number " + target + ". Run oee_AddOfficerXP without arguments for the list.");
                return null;
            }
            result.add(officers.get(index));
            return result;
        }
        String lower = target.toLowerCase();
        for (OfficerDataAPI officer : officers) {
            if (officer.getPerson().getNameString().toLowerCase().contains(lower)) result.add(officer);
        }
        if (result.size() != 1) {
            Console.showMessage(result.isEmpty()
                    ? "No officer named \"" + target + "\"."
                    : "\"" + target + "\" matches " + result.size() + " officers; use their number instead.");
            showList(officers);
            return null;
        }
        return result;
    }

    private static void showList(List<OfficerDataAPI> officers) {
        if (officers.isEmpty()) {
            Console.showMessage("You have no officers.");
            return;
        }
        OfficerLevelupPlugin plugin = oee_Mastery.getLevelPlugin();
        StringBuilder sb = new StringBuilder("Officers:\n");
        for (int i = 0; i < officers.size(); i++) {
            PersonAPI person = officers.get(i).getPerson();
            sb.append(String.format("  %d. %s - level %d/%d, %s experience, %s mastery experience%s%n",
                    i + 1, person.getNameString(), person.getStats().getLevel(), plugin.getMaxLevel(person),
                    Misc.getWithDGS(person.getStats().getXP()), Misc.getWithDGS(oee_Mastery.getOfficerXP(person)),
                    Misc.isMercenary(person) ? " (mercenary)" : ""));
        }
        sb.append("Global mastery experience: ").append(Misc.getWithDGS(oee_Mastery.getGlobalXP()));
        Console.showMessage(sb.toString());
    }
}
