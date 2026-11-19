package survivaldial;

import com.fs.starfarer.api.Global;
import java.util.*;
import org.json.*;

/** Player-saved profiles, shared across campaigns through saves/common. */
public final class CustomPresets {
    public static final String FILE = "survival_dial_custom_presets.json";
    private static final int VERSION = 1;
    private static final int MAX_PRESETS = 30;
    private static final int MAX_NAME = 40;
    private static final List<Preset> presets = new ArrayList<Preset>();

    public static final class Preset {
        public final String name;
        public final JSONObject profile;
        Preset(String name, JSONObject profile) { this.name = name; this.profile = profile; }
    }

    public static List<Preset> all() { return new ArrayList<Preset>(presets); }

    public static Preset find(String name) {
        if (name == null) return null;
        String clean = name.trim();
        for (Preset preset : presets) if (preset.name.equalsIgnoreCase(clean)) return preset;
        return null;
    }

    public static void load() {
        presets.clear();
        try {
            if (Global.getSettings() == null || !Global.getSettings().fileExistsInCommon(FILE)) return;
            JSONObject root = new JSONObject(Global.getSettings().readTextFileFromCommon(FILE));
            JSONArray saved = root.optJSONArray("presets");
            if (saved == null) return;
            for (int i = 0; i < saved.length() && presets.size() < MAX_PRESETS; i++) {
                JSONObject item = saved.optJSONObject(i);
                if (item == null) continue;
                String name = item.optString("name", "");
                JSONObject stored = item.optJSONObject("profile");
                try {
                    String clean = cleanName(name);
                    if (find(clean) != null) continue;
                    presets.add(new Preset(clean, normalize(stored)));
                } catch (Exception e) {
                    Global.getLogger(CustomPresets.class).warn("Skipping custom preset \"" + name + "\": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            presets.clear();
            Global.getLogger(CustomPresets.class).warn("Could not load custom presets", e);
        }
    }

    public static boolean add(String name, JSONObject profile) throws Exception {
        SettingsModel.validate(profile);
        String clean = cleanName(name);
        Preset existing = find(clean);
        if (existing == null && presets.size() >= MAX_PRESETS) throw new IllegalArgumentException("At most " + MAX_PRESETS + " custom presets can be saved");
        JSONObject snapshot = new JSONObject();
        for (SettingsModel.Entry entry : SettingsModel.entries) {
            if (entry.supported() && profile.has(entry.key)) snapshot.put(entry.key, profile.get(entry.key));
        }
        Preset replacement = new Preset(existing == null ? clean : existing.name, SettingsModel.copy(snapshot));
        int index = existing == null ? presets.size() : presets.indexOf(existing);
        if (existing == null) presets.add(replacement);
        else presets.set(index, replacement);
        try {
            save();
        } catch (Exception e) {
            if (existing == null) presets.remove(presets.size() - 1);
            else presets.set(index, existing);
            throw e;
        }
        return existing != null;
    }

    public static void remove(String name) throws Exception {
        Preset preset = find(name);
        if (preset == null) throw new IllegalArgumentException("Unknown custom preset: " + name);
        int index = presets.indexOf(preset);
        presets.remove(index);
        try {
            save();
        } catch (Exception e) {
            presets.add(index, preset);
            throw e;
        }
    }

    public static JSONObject apply(String name, JSONObject current, boolean creation) throws Exception {
        Preset preset = find(name);
        if (preset == null) throw new IllegalArgumentException("Unknown custom preset: " + name);
        JSONObject result = SettingsModel.copy(current);
        for (SettingsModel.Entry entry : SettingsModel.entries) {
            if (!entry.supported()) continue;
            if (!creation && !entry.editable(false)) continue;
            if (preset.profile.has(entry.key)) result.put(entry.key, preset.profile.get(entry.key));
        }
        SettingsModel.validate(result);
        return result;
    }

    public static String validateName(String raw) { return cleanName(raw); }

    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.trim();
        if (name.length() == 0) throw new IllegalArgumentException("Enter a preset name");
        if (name.length() > MAX_NAME) throw new IllegalArgumentException("Preset names must be " + MAX_NAME + " characters or fewer");
        for (String builtin : SettingsModel.PRESETS) {
            if (builtin.equalsIgnoreCase(name)) throw new IllegalArgumentException("That name is reserved for a built-in preset");
        }
        if ("Custom".equalsIgnoreCase(name)) throw new IllegalArgumentException("That name is reserved");
        if (!name.matches("[\\p{L}\\p{N} _.,'()\\-]+")) {
            throw new IllegalArgumentException("Use letters, numbers, spaces, and basic punctuation only");
        }
        return name;
    }

    private static JSONObject normalize(JSONObject stored) throws Exception {
        if (stored == null) throw new IllegalArgumentException("The saved profile is missing");
        JSONObject result = SettingsModel.baseline();
        for (SettingsModel.Entry entry : SettingsModel.entries) {
            if (entry.supported() && stored.has(entry.key)) result.put(entry.key, stored.get(entry.key));
        }
        SettingsModel.validate(result);
        return result;
    }

    private static void save() throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", VERSION);
        JSONArray saved = new JSONArray();
        for (Preset preset : presets) {
            JSONObject item = new JSONObject();
            item.put("name", preset.name);
            item.put("profile", preset.profile);
            saved.put(item);
        }
        root.put("presets", saved);
        Global.getSettings().writeTextFileToCommon(FILE, root.toString(2));
    }

    private CustomPresets() {}
}
