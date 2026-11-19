package survivaldial;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import org.lwjgl.input.Keyboard;
import org.json.JSONObject;

public class SurvivalDialPlugin extends BaseModPlugin {
    @Override public void onApplicationLoad() throws Exception { SettingsModel.initialize(); CustomPresets.load(); }
    @Override public void onNewGame() {
        try {
            JSONObject profile=SettingsModel.pending==null ? SettingsModel.baseline() : SettingsModel.copy(SettingsModel.pending);
            SettingsModel.pending=null;
            if(Global.getSector().isExtMode()) {
                SettingsModel.apply(profile);
                Global.getSector().getPersistentData().put(SettingsModel.SAVE_KEY,profile.toString());
            } else SettingsModel.restore();
        } catch(Exception e) { throw new RuntimeException("Survival Dial: new-game settings failed",e); }
    }
    @Override public void onNewGameAfterProcGen() {
        try {
            if(Global.getSector().isExtMode()) CloudCoverage.applyNewCampaign(SettingsModel.current());
        } catch(Exception e) { throw new RuntimeException("Survival Dial: cloud generation failed",e); }
    }
    @Override public void onGameLoad(boolean newGame) {
        try {
            SettingsModel.pending=null;
            SettingsModel.restore();
            Global.getSector().removeTransientScriptsOfClass(Hotkey.class);
            if(Global.getSector().isExtMode()) {
                SettingsModel.apply(SettingsModel.current());
                Global.getSector().addTransientScript(new Hotkey());
            }
        } catch(Exception e) { throw new RuntimeException("Survival Dial: invalid saved settings; see cause",e); }
    }
    public static class Hotkey implements EveryFrameScript {
        private boolean wasDown;
        public boolean isDone(){ return false; }
        public boolean runWhilePaused(){ return true; }
        public void advance(float amount) {
            if(!Keyboard.isCreated()) return;
            boolean down=Keyboard.isKeyDown(Keyboard.KEY_F8) && (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL));
            boolean pressed=down && !wasDown; wasDown=down;
            if(!pressed || !Global.getSector().isExtMode() || Global.getSector().isInNewGameAdvance()) return;
            CampaignUIAPI ui=Global.getSector().getCampaignUI();
            if(ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab()!=null) return;
            try { ui.showInteractionDialog(new SettingsDialog(SettingsModel.current(),false,null),Global.getSector().getPlayerFleet()); }
            catch(Exception e) { Global.getLogger(Hotkey.class).error("Could not open Survival Dial",e); ui.addMessage("Survival Dial could not open: "+e.getMessage()); }
        }
    }
}
