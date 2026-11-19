package survivaldial;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.EngagementResultAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TextFieldAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.ValueDisplayMode;
import java.awt.Color;
import java.util.*;
import org.json.*;
import org.lwjgl.input.Keyboard;

public final class SettingsDialog implements InteractionDialogPlugin {
    public interface OnClose {void close(JSONObject profile) throws Exception;}
    private enum Screen {HOME, PRESETS, CUSTOM_PRESETS, DELETE_PRESETS, COMMON, CATEGORIES, DETAILS, REVIEW}
    private static final String[] SIZES={"Frigate","Destroyer","Cruiser","Capital"};
    private static final int PAGE_SIZE=5;
    private final boolean creation;
    private final OnClose onClose;
    private final JSONObject original;
    private JSONObject draft;
    private InteractionDialogAPI dialog;
    private String preset="Custom",category;
    private Screen screen=Screen.HOME;
    private int page;
    private SettingsModel.Entry editing;
    private Boolean toggled;

    public SettingsDialog(JSONObject profile,boolean creation,OnClose onClose) throws Exception {
        original=SettingsModel.copy(profile);draft=SettingsModel.copy(profile);this.creation=creation;this.onClose=onClose;
        for(String name:SettingsModel.PRESETS)if(equalEditable(draft,SettingsModel.preset(draft,name,creation))){preset=name;break;}
        if(preset.equals("Custom"))for(CustomPresets.Preset saved:CustomPresets.all())if(equalEditable(draft,saved.profile)){preset=saved.name;break;}
    }
    private boolean equalEditable(JSONObject a,JSONObject b) throws Exception {
        for(SettingsModel.Entry e:SettingsModel.entries)if(e.editable(creation) && !SettingsPresentation.same(a.get(e.key),b.get(e.key)))return false;
        return true;
    }
    public void init(InteractionDialogAPI dialog){this.dialog=dialog;try{render();}catch(Exception e){error(e);}}
    private void back(String label,String id){
        dialog.getOptionPanel().addOption(label,id);
        dialog.getOptionPanel().setShortcut(id,Keyboard.KEY_ESCAPE,false,false,false,true);
    }
    private List<SettingsModel.Entry> changed() throws Exception {
        List<SettingsModel.Entry> result=new ArrayList<SettingsModel.Entry>();
        for(SettingsModel.Entry e:SettingsModel.entries)if(e.supported() && !SettingsPresentation.same(original.get(e.key),draft.get(e.key)))result.add(e);
        return result;
    }
    private List<SettingsModel.Entry> list() throws Exception {
        if(screen==Screen.REVIEW)return changed();
        List<SettingsModel.Entry> result=new ArrayList<SettingsModel.Entry>();
        if(screen==Screen.COMMON){
            for(String key:SettingsPresentation.COMMON){SettingsModel.Entry e=SettingsPresentation.find(key);if(SettingsPresentation.visible(e,draft))result.add(e);}
        }else{
            for(SettingsModel.Entry e:SettingsModel.entries)if(e.category.equals(category) && SettingsPresentation.visible(e,draft))result.add(e);
        }
        return result;
    }
    private String shortValue(SettingsModel.Entry e) throws Exception {
        if(e.key.equals("ext_retirementMinCycles"))return draft.getDouble(e.key)==0?"Off":SettingsPresentation.value(draft.get(e.key))+"-"+SettingsPresentation.value(draft.get("ext_retirementMaxCycles"))+" cycles";
        if(e.type.equals("array"))return "By ship size";
        return SettingsPresentation.value(draft.get(e.key));
    }
    private void render() throws Exception {
        OptionPanelAPI options=dialog.getOptionPanel();options.clearOptions();dialog.getTextPanel().clear();
        dialog.getTextPanel().addPara("Survival Dial");
        if(editing!=null){renderEditor();return;}
        if(screen==Screen.HOME){
            dialog.getTextPanel().addPara("Choose a difficulty and you're ready to go. Customization is optional.");
            dialog.getTextPanel().addPara(preset+": "+(CustomPresets.find(preset)==null?SettingsPresentation.presetDescription(preset):"Your saved custom preset."));
            int count=changed().size();
            dialog.getTextPanel().addPara(count==0?"No pending changes.":count+" settings changed. They will take effect only when you apply them.");
            options.addOption("Difficulty: "+preset+(preset.equals("Hard")?" (default)":"")+"  [+]","presets");
            options.addOption("Customize (optional)","customize","Start with a short list of common controls. All other controls are available from there.");
            if(count>0)options.addOption("Review changes","review");
            options.addOption(creation?"Apply and continue":"Apply settings","apply");
            back(creation?"Continue without changes":"Cancel","cancel");
        }else if(screen==Screen.PRESETS){
            dialog.getTextPanel().addPara("Pick a starting point. You can change individual settings afterwards.");
            dialog.getTextPanel().addPara("Built-in presets adjust costs, scarcity, hazard damage, retirement timing, and hyperspace cloud coverage, and keep story points at 2 per level. Custom presets restore exactly what you saved. In an existing campaign, new-game-only choices are preserved.");
            for(String name:SettingsModel.PRESETS)options.addOption(name+(name.equals("Hard")?" (default)":"")+" - "+SettingsPresentation.presetDescription(name),"preset:"+name,
                "Replaces editable settings with this preset. Review the pending changes before applying. Hard also clears earlier custom values on settings hidden from the simplified menu.");
            int savedCount=CustomPresets.all().size();
            if(savedCount>0)options.addOption("Custom presets ("+savedCount+")...","custompresets","Load or manage presets you saved yourself.");
            options.addOption("Save current settings as a custom preset...","savepreset","Saves the settings currently staged in this editor. Custom presets are shared across campaigns.");
            back("Back","home");
        }else if(screen==Screen.CUSTOM_PRESETS){
            List<CustomPresets.Preset> saved=CustomPresets.all();
            dialog.getTextPanel().addPara("Custom presets");
            dialog.getTextPanel().addPara("Saved in saves/common and shared across campaigns. Select one to load its values into this editor.");
            if(saved.isEmpty())dialog.getTextPanel().addPara("No custom presets saved yet.");
            for(CustomPresets.Preset savedPreset:saved)options.addOption(savedPreset.name,"custompreset:"+savedPreset.name,
                "Loads this saved preset. In an existing campaign, new-game-only values are preserved.");
            if(!saved.isEmpty())options.addOption("Delete a custom preset...","deletepresets");
            back("Back to presets","presets");
        }else if(screen==Screen.DELETE_PRESETS){
            List<CustomPresets.Preset> saved=CustomPresets.all();
            dialog.getTextPanel().addPara("Delete a custom preset");
            dialog.getTextPanel().addPara("Choose a saved preset to delete. This cannot be undone.");
            for(CustomPresets.Preset savedPreset:saved){
                String id="deletepreset:"+savedPreset.name;
                options.addOption("Delete: "+savedPreset.name,id);
                options.addOptionConfirmation(id,"Delete custom preset \""+savedPreset.name+"\"? This cannot be undone.","Delete","Cancel");
            }
            if(saved.isEmpty())dialog.getTextPanel().addPara("No custom presets left.");
            back("Back to custom presets","custompresets");
        }else if(screen==Screen.CATEGORIES){
            dialog.getTextPanel().addPara("All settings - optional fine tuning");
            dialog.getTextPanel().addPara("You can leave all of these alone. Unchanged or inactive defaults are hidden. Earlier custom values remain available so you can review or reset them.");
            Set<String> categories=new LinkedHashSet<String>();
            for(SettingsModel.Entry e:SettingsModel.entries)if(SettingsPresentation.visible(e,draft))categories.add(e.category);
            for(String cat:categories)options.addOption(cat,"cat:"+cat);
            back("Back to common settings","customize");
        }else{
            List<SettingsModel.Entry> entries=list();
            int pages=Math.max(1,(entries.size()+PAGE_SIZE-1)/PAGE_SIZE);page=Math.max(0,Math.min(page,pages-1));
            String title=screen==Screen.COMMON?"Common settings":screen==Screen.REVIEW?"Review changes":category;
            dialog.getTextPanel().addPara(title+" - "+(page+1)+" / "+pages);
            dialog.getTextPanel().addPara(screen==Screen.REVIEW?"Before -> After. Select an item to inspect it.":"Select an item to see what it does. You only need to change the things you care about.");
            for(int i=page*PAGE_SIZE;i<Math.min(entries.size(),(page+1)*PAGE_SIZE);i++){
                SettingsModel.Entry e=entries.get(i);
                String val=screen==Screen.REVIEW?SettingsPresentation.value(original.get(e.key))+" -> "+SettingsPresentation.value(draft.get(e.key)):shortValue(e);
                options.addOption(SettingsPresentation.name(e)+": "+val+(e.editable(creation)?"":" (new game only)"),"edit:"+e.key,SettingsPresentation.tooltip(e,draft.get(e.key)));
            }
            if(page>0)options.addOption("Previous","prev");
            if(page+1<pages)options.addOption("Next","next");
            if(screen==Screen.COMMON)options.addOption("All settings...","all");
            back(screen==Screen.DETAILS?"Back to all settings":"Back",screen==Screen.DETAILS?"all":"home");
        }
    }
    private boolean retirementRange(){return editing.key.equals("ext_retirementMinCycles");}
    private int exactCount(){return retirementRange()?2:editing.type.equals("array")?4:1;}
    private String exactText() throws JSONException {
        int count=exactCount();
        StringBuilder text=new StringBuilder();
        for(int i=0;i<count;i++){
            if(i>0)text.append(", ");
            if(retirementRange())text.append(SettingsPresentation.number(draft.getDouble(i==0?"ext_retirementMinCycles":"ext_retirementMaxCycles")));
            else if(editing.type.equals("array"))text.append(SettingsPresentation.number(draft.getJSONArray(editing.key).getDouble(i)));
            else text.append(SettingsPresentation.number(draft.getDouble(editing.key)));
        }
        return text.toString();
    }
    private String exactHelp(){
        if(retirementRange())return "Enter the earliest and latest cycle, separated by a comma. 0 disables retirement. Then click Keep this change.";
        String range="Range "+SettingsPresentation.number(editing.min)+" to "+SettingsPresentation.number(editing.max);
        if(editing.type.equals("array"))return "Enter four values in ship order: frigate, destroyer, cruiser, capital. "+range+(editing.integer?"; whole numbers only.":".")+" Then click Keep this change.";
        return range+(editing.integer?"; whole numbers only.":".")+" Use a dot for decimals. Then click Keep this change.";
    }
    private void openExactDialog() throws Exception {
        final int count=exactCount();
        final String title=SettingsPresentation.name(editing);
        final String initial=exactText();
        final String help=exactHelp();
        dialog.showCustomDialog(540f,220f,new BaseCustomDialogDelegate(){
            private TextFieldAPI field;
            public void createCustomDialog(CustomPanelAPI panel,CustomDialogDelegate.CustomDialogCallback callback){
                TooltipMakerAPI ui=panel.createUIElement(500f,150f,false);
                ui.addPara(title,0f);
                ui.addPara(help,8f);
                field=ui.addTextField(460f,32f);
                field.setText(initial);
                field.setMaxChars(120);
                panel.addUIElement(ui).inTL(0f,0f);
                field.grabFocus();
            }
            public String getConfirmText(){return "Set value";}
            public String getCancelText(){return "Cancel";}
            public boolean hasCancelButton(){return true;}
            public void customDialogConfirm(){
                try{applyExact(field==null?"":field.getText(),count);}
                catch(Exception e){
                    try{render();}catch(Exception ignored){}
                    dialog.getTextPanel().addPara("Could not use value: "+e.getMessage()+". Use dots for decimals and separate multiple values with commas.");
                }
            }
            public void customDialogCancel(){}
            public CustomUIPanelPlugin getCustomPanelPlugin(){return new BaseCustomUIPanelPlugin();}
        });
    }
    private void openPresetNameDialog() throws Exception {
        dialog.showCustomDialog(540f,230f,new BaseCustomDialogDelegate(){
            private TextFieldAPI field;
            public void createCustomDialog(CustomPanelAPI panel,CustomDialogDelegate.CustomDialogCallback callback){
                TooltipMakerAPI ui=panel.createUIElement(500f,160f,false);
                ui.addPara("Save custom preset",0f);
                ui.addPara("Choose a name. The preset is saved in saves/common and is available in every campaign. Built-in preset names are reserved.",8f);
                field=ui.addTextField(460f,32f);
                field.setMaxChars(40);
                panel.addUIElement(ui).inTL(0f,0f);
                field.grabFocus();
            }
            public String getConfirmText(){return "Save preset";}
            public String getCancelText(){return "Cancel";}
            public boolean hasCancelButton(){return true;}
            public void customDialogConfirm(){
                try{
                    String name=CustomPresets.validateName(field==null?"":field.getText());
                    boolean updated=CustomPresets.add(name,draft);
                    CustomPresets.Preset saved=CustomPresets.find(name);
                    preset=saved==null?name:saved.name;screen=Screen.CUSTOM_PRESETS;page=0;
                    render();
                    dialog.getTextPanel().addPara((updated?"Updated":"Saved")+" custom preset \""+preset+"\".");
                }catch(Exception e){
                    try{render();}catch(Exception ignored){}
                    dialog.getTextPanel().addPara("Could not save preset: "+e.getMessage()+".");
                }
            }
            public void customDialogCancel(){}
            public CustomUIPanelPlugin getCustomPanelPlugin(){return new BaseCustomUIPanelPlugin();}
        });
    }
    private void applyExact(String text,int count) throws Exception {
        String trimmed=text==null?"":text.trim();
        if(trimmed.length()==0)throw new IllegalArgumentException("Enter a value");
        String[] parts=trimmed.split("\\s*[,/]\\s*|\\s+");
        if(parts.length!=count)throw new IllegalArgumentException("Enter "+count+" value"+(count==1?"":"s")+", separated by commas");
        JSONObject next=SettingsModel.copy(draft);
        double[] values=new double[count];
        for(int i=0;i<count;i++){
            double value;
            try{value=Double.parseDouble(parts[i]);}
            catch(NumberFormatException e){throw new IllegalArgumentException("'"+parts[i]+"' is not a number");}
            if(!Double.isFinite(value))throw new IllegalArgumentException("Value must be finite");
            SettingsModel.Entry entry=retirementRange() && i==1?SettingsPresentation.find("retirementMaxCycles"):editing;
            if(!entry.integer)value=Math.round(value*10000d)/10000d;
            values[i]=value;
            if(retirementRange())next.put(entry.key,value);
            else if(!editing.type.equals("array"))next.put(editing.key,value);
        }
        if(editing.type.equals("array") && !retirementRange()){
            JSONArray array=new JSONArray();
            for(double value:values)array.put(value);
            next.put(editing.key,array);
        }
        SettingsModel.validate(next);
        for(int i=0;i<count;i++)dialog.getOptionPanel().setSelectorValue("value"+i,(float)values[i]);
    }
    private void renderEditor() throws Exception {
        OptionPanelAPI options=dialog.getOptionPanel();
        dialog.getTextPanel().addPara(SettingsPresentation.name(editing));
        dialog.getTextPanel().addPara(SettingsPresentation.tooltip(editing,draft.get(editing.key)));
        if(!editing.editable(creation)){
            dialog.getTextPanel().addPara("This can be changed when starting a new Survival campaign. Existing campaign results will not be rebuilt.");
            back("Back","discardItem");return;
        }
        if(editing.type.equals("boolean"))options.addOption("Value: "+SettingsPresentation.value(draft.get(editing.key))+" (click to change)","toggle");
        else{
            int count=retirementRange()?2:editing.type.equals("array")?4:1;
            for(int i=0;i<count;i++){
                SettingsModel.Entry limits=retirementRange() && i==1?SettingsPresentation.find("retirementMaxCycles"):editing;
                String label=retirementRange()?(i==0?"Earliest cycle (0 disables retirement)":"Latest cycle"):count==4?SIZES[i]:SettingsPresentation.name(editing);
                options.addSelector(label,"value"+i,Color.CYAN,340,95,(float)limits.min,(float)limits.max,ValueDisplayMode.VALUE,SettingsPresentation.tooltip(limits,draft.get(limits.key)));
                double value=retirementRange()?draft.getDouble(limits.key):count==4?draft.getJSONArray(editing.key).getDouble(i):draft.getDouble(editing.key);
                options.setSelectorValue("value"+i,(float)value);
            }
            options.addOption("Enter exact value...","exact","Type a precise number instead of dragging the slider.");
        }
        options.addOption("Keep this change","keep");
        options.addOption("Use Survival default","reset");
        back("Back without changing this item","discardItem");
    }
    public void optionSelected(String text,Object data){
        if(!(data instanceof String))return;String id=(String)data;
        try{
            if(id.equals("home")){screen=Screen.HOME;page=0;}
            else if(id.equals("presets")){screen=Screen.PRESETS;page=0;}
            else if(id.startsWith("preset:")){preset=id.substring(7);draft=SettingsModel.preset(draft,preset,creation);screen=Screen.HOME;page=0;}
            else if(id.equals("custompresets")){screen=Screen.CUSTOM_PRESETS;page=0;}
            else if(id.startsWith("custompreset:")){String name=id.substring("custompreset:".length());draft=CustomPresets.apply(name,draft,creation);preset=name;screen=Screen.HOME;page=0;}
            else if(id.equals("savepreset")){openPresetNameDialog();return;}
            else if(id.equals("deletepresets")){screen=Screen.DELETE_PRESETS;page=0;}
            else if(id.startsWith("deletepreset:")){String name=id.substring("deletepreset:".length());CustomPresets.remove(name);if(preset.equalsIgnoreCase(name))preset="Custom";screen=CustomPresets.all().isEmpty()?Screen.CUSTOM_PRESETS:Screen.DELETE_PRESETS;page=0;}
            else if(id.equals("customize")){screen=Screen.COMMON;page=0;}
            else if(id.equals("all")){screen=Screen.CATEGORIES;page=0;}
            else if(id.equals("review")){screen=Screen.REVIEW;page=0;}
            else if(id.startsWith("cat:")){category=id.substring(4);screen=Screen.DETAILS;page=0;}
            else if(id.startsWith("edit:")){editing=SettingsPresentation.find(id.substring(5));toggled=null;}
            else if(id.equals("exact") && editing!=null && editing.editable(creation)){openExactDialog();return;}
            else if(id.equals("next"))page++;
            else if(id.equals("prev"))page--;
            else if(id.equals("toggle") && editing!=null && editing.editable(creation)){
                toggled=toggled==null?!draft.getBoolean(editing.key):!toggled;
                dialog.getOptionPanel().setOptionText("Value: "+SettingsPresentation.value(toggled)+" (click to change)","toggle");return;
            }else if((id.equals("keep") || id.equals("reset")) && editing!=null && editing.editable(creation)){
                JSONObject next=SettingsModel.copy(draft);Object value=editing.vanilla;
                if(id.equals("keep")){
                    if(editing.type.equals("boolean"))value=toggled==null?draft.get(editing.key):toggled;
                    else if(editing.type.equals("array")){JSONArray array=new JSONArray();for(int i=0;i<4;i++)array.put(slider(i));value=array;}
                    else value=slider(0);
                }
                next.put(editing.key,value);
                if(retirementRange())next.put("ext_retirementMaxCycles",id.equals("reset")?SettingsPresentation.find("retirementMaxCycles").vanilla:slider(1));
                SettingsModel.validate(next);
                if(!equalEditable(next,draft))preset="Custom";
                draft=next;editing=null;toggled=null;
            }else if(id.equals("discardItem")){editing=null;toggled=null;}
            else if(id.equals("apply") || id.equals("cancel")){
                JSONObject result=id.equals("apply")?draft:original;SettingsModel.validate(result);
                if(onClose!=null)onClose.close(SettingsModel.copy(result));
                else{
                    if(id.equals("apply")){SettingsModel.apply(result);Global.getSector().getPersistentData().put(SettingsModel.SAVE_KEY,result.toString());}
                    dialog.dismiss();
                }return;
            }
            render();
        }catch(Exception e){error(e);}
    }
    private double slider(int i){double n=dialog.getOptionPanel().getSelectorValue("value"+i);return editing.integer?Math.rint(n):Math.round(n*10000d)/10000d;}
    private void error(Exception e){Global.getLogger(SettingsDialog.class).warn("Survival Dial settings error",e);dialog.getTextPanel().addPara("Could not apply: "+e.getMessage()+". Adjust the values and try again.");}
    public void advance(float amount){
        if(editing==null || !editing.editable(creation) || editing.type.equals("boolean"))return;
        StringBuilder text=new StringBuilder("Keep: ");int count=retirementRange()?2:editing.type.equals("array")?4:1;
        for(int i=0;i<count;i++){if(i>0)text.append(" / ");text.append(SettingsPresentation.number(slider(i)));}
        dialog.getOptionPanel().setOptionText(text.toString(),"keep");
    }
    public void optionMousedOver(String text,Object data){}
    public void backFromEngagement(EngagementResultAPI result){}
    public Object getContext(){return null;}
    public Map<String,MemoryAPI> getMemoryMap(){return null;}
}
