package data.scripts.console;

import com.fs.starfarer.api.util.Misc;
import data.scripts.mastery.oee_Mastery;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

/** Adds to (or with a negative amount, removes from) the global mastery pool. Without arguments, shows the pool. */
public class oee_AddGlobalMasteryXPCommand implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
            return CommandResult.WRONG_CONTEXT;
        }
        String arg = args.trim();
        if (arg.isEmpty()) {
            Console.showMessage("Global mastery experience: " + Misc.getWithDGS(oee_Mastery.getGlobalXP()));
            return CommandResult.SUCCESS;
        }
        if (!CommandUtils.isLong(arg)) {
            Console.showMessage("Error: experience must be a whole number!");
            return CommandResult.BAD_SYNTAX;
        }
        long before = oee_Mastery.getGlobalXP();
        oee_Mastery.addGlobalXP(Long.parseLong(arg));
        long after = oee_Mastery.getGlobalXP();
        Console.showMessage((after >= before ? "Added " : "Removed ") + Misc.getWithDGS(Math.abs(after - before))
                + " global mastery experience (now " + Misc.getWithDGS(after) + ").");
        return CommandResult.SUCCESS;
    }
}
