package survivaldial;

import com.fs.starfarer.api.*;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.ui.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Uses the real API interfaces and JSON library; no game initialization or saves are needed. */
public class SettingsTest {
    private static int assertions;
    private static void check(boolean good,String message){assertions++;if(!good)throw new AssertionError(message);}
    private interface Checked {void run() throws Exception;}
    private static void rejects(Checked action,String message) throws Exception {
        boolean rejected=false;try{action.run();}catch(IllegalArgumentException expected){rejected=true;}
        check(rejected,message);
    }
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h);}
    private static void dialogText(CustomDialogDelegate delegate,String value) throws Exception {
        final String[] text={""};
        TextFieldAPI field=proxy(TextFieldAPI.class,(o,m,a)->{
            if(m.getName().equals("setText")){text[0]=(String)a[0];return null;}
            if(m.getName().equals("getText"))return text[0];
            return null;
        });
        TooltipMakerAPI tooltip=proxy(TooltipMakerAPI.class,(o,m,a)->m.getName().equals("addTextField")?field:null);
        PositionAPI position=proxy(PositionAPI.class,(o,m,a)->m.getName().equals("inTL")?o:null);
        CustomPanelAPI panel=proxy(CustomPanelAPI.class,(o,m,a)->{
            if(m.getName().equals("createUIElement"))return tooltip;
            if(m.getName().equals("addUIElement"))return position;
            return null;
        });
        delegate.createCustomDialog(panel,proxy(CustomDialogDelegate.CustomDialogCallback.class,(o,m,a)->null));
        field.setText(value);
        delegate.customDialogConfirm();
    }
    public static void main(String[] args) throws Exception {
        String json=Files.readString(Path.of(args[0]));
        JSONArray catalog=new JSONArray(json);
        JSONObject settings=new JSONObject();
        settings.put("normalTariff",0.3);
        settings.put("crewSalary",10);
        for(int i=0;i<catalog.length();i++){JSONObject row=catalog.getJSONObject(i);settings.put(row.getString("key"),row.get("default"));}
        final int[] resets={0}; final boolean[] fail={false};
        final Map<String,String> commonFiles=new HashMap<String,String>();
        Global.setSettings(proxy(SettingsAPI.class,(o,m,a)->{
            if(m.getName().equals("loadText"))return json;
            if(m.getName().equals("getSettingsJSON"))return settings;
            if(m.getName().equals("resetCached")){resets[0]++;if(fail[0]){fail[0]=false;throw new IllegalStateException("injected cache failure");}return null;}
            if(m.getName().equals("fileExistsInCommon"))return commonFiles.containsKey((String)a[0]);
            if(m.getName().equals("readTextFileFromCommon")){String text=commonFiles.get((String)a[0]);if(text==null)throw new java.io.IOException("missing common file");return text;}
            if(m.getName().equals("writeTextFileToCommon")){commonFiles.put((String)a[0],(String)a[1]);return null;}
            if(m.getName().equals("deleteTextFileFromCommon")){commonFiles.remove((String)a[0]);return null;}
            return null;
        }));
        Map<String,Object> persistent=new HashMap<String,Object>();
        final boolean[] extended={true}; final int[] hotkeys={0};
        Global.setSector(proxy(SectorAPI.class,(o,m,a)->{
            if(m.getName().equals("getPersistentData"))return persistent;
            if(m.getName().equals("isExtMode"))return extended[0];
            if(m.getName().equals("addTransientScript")){hotkeys[0]++;return null;}
            return null;
        }));
        SettingsModel.initialize();
        CustomPresets.load();
        check(CustomPresets.all().isEmpty(),"no custom presets initially");
        int beforeNormal=resets[0];extended[0]=false;new SurvivalDialPlugin().onGameLoad(false);
        check(resets[0]==beforeNormal && hotkeys[0]==0 && persistent.isEmpty(),"Normal-only session has no overrides, hotkeys, or save data");
        extended[0]=true;
        check(SettingsModel.entries.size()==144,"143 shipped settings plus the mod cloud control");
        JSONObject hard=SettingsModel.preset(SettingsModel.baseline(),"Hard",true);
        SettingsModel.validate(hard);
        for(SettingsModel.Entry e:SettingsModel.entries){
            check(e.description.length()>30,"useful tooltip for "+e.key);
            check(!e.status.equals("unverified"),"audit evidence for "+e.key);
            if(e.supported()) check(hard.get(e.key).toString().equals(e.vanilla.toString()) || (e.reference!=null),"Hard restores default "+e.key);
        }
        double lastCost=0,lastCapacity=Double.MAX_VALUE;
        for(String name:SettingsModel.PRESETS){
            JSONObject p=SettingsModel.preset(hard,name,true);SettingsModel.validate(p);
            check(p.getDouble("ext_weaponPriceMult")>lastCost,"increasing preset costs");lastCost=p.getDouble("ext_weaponPriceMult");
            check(p.getDouble("ext_fuelCapacityMult")<lastCapacity,"decreasing preset capacity");lastCapacity=p.getDouble("ext_fuelCapacityMult");
            check(p.getJSONArray("ext_shipBuyPriceMult").length()==4,"four hull sizes");
        }
        int[][] retirementWindows={{45,50},{35,40},{25,30},{20,25}};
        JSONObject customRetirement=SettingsModel.copy(hard);
        customRetirement.put("ext_retirementMinCycles",55);customRetirement.put("ext_retirementMaxCycles",65);
        for(int i=0;i<SettingsModel.PRESETS.length;i++){
            String name=SettingsModel.PRESETS[i];
            JSONObject fresh=SettingsModel.preset(customRetirement,name,true);
            check(fresh.getInt("ext_retirementMinCycles")==retirementWindows[i][0]
                && fresh.getInt("ext_retirementMaxCycles")==retirementWindows[i][1],name+" sets the requested new-campaign retirement window");
            check(fresh.getInt("ext_storyPointsPerLevel")==2,name+" keeps two story points per level");
            JSONObject existing=SettingsModel.preset(customRetirement,name,false);
            check(existing.getInt("ext_retirementMinCycles")==55 && existing.getInt("ext_retirementMaxCycles")==65,
                name+" preserves both retirement bounds in existing campaigns");
        }
        JSONObject easy=SettingsModel.preset(hard,"Easy",true);
        JSONObject draft=SettingsModel.copy(easy);draft.getJSONArray("ext_shipBuyPriceMult").put(0,11);
        check(easy.getJSONArray("ext_shipBuyPriceMult").getDouble(0)!=11,"draft does not alias original arrays");
        draft=SettingsModel.copy(hard);draft.put("ext_retirementMinCycles",50);draft.put("ext_retirementMaxCycles",60);
        JSONObject live=SettingsModel.preset(draft,"Easy",false);
        check(live.getDouble("ext_retirementMinCycles")==50,"live presets preserve creation-only settings");
        JSONObject invalid=SettingsModel.copy(hard);invalid.put("ext_retirementMinCycles",70);
        rejects(()->SettingsModel.apply(invalid),"inverted retirement bounds rejected");
        check(settings.getDouble("ext_weaponPriceMult")==10,"invalid profile does not partially apply");
        invalid.put("ext_retirementMinCycles",25);invalid.put("ext_storyPointsPerLevel",0);
        rejects(()->SettingsModel.validate(invalid),"zero story-point divisor rejected");
        invalid.put("ext_storyPointsPerLevel",2.5);
        rejects(()->SettingsModel.validate(invalid),"fractional point count rejected");
        SettingsModel.apply(easy);check(settings.getDouble("ext_weaponPriceMult")==3.25,"profile applied");
        fail[0]=true;boolean caught=false;try{SettingsModel.apply(hard);}catch(IllegalStateException e){caught=true;}
        check(caught && settings.getDouble("ext_weaponPriceMult")==3.25,"cache failure rolls back all settings");
        persistent.put(SettingsModel.SAVE_KEY,easy.toString());
        SettingsModel.restore();SettingsModel.apply(SettingsModel.current());
        check(settings.getDouble("ext_weaponPriceMult")==3.25,"save profile restored");
        persistent.clear();SettingsModel.restore();SettingsModel.apply(SettingsModel.current());
        check(settings.getDouble("ext_weaponPriceMult")==10,"different save does not inherit overrides");
        SettingsModel.apply(easy);extended[0]=false;new SurvivalDialPlugin().onGameLoad(false);
        check(settings.getDouble("ext_weaponPriceMult")==10,"normal campaign restores baseline");
        check(hotkeys[0]==0,"no settings hotkey installed for Normal campaign");
        boolean normalRejected=false;try{SettingsModel.apply(easy);}catch(IllegalStateException expected){normalRejected=true;}
        check(normalRejected,"Normal campaign refuses Survival overrides");
        check(settings.getDouble("normalTariff")==0.3 && settings.getInt("crewSalary")==10,"ordinary settings untouched across all operations");
        extended[0]=true;
        SettingsModel.apply(easy);
        SettingsDialog dialog=new SettingsDialog(hard,false,null);
        Map<String,Float> selectors=new HashMap<String,Float>();
        Map<String,String> labels=new LinkedHashMap<String,String>();
        Map<String,String> tooltips=new HashMap<String,String>();
        List<String> paragraphs=new ArrayList<String>();
        OptionPanelAPI options=proxy(OptionPanelAPI.class,(o,m,a)->{
            if(m.getName().equals("clearOptions")){labels.clear();return null;}
            if(m.getName().equals("addOption")){labels.put((String)a[1],(String)a[0]);if(a.length==3)tooltips.put((String)a[1],(String)a[2]);return null;}
            if(m.getName().equals("setSelectorValue")){selectors.put((String)a[0],(Float)a[1]);return null;}
            if(m.getName().equals("getSelectorValue"))return selectors.get(a[0]);
            return null;
        });
        TextPanelAPI text=proxy(TextPanelAPI.class,(o,m,a)->{
            if(m.getName().equals("clear"))paragraphs.clear();
            if(m.getName().equals("addPara"))paragraphs.add((String)a[0]);
            return null;
        });
        boolean[] dismissed={false};
        final CustomDialogDelegate[] custom={null};
        InteractionDialogAPI ui=proxy(InteractionDialogAPI.class,(o,m,a)->{
            if(m.getName().equals("getOptionPanel"))return options;
            if(m.getName().equals("getTextPanel"))return text;
            if(m.getName().equals("dismiss")){dismissed[0]=true;return null;}
            if(m.getName().equals("showCustomDialog")){custom[0]=(CustomDialogDelegate)a[2];return null;}
            return null;
        });
        dialog.init(ui);dialog.optionSelected(null,"preset:Insane");dialog.optionSelected(null,"cancel");
        check(dismissed[0] && settings.getDouble("ext_weaponPriceMult")==3.25,"Cancel leaves global settings untouched");
        dialog=new SettingsDialog(hard,false,null);dialog.init(ui);
        dialog.optionSelected(null,"edit:ext_weaponPriceMult");selectors.put("value0",7f);dialog.optionSelected(null,"keep");dialog.optionSelected(null,"apply");
        check(settings.getDouble("ext_weaponPriceMult")==7,"slider value applies");
        check(new JSONObject((String)persistent.get(SettingsModel.SAVE_KEY)).getDouble("ext_weaponPriceMult")==7,"slider value persists in save data");
        check(resets[0]>0,"settings caches invalidated");
        for(String name:SettingsModel.PRESETS){
            JSONObject p=SettingsModel.preset(hard,name,true);
            check(p.getJSONArray("ext_fuelUseMultCiv").getDouble(0)==1,"presets keep neutral civilian fuel use");
            check(p.getJSONArray("ext_fuelUseMult").getDouble(0)==1 && p.getJSONArray("ext_fuelUseMult").getDouble(1)==1,"presets preserve unchanged small-ship fuel use");
        }
        JSONObject oldCustom=SettingsModel.copy(hard);oldCustom.put("ext_officerSalaryMult",2);
        check(SettingsPresentation.visible(SettingsPresentation.find("officerSalaryMult"),oldCustom),"earlier neutral custom value is discoverable");
        check(!SettingsPresentation.visible(SettingsPresentation.find("officerSalaryMult"),hard),"neutral default hidden");
        check(SettingsModel.preset(oldCustom,"Easy",true).getDouble("ext_officerSalaryMult")==2,"Easy preserves earlier hidden custom values");
        check(SettingsModel.preset(oldCustom,"Hard",true).getDouble("ext_officerSalaryMult")==1,"Hard can restore hidden legacy customization");
        check(!SettingsPresentation.visible(SettingsPresentation.find("certainWeaponCooldownsBlockPhaseCloak"),hard),"unused switch omitted");
        check(!SettingsPresentation.visible(SettingsPresentation.find("respawnFleetId"),hard),"non-slider identifier omitted");
        SettingsDialog simple=new SettingsDialog(hard,true,p->{});simple.init(ui);
        check(labels.size()==4 && labels.containsKey("customize") && labels.containsKey("presets"),"home has four choices, no settings dump");
        simple.optionSelected(null,"presets");
        check(labels.size()==6 && labels.get("preset:Hard").contains("default") && labels.containsKey("savepreset"),"preset list has four presets, save option, and Back");
        simple.optionSelected(null,"home");simple.optionSelected(null,"customize");
        check(labels.containsKey("all") && labels.size()<=8,"common list stays short with optional all-settings route");
        check(labels.containsKey("edit:ext_shipBuyPriceMult"),"ship prices are easy to find");
        String tooltip=tooltips.get("edit:ext_shipBuyPriceMult");
        check(tooltip.contains("Normal:") && tooltip.contains("Survival default:") && tooltip.contains("Your value:"),"common tooltip compares all three values");
        simple.optionSelected(null,"next");simple.optionSelected(null,"next");
        check(labels.containsKey("edit:ext_retirementMinCycles"),"retirement accessible in common controls");
        simple.optionSelected(null,"edit:ext_retirementMinCycles");selectors.put("value0",50f);selectors.put("value1",60f);simple.optionSelected(null,"keep");
        simple.optionSelected(null,"home");check(labels.containsKey("review"),"pending edits expose review");
        simple.optionSelected(null,"review");
        check(labels.get("edit:ext_retirementMinCycles").contains("25 -> 50") && labels.get("edit:ext_retirementMaxCycles").contains("30 -> 60"),"retirement range changes are reviewed together");
        simple.optionSelected(null,"home");simple.optionSelected(null,"customize");simple.optionSelected(null,"all");
        check(labels.size()<=7,"advanced categories bounded");simple.optionSelected(null,"cat:Fleet and travel");
        check(labels.size()<=8,"detail pages bounded");
        simple=new SettingsDialog(hard,false,null);simple.init(ui);simple.optionSelected(null,"edit:ext_retirementMinCycles");
        check(labels.size()==1 && labels.containsKey("discardItem"),"new-game-only control opens explanation without sliders in live games");
        SettingsModel.apply(hard);
        JSONObject[] picked={null};simple=new SettingsDialog(hard,true,p->picked[0]=p);simple.init(ui);simple.optionSelected(null,"edit:ext_retirementMinCycles");
        selectors.put("value0",60f);selectors.put("value1",50f);simple.optionSelected(null,"keep");
        check(labels.containsKey("keep") && paragraphs.stream().anyMatch(p->p.contains("Could not apply")),"invalid range leaves editor open with explanation");
        simple.optionSelected(null,"discardItem");simple.optionSelected(null,"apply");
        check(picked[0].getDouble("ext_retirementMinCycles")==25,"invalid range never enters pending profile");
        String table=PresetTable.render(hard);
        check(table.contains("**Hard (default)**") && table.contains("| Weapon prices | 3.25x | 6.4x | 10x | 15x |"),"README values generated from real preset model");
        check(table.contains("| Retirement window* | 45-50 cycles | 35-40 cycles | 25-30 cycles | 20-25 cycles |"),"README shows requested retirement windows");
        check(table.contains("| Hazard damage | 1.25x | 1.6x | 2x | 3x |"),"README hazard values match presets");
        check(table.contains("| Hyperspace cloud coverage target* | 50% | 75% | 100% | 125% |"),"README cloud targets match presets");
        check(table.contains("| Commission income* |") && table.contains("| Story points per level* |") && table.contains("New-game setup only"),"all creation-only table rows marked");
        int[] cloudTargets={50,75,100,125};double[] hazards={1.25,1.6,2,3};
        JSONObject customClouds=SettingsModel.copy(hard);customClouds.put(CloudCoverage.KEY,60);
        for(int i=0;i<SettingsModel.PRESETS.length;i++){
            JSONObject fresh=SettingsModel.preset(customClouds,SettingsModel.PRESETS[i],true);
            check(fresh.getInt(CloudCoverage.KEY)==cloudTargets[i] && fresh.getDouble("ext_environmentalDamageMult")==hazards[i],"agreed cloud and hazard preset values");
            JSONObject liveClouds=SettingsModel.preset(customClouds,SettingsModel.PRESETS[i],false);
            check(liveClouds.getInt(CloudCoverage.KEY)==60,"live presets preserve cloud generation setting");
        }
        simple=new SettingsDialog(hard,false,null);simple.init(ui);simple.optionSelected(null,"edit:"+CloudCoverage.KEY);
        check(labels.size()==1 && labels.containsKey("discardItem"),"cloud slider disabled in existing campaigns");
        JSONObject[] cloudPicked={null};simple=new SettingsDialog(hard,true,p->cloudPicked[0]=p);simple.init(ui);
        simple.optionSelected(null,"edit:"+CloudCoverage.KEY);selectors.put("value0",60f);simple.optionSelected(null,"keep");simple.optionSelected(null,"apply");
        check(cloudPicked[0].getInt(CloudCoverage.KEY)==60,"custom cloud percentage accepted during new-game setup");
        customClouds.put(CloudCoverage.KEY,126);rejects(()->SettingsModel.validate(customClouds),"coverage above slider limit rejected");
        customClouds.put(CloudCoverage.KEY,-1);rejects(()->SettingsModel.validate(customClouds),"negative coverage rejected");
        SettingsModel.apply(hard);
        SettingsDialog exactDialog=new SettingsDialog(hard,false,null);exactDialog.init(ui);
        selectors.clear();custom[0]=null;
        exactDialog.optionSelected(null,"edit:ext_weaponPriceMult");
        check(labels.containsKey("exact"),"numeric setting offers exact value entry");
        exactDialog.optionSelected(null,"exact");
        check(custom[0]!=null,"exact value dialog opens");
        dialogText(custom[0],"0.75");
        check(selectors.get("value0")==0.75f,"exact single value sets selector");
        exactDialog.optionSelected(null,"keep");exactDialog.optionSelected(null,"apply");
        check(settings.getDouble("ext_weaponPriceMult")==0.75,"exact single value applies");
        check(new JSONObject((String)persistent.get(SettingsModel.SAVE_KEY)).getDouble("ext_weaponPriceMult")==0.75,"exact single value persists");

        exactDialog=new SettingsDialog(hard,false,null);exactDialog.init(ui);
        selectors.clear();custom[0]=null;
        exactDialog.optionSelected(null,"edit:ext_fuelUseMult");exactDialog.optionSelected(null,"exact");
        dialogText(custom[0],"1, 1, 1.25, 1.5");
        check(selectors.get("value0")==1f && selectors.get("value1")==1f && selectors.get("value2")==1.25f && selectors.get("value3")==1.5f,"exact hull-size values set all selectors");
        exactDialog.optionSelected(null,"keep");exactDialog.optionSelected(null,"apply");
        JSONArray fuel=settings.getJSONArray("ext_fuelUseMult");
        check(fuel.getDouble(2)==1.25 && fuel.getDouble(3)==1.5,"exact hull-size values apply");

        exactDialog=new SettingsDialog(hard,true,null);exactDialog.init(ui);
        selectors.clear();custom[0]=null;
        exactDialog.optionSelected(null,"edit:ext_retirementMinCycles");exactDialog.optionSelected(null,"exact");
        dialogText(custom[0],"40, 45");
        check(selectors.get("value0")==40f && selectors.get("value1")==45f,"exact retirement range sets both selectors");
        exactDialog.optionSelected(null,"keep");exactDialog.optionSelected(null,"apply");
        check(settings.getDouble("ext_retirementMinCycles")==40 && settings.getDouble("ext_retirementMaxCycles")==45,"exact retirement range applies");

        exactDialog=new SettingsDialog(hard,false,null);exactDialog.init(ui);
        selectors.clear();custom[0]=null;
        exactDialog.optionSelected(null,"edit:ext_weaponPriceMult");exactDialog.optionSelected(null,"exact");
        dialogText(custom[0],"not-a-number");
        check(paragraphs.stream().anyMatch(p->p.contains("Could not use value")),"invalid exact value is explained");
        check(selectors.get("value0")==10f,"invalid exact value leaves selector unchanged");
        exactDialog.optionSelected(null,"discardItem");
        JSONObject customProfile=SettingsModel.copy(hard);customProfile.put("ext_fuelPriceMult",4.25);
        CustomPresets.add("My test",customProfile);
        check(commonFiles.containsKey(CustomPresets.FILE),"custom preset file written");
        check(CustomPresets.find("my TEST")!=null,"custom preset lookup is case-insensitive");
        check(CustomPresets.all().size()==1,"one custom preset saved");
        JSONObject fromCustom=CustomPresets.apply("My test",hard,true);
        check(fromCustom.getDouble("ext_fuelPriceMult")==4.25,"custom preset applies saved values");
        JSONObject liveCustom=SettingsModel.copy(hard);liveCustom.put("ext_retirementMinCycles",10);liveCustom.put("ext_retirementMaxCycles",20);
        JSONObject appliedLive=CustomPresets.apply("My test",liveCustom,false);
        check(appliedLive.getDouble("ext_fuelPriceMult")==4.25,"custom preset applies live values");
        check(appliedLive.getDouble("ext_retirementMinCycles")==10 && appliedLive.getDouble("ext_retirementMaxCycles")==20,"custom preset preserves new-game-only values in a campaign");
        CustomPresets.load();
        check(CustomPresets.find("My test")!=null,"custom preset reloads from common file");
        rejects(()->CustomPresets.add("Hard",hard),"built-in preset name rejected");
        JSONObject replacement=SettingsModel.copy(hard);replacement.put("ext_fuelPriceMult",4.5);
        check(CustomPresets.add("my TEST",replacement),"saving an existing name updates the preset");
        check(CustomPresets.all().size()==1,"updating a preset does not create a duplicate");
        check(CustomPresets.find("My test").profile.getDouble("ext_fuelPriceMult")==4.5,"update replaces the stored profile");

        SettingsDialog presetDialog=new SettingsDialog(hard,true,null);presetDialog.init(ui);
        presetDialog.optionSelected(null,"presets");
        check(labels.containsKey("custompresets") && labels.containsKey("savepreset"),"presets menu exposes saved presets and save action");
        presetDialog.optionSelected(null,"custompresets");
        check(labels.containsKey("custompreset:My test"),"custom preset list shows the saved name");
        check(!labels.containsKey("savepreset"),"custom preset list does not duplicate the save action");
        presetDialog.optionSelected(null,"custompreset:My test");
        check(labels.get("presets").contains("My test"),"loading a custom preset labels it on the home screen");
        presetDialog.optionSelected(null,"presets");presetDialog.optionSelected(null,"custompresets");presetDialog.optionSelected(null,"deletepresets");
        check(labels.containsKey("deletepreset:My test"),"delete list shows the saved name");
        presetDialog.optionSelected(null,"deletepreset:My test");
        check(CustomPresets.find("My test")==null,"custom preset deleted");
        check(!commonFiles.get(CustomPresets.FILE).contains("My test"),"deleted preset removed from common file");

        presetDialog=new SettingsDialog(hard,true,null);presetDialog.init(ui);
        presetDialog.optionSelected(null,"presets");custom[0]=null;presetDialog.optionSelected(null,"savepreset");
        check(custom[0]!=null,"save preset dialog opens");
        dialogText(custom[0],"UI preset");
        check(CustomPresets.find("UI preset")!=null,"save preset dialog writes the preset");
        check(labels.containsKey("custompreset:UI preset"),"saved preset appears in the custom list");
        presetDialog.optionSelected(null,"presets");custom[0]=null;presetDialog.optionSelected(null,"savepreset");
        dialogText(custom[0],"ui PRESET");
        check(CustomPresets.all().size()==1,"saving an existing name updates instead of duplicating");
        check(paragraphs.stream().anyMatch(p->p.contains("Updated custom preset")),"duplicate save reports an update");
        presetDialog.optionSelected(null,"deletepresets");presetDialog.optionSelected(null,"deletepreset:UI preset");
        check(CustomPresets.all().isEmpty(),"custom preset list can be emptied");
        assertions+=CloudCoverageTest.run();
        if(args.length>1)Files.writeString(Path.of(args[1]),table);
        System.out.println("PASS: "+assertions+" assertions (catalog, presets, validation, rollback, save isolation, native dialog Apply/Cancel)");
    }
}
