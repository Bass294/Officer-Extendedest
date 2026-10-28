package data.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

public class oee_ModPlugin extends BaseModPlugin {

    private static final Logger log = Logger.getLogger(oee_ModPlugin.class);

    @Override
    public void onApplicationLoad() {
        oee_Settings.init();
        log.info("Officer Extendedest loaded");
    }

    @Override
    public void onGameLoad(boolean newGame) {
        // an earlier version had an officer limit setting that put a flat bonus on the player's officer count under
        // the mod id; the setting is gone, so clear any bonus a save still carries
        Global.getSector().getPlayerStats().getOfficerNumber().unmodify(oee_Settings.MOD_ID);
    }
}
