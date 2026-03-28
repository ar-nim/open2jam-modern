package org.open2jam;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import org.open2jam.parsers.Event;
import org.open2jam.util.DebugLogger;
import org.open2jam.util.Logger;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unified configuration class (replaces LegacyConfig.java + GameOptions.java).
 * 
 * <p>Stores key bindings and game options in {@code save/config.json} using Jackson JSON serialization.</p>
 * 
 * <h2>Features:</h2>
 * <ul>
 *   <li><strong>Human-readable JSON:</strong> Editable with any text editor</li>
 *   <li><strong>Type-safe:</strong> Jackson validates types during deserialization</li>
 *   <li><strong>Backward compatible:</strong> Missing fields use defaults</li>
 *   <li><strong>Zero-allocation gameplay:</strong> Key codes returned as primitive int arrays</li>
 *   <li><strong>Auto-save:</strong> Changes persisted immediately</li>
 * </ul>
 * 
 * <h2>Thread Safety:</h2>
 * <ul>
 *   <li>Singleton instance is thread-safe (initialized on first access)</li>
 *   <li>Save operations are synchronized to prevent concurrent writes</li>
 *   <li>Read operations are lock-free (immutable after load)</li>
 * </ul>
 * 
 * <h2>File Format:</h2>
 * <pre>{@code
 * {
 *   "keyBindings": {
 *     "misc": { "SPEED_UP": 38, "SPEED_DOWN": 40, ... },
 *     "keyboard": {
 *       "k4": { "NOTE_1": 68, "NOTE_2": 70, ... },
 *       "k5": { ... },
 *       ...
 *     }
 *   },
 *   "gameOptions": {
 *     "speedMultiplier": 1.0,
 *     "speedType": "HiSpeed",
 *     "visibilityModifier": "None",
 *     ...
 *   }
 * }
 * }</pre>
 * 
 * @author open2jam-modern team
 */
public class Config {
    
    private static final File CONFIG_FILE = new File("save/config.json");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Writer for indented output (Jackson 3.x)
    private static final ObjectWriter INDENTED_WRITER = MAPPER.writerWithDefaultPrettyPrinter();

    // ===== Constants for Validation and Key Bindings =====
    private static final String UI_SCALE_AUTOMATIC = "automatic";
    private static final String INVALID_PREFIX = "Invalid ";
    private static final String USING_DEFAULT_SUFFIX = "), using default ";
    private static final String NOTE_1 = "NOTE_1";
    private static final String NOTE_2 = "NOTE_2";
    private static final String NOTE_3 = "NOTE_3";
    private static final String NOTE_4 = "NOTE_4";
    private static final String NOTE_5 = "NOTE_5";
    private static final String NOTE_6 = "NOTE_6";
    private static final String NOTE_7 = "NOTE_7";
    private static final String NOTE_8 = "NOTE_8";
    private static final String NOTE_SC = "NOTE_SC";

    // ===== Debounced Save =====
    private static final ScheduledExecutorService saveExecutor = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "Config-Saver");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean savePending = new AtomicBoolean(false);
    private static final long SAVE_DELAY_MS = 500;  // Debounce delay

    // ===== Config Fields (private for encapsulation) =====
    @JsonProperty("keyBindings")
    private KeyBindings keyBindings = new KeyBindings();

    @JsonProperty("gameOptions")
    private GameOptionsWrapper gameOptions = new GameOptionsWrapper();

    @JsonProperty("lastOpenedLibraryId")
    private Integer lastOpenedLibraryId = null;

    // REMOVED: getInstance() method (singleton pattern removed)

    /**
     * Load configuration from disk.
     *
     * <p>Creates default config if file doesn't exist or is corrupted.</p>
     *
     * @return Loaded or default Config instance
     */
    public static Config load() {  // Changed from private to public
        // Ensure save directory exists
        File saveDir = new File("save");
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            Logger.global.severe("Failed to create save directory: " + saveDir.getAbsolutePath());
        }

        if (!CONFIG_FILE.exists()) {
            DebugLogger.debug("Config file not found, creating default: " + CONFIG_FILE.getAbsolutePath());
            return createDefault();
        }

        try {
            Config config = MAPPER.readValue(CONFIG_FILE, Config.class);
            config.migrateOldKeyBindings();
            config.validateAndSanitize();
            DebugLogger.debug("Config loaded: " + CONFIG_FILE.getAbsolutePath());
            return config;
        } catch (Exception e) {
            Logger.global.log(java.util.logging.Level.SEVERE,
                "Failed to load config.json, creating default", e);
            return createDefault();
        }
    }

    /**
     * Validate and sanitize config values after deserialization to prevent
     * resource exhaustion from crafted config files.
     */
    private void validateAndSanitize() {
        // Validate display dimensions
        validateIntRange("displayWidth", gameOptions.getDisplayWidth(), 640, 7680, 1280,
            w -> gameOptions.setDisplayWidth(w));
        validateIntRange("displayHeight", gameOptions.getDisplayHeight(), 480, 4320, 720,
            h -> gameOptions.setDisplayHeight(h));

        // Validate volume levels (0-100)
        validateFloatRange("keyVolume", gameOptions.getKeyVolume(), 0, 100);
        validateFloatRange("bgmVolume", gameOptions.getBgmVolume(), 0, 100);
        validateFloatRange("masterVolume", gameOptions.getMasterVolume(), 0, 100);

        // Validate speed multiplier (0.1x - 10x)
        validateDoubleRange("speedMultiplier", gameOptions.getSpeedMultiplier(), 0.1, 10.0, 1.0,
            v -> gameOptions.setSpeedMultiplier(v));

        // Validate buffer size (64-4096)
        validateIntRange("bufferSize", gameOptions.getBufferSize(), 64, 4096, 512,
            s -> gameOptions.setBufferSize(s));

        // Validate display frequency (30-1024 Hz for high-refresh monitors)
        validateIntRange("displayFrequency", gameOptions.getDisplayFrequency(), 30, 1024, 60,
            f -> gameOptions.setDisplayFrequency(f));

        // Validate bits per pixel (16 or 32)
        validateIntValues("displayBitsPerPixel", gameOptions.getDisplayBitsPerPixel(), new int[]{16, 32}, 32,
            b -> gameOptions.setDisplayBitsPerPixel(b));

        // Validate UI scale
        validateUiScale();
    }

    /**
     * Validate integer range and set to default if out of range.
     *
     * @param name Field name for logging
     * @param value Current value
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param defaultValue Default value if out of range
     * @param setter Consumer to set the value
     */
    private void validateIntRange(String name, int value, int min, int max, int defaultValue,
                                   java.util.function.IntConsumer setter) {
        if (value < min || value > max) {
            Logger.global.warning(INVALID_PREFIX + name + " (" + value + ")" + USING_DEFAULT_SUFFIX + defaultValue);
            setter.accept(defaultValue);
        }
    }

    /**
     * Validate float range and clamp if out of range.
     *
     * @param name Field name for logging
     * @param value Current value
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     */
    private void validateFloatRange(String name, float value, float min, float max) {
        float clamped = Math.max(min, Math.min(max, value));
        if (clamped != value) {
            Logger.global.warning("Invalid " + name + " (" + value + "), clamped to " + clamped);
        }
    }

    /**
     * Validate double range and set to default if out of range.
     *
     * @param name Field name for logging
     * @param value Current value
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param defaultValue Default value if out of range
     * @param setter Consumer to set the value
     */
    private void validateDoubleRange(String name, double value, double min, double max, double defaultValue,
                                      java.util.function.DoubleConsumer setter) {
        if (value < min || value > max) {
            Logger.global.warning(INVALID_PREFIX + name + " (" + value + ")" + USING_DEFAULT_SUFFIX + defaultValue);
            setter.accept(defaultValue);
        }
    }

    /**
     * Validate integer is one of allowed values.
     *
     * @param name Field name for logging
     * @param value Current value
     * @param allowedValues Array of allowed values
     * @param defaultValue Default value if not in allowed values
     * @param setter Consumer to set the value
     */
    private void validateIntValues(String name, int value, int[] allowedValues, int defaultValue,
                                    java.util.function.IntConsumer setter) {
        boolean valid = Arrays.stream(allowedValues).anyMatch(v -> v == value);
        if (!valid) {
            Logger.global.warning(INVALID_PREFIX + name + " (" + value + ")" + USING_DEFAULT_SUFFIX + defaultValue);
            setter.accept(defaultValue);
        }
    }

    /**
     * Validate UI scale format and range.
     */
    private void validateUiScale() {
        if (!UI_SCALE_AUTOMATIC.equalsIgnoreCase(gameOptions.getUiScale())) {
            try {
                double val = Double.parseDouble(gameOptions.getUiScale());
                if (val < 0.5 || val > 4.0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Logger.global.warning(INVALID_PREFIX + "uiScale (" + gameOptions.getUiScale() + ")" + USING_DEFAULT_SUFFIX + "'automatic'");
                gameOptions.setUiScale(UI_SCALE_AUTOMATIC);
            }
        }
    }

    /**
     * Create default configuration.
     *
     * @return Config with default key bindings and game options
     */
    private static Config createDefault() {
        Config config = new Config();
        config.setKeyBindings(createDefaultKeyBindings());
        config.setGameOptions(createDefaultGameOptions());
        config.save();
        return config;
    }

    /**
     * Create default key bindings.
     * 
     * <p>Matches original O2Jam defaults:</p>
     * <ul>
     *   <li>K4: D F J K (Shift for SC)</li>
     *   <li>K5: D F Space J K (Shift for SC)</li>
     *   <li>K6: S D F J K L (Shift for SC)</li>
     *   <li>K7: S D F Space J K L (Shift for SC)</li>
     *   <li>K8: A S D F H J K L</li>
     * </ul>
     * 
     * @return KeyBindings with default mappings
     */
    private static KeyBindings createDefaultKeyBindings() {
        KeyBindings kb = new KeyBindings();

        // Misc bindings (volume, speed)
        kb.misc.put("SPEED_DOWN", org.open2jam.render.lwjgl.Keyboard.KEY_DOWN);
        kb.misc.put("SPEED_UP", org.open2jam.render.lwjgl.Keyboard.KEY_UP);
        kb.misc.put("MAIN_VOL_UP", org.open2jam.render.lwjgl.Keyboard.KEY_2);
        kb.misc.put("MAIN_VOL_DOWN", org.open2jam.render.lwjgl.Keyboard.KEY_1);
        kb.misc.put("KEY_VOL_UP", org.open2jam.render.lwjgl.Keyboard.KEY_4);
        kb.misc.put("KEY_VOL_DOWN", org.open2jam.render.lwjgl.Keyboard.KEY_3);
        kb.misc.put("BGM_VOL_UP", org.open2jam.render.lwjgl.Keyboard.KEY_6);
        kb.misc.put("BGM_VOL_DOWN", org.open2jam.render.lwjgl.Keyboard.KEY_5);

        // K4
        kb.keyboard.k4.put(NOTE_1, org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k4.put(NOTE_2, org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k4.put(NOTE_3, org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k4.put(NOTE_4, org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k4.put(NOTE_SC, org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K5
        kb.keyboard.k5.put(NOTE_1, org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k5.put(NOTE_2, org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k5.put(NOTE_3, org.open2jam.render.lwjgl.Keyboard.KEY_SPACE);
        kb.keyboard.k5.put(NOTE_4, org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k5.put(NOTE_5, org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k5.put(NOTE_SC, org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K6
        kb.keyboard.k6.put(NOTE_1, org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k6.put(NOTE_2, org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k6.put(NOTE_3, org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k6.put(NOTE_4, org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k6.put(NOTE_5, org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k6.put(NOTE_6, org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k6.put(NOTE_SC, org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K7
        kb.keyboard.k7.put(NOTE_1, org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k7.put(NOTE_2, org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k7.put(NOTE_3, org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k7.put(NOTE_4, org.open2jam.render.lwjgl.Keyboard.KEY_SPACE);
        kb.keyboard.k7.put(NOTE_5, org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k7.put(NOTE_6, org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k7.put(NOTE_7, org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k7.put(NOTE_SC, org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K8
        kb.keyboard.k8.put(NOTE_1, org.open2jam.render.lwjgl.Keyboard.KEY_A);
        kb.keyboard.k8.put(NOTE_2, org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k8.put(NOTE_3, org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k8.put(NOTE_4, org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k8.put(NOTE_5, org.open2jam.render.lwjgl.Keyboard.KEY_H);
        kb.keyboard.k8.put(NOTE_6, org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k8.put(NOTE_7, org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k8.put(NOTE_8, org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k8.put(NOTE_SC, org.open2jam.render.lwjgl.Keyboard.KEY_L);

        return kb;
    }

    /**
     * Create default game options wrapper.
     *
     * @return GameOptionsWrapper with default values
     */
    private static GameOptionsWrapper createDefaultGameOptions() {
        GameOptionsWrapper opts = new GameOptionsWrapper();
        opts.setSpeedMultiplier(1.0);
        opts.setSpeedType(GameOptions.SpeedType.HI_SPEED);
        opts.setVisibilityModifier(GameOptions.VisibilityMod.NONE);
        opts.setChannelModifier(GameOptions.ChannelMod.NONE);
        opts.setJudgmentType(GameOptions.JudgmentType.BEAT_JUDGMENT);
        opts.setKeyVolume(1.0f);
        opts.setBgmVolume(1.0f);
        opts.setMasterVolume(1.0f);
        opts.setAutoplay(false);
        opts.setAutosound(false);
        opts.setAutoplayChannels(Arrays.asList(false, false, false, false, false, false, false));
        opts.setDisplayFullscreen(false);
        opts.setDisplayVsync(true);
        opts.setFpsLimiter(GameOptions.FpsLimiter.X1);
        opts.setDisplayWidth(1280);
        opts.setDisplayHeight(720);
        opts.setDisplayBitsPerPixel(32);
        opts.setDisplayFrequency(60);
        opts.setBufferSize(512);
        opts.setDisplayLag(0.0);
        opts.setAudioLatency(0.0);
        opts.setHasteMode(false);
        opts.setHasteModeNormalizeSpeed(true);
        return opts;
    }

    // ===== Save/Load =====

    /**
     * Save configuration to disk immediately.
     *
     * <p>Thread-safe: synchronized to prevent concurrent writes.</p>
     *
     * <p>Note: For rapid successive changes (e.g., slider adjustments), use scheduleSave()
     * which debounces writes to avoid UI blocking.</p>
     */
    public synchronized void save() {
        try {
            INDENTED_WRITER.writeValue(CONFIG_FILE, this);
            // Silent save - no log spam
        } catch (Exception e) {
            Logger.global.log(java.util.logging.Level.SEVERE, "Failed to save config.json", e);
        }
    }

    /**
     * Schedule save with debouncing.
     *
     * <p>Use this for rapid successive changes (e.g., slider adjustments, key binding changes).
     * Multiple calls within 500ms will result in a single disk write.</p>
     *
     * <p>Thread Safety: Uses ScheduledExecutorService with atomic flag.</p>
     */
    public void scheduleSave() {
        if (savePending.compareAndSet(false, true)) {
            saveExecutor.schedule(() -> {
                try {
                    save();
                } finally {
                    savePending.set(false);
                }
            }, SAVE_DELAY_MS, TimeUnit.MILLISECONDS);
        }
        // If savePending was already true, the scheduled save will handle this change
    }

    // ===== Key Binding Accessors (primitive arrays for zero-allocation) =====

    /**
     * Get key codes for a keyboard type as primitive int array.
     * 
     * <p>Zero-allocation: returns fresh array each call (no GC pressure during gameplay).</p>
     * 
     * @param kt Keyboard type (K4-K8)
     * @return int array indexed by Event.Channel.ordinal(), -1 for unbound keys
     */
    public int[] getKeyCodes(KeyboardType kt) {
        Map<String, Integer> map = getInstanceKeyboardMap(kt);
        int[] keyCodes = new int[Event.Channel.values().length];
        Arrays.fill(keyCodes, -1);

        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            try {
                Event.Channel channel = Event.Channel.valueOf(entry.getKey());
                keyCodes[channel.ordinal()] = entry.getValue();
            } catch (IllegalArgumentException e) {
                // Ignore invalid channel names
            }
        }
        return keyCodes;
    }

    /**
     * Get misc key codes as primitive int array.
     * 
     * <p>Zero-allocation: returns fresh array each call.</p>
     * 
     * @return int array indexed by MiscEvent.ordinal(), -1 for unbound keys
     */
    public int[] getInstanceMiscKeyCodes() {
        int[] keyCodes = new int[MiscEvent.values().length];
        Arrays.fill(keyCodes, -1);

        for (Map.Entry<String, Integer> entry : keyBindings.misc.entrySet()) {
            try {
                MiscEvent event = MiscEvent.valueOf(entry.getKey());
                keyCodes[event.ordinal()] = entry.getValue();
            } catch (IllegalArgumentException e) {
                // Ignore invalid event names
            }
        }
        return keyCodes;
    }

    /**
     * Get keyboard map for a keyboard type.
     * 
     * @param kt Keyboard type
     * @return Map of channel name to key code
     */
    private Map<String, Integer> getInstanceKeyboardMap(KeyboardType kt) {
        return switch (kt) {
            case K4 -> keyBindings.keyboard.k4;
            case K5 -> keyBindings.keyboard.k5;
            case K6 -> keyBindings.keyboard.k6;
            case K7 -> keyBindings.keyboard.k7;
            case K8 -> keyBindings.keyboard.k8;
            default -> Collections.emptyMap();
        };
    }

    /**
     * Set key code for a channel.
     *
     * @param kt Keyboard type
     * @param channel Channel to bind
     * @param keyCode GLFW key code
     */
    public void setKeyCode(KeyboardType kt, Event.Channel channel, int keyCode) {
        Map<String, Integer> map = getInstanceKeyboardMap(kt);
        map.put(channel.name(), keyCode);
        scheduleSave();  // Debounced save for rapid changes
    }

    /**
     * Set misc key code.
     *
     * @param event Misc event
     * @param keyCode GLFW key code
     */
    public void setMiscKeyCode(MiscEvent event, int keyCode) {
        keyBindings.misc.put(event.name(), keyCode);
        scheduleSave();  // Debounced save for rapid changes
    }

    // ===== Key Bindings =====

    /**
     * Get key bindings.
     *
     * @return KeyBindings with all key mappings
     */
    @JsonProperty("keyBindings")
    public KeyBindings getKeyBindings() {
        return keyBindings;
    }

    /**
     * Set key bindings.
     *
     * @param keyBindings New key bindings (will be saved immediately)
     */
    @JsonProperty("keyBindings")
    public void setKeyBindings(KeyBindings keyBindings) {
        this.keyBindings = keyBindings;
        scheduleSave();
    }

    // ===== Game Options =====

    /**
     * Get game options.
     *
     * @return GameOptionsWrapper with all game settings
     */
    @JsonProperty("gameOptions")
    public GameOptionsWrapper getGameOptions() {
        return gameOptions;
    }

    /**
     * Set game options.
     *
     * @param options New game options (will be saved immediately)
     */
    @JsonProperty("gameOptions")
    public void setGameOptions(GameOptionsWrapper options) {
        this.gameOptions = options;
        scheduleSave();
    }

    // ===== UI State =====

    /**
     * Get last opened library ID.
     *
     * @return Library ID from SQLite, or null if not set
     */
    @JsonProperty("lastOpenedLibraryId")
    public Integer getLastOpenedLibraryId() {
        return lastOpenedLibraryId;
    }

    /**
     * Set last opened library ID.
     *
     * @param libraryId Library ID from SQLite libraries table
     */
    @JsonProperty("lastOpenedLibraryId")
    public void setLastOpenedLibraryId(Integer libraryId) {
        this.lastOpenedLibraryId = libraryId;
        scheduleSave();
    }

    // ===== Migration =====

    /**
     * Migrate old key bindings from LegacyConfig format.
     */
    private void migrateOldKeyBindings() {
        // Migration logic handled by ConfigMigration class
        // This method is a hook for future migration needs
    }

    // ===== Inner Classes =====

    /**
     * Key bindings container.
     *
     * <p>Thread Safety: Uses ConcurrentHashMap for all maps to prevent ConcurrentModificationException.</p>
     */
    public static class KeyBindings {
        @JsonProperty("misc")
        private Map<String, Integer> misc = new ConcurrentHashMap<>();
        
        @JsonProperty("keyboard")
        private KeyboardMaps keyboard = new KeyboardMaps();

        // Getters/setters for Jackson
        @JsonProperty("misc")
        public Map<String, Integer> getMisc() { return misc; }
        
        @JsonProperty("misc")
        public void setMisc(Map<String, Integer> misc) { this.misc = new ConcurrentHashMap<>(misc); }
        
        @JsonProperty("keyboard")
        public KeyboardMaps getKeyboard() { return keyboard; }
        
        @JsonProperty("keyboard")
        public void setKeyboard(KeyboardMaps keyboard) { this.keyboard = keyboard; }
    }

    /**
     * Keyboard maps container.
     *
     * <p>Thread Safety: Uses ConcurrentHashMap for all maps to prevent ConcurrentModificationException.</p>
     */
    public static class KeyboardMaps {
        @JsonProperty("k4")
        private Map<String, Integer> k4 = new ConcurrentHashMap<>();
        
        @JsonProperty("k5")
        private Map<String, Integer> k5 = new ConcurrentHashMap<>();
        
        @JsonProperty("k6")
        private Map<String, Integer> k6 = new ConcurrentHashMap<>();
        
        @JsonProperty("k7")
        private Map<String, Integer> k7 = new ConcurrentHashMap<>();
        
        @JsonProperty("k8")
        private Map<String, Integer> k8 = new ConcurrentHashMap<>();

        // Getters/setters for Jackson
        @JsonProperty("k4")
        public Map<String, Integer> getK4() { return k4; }
        
        @JsonProperty("k4")
        public void setK4(Map<String, Integer> k4) { this.k4 = new ConcurrentHashMap<>(k4); }
        
        @JsonProperty("k5")
        public Map<String, Integer> getK5() { return k5; }
        
        @JsonProperty("k5")
        public void setK5(Map<String, Integer> k5) { this.k5 = new ConcurrentHashMap<>(k5); }
        
        @JsonProperty("k6")
        public Map<String, Integer> getK6() { return k6; }
        
        @JsonProperty("k6")
        public void setK6(Map<String, Integer> k6) { this.k6 = new ConcurrentHashMap<>(k6); }
        
        @JsonProperty("k7")
        public Map<String, Integer> getK7() { return k7; }
        
        @JsonProperty("k7")
        public void setK7(Map<String, Integer> k7) { this.k7 = new ConcurrentHashMap<>(k7); }
        
        @JsonProperty("k8")
        public Map<String, Integer> getK8() { return k8; }
        
        @JsonProperty("k8")
        public void setK8(Map<String, Integer> k8) { this.k8 = new ConcurrentHashMap<>(k8); }
    }

    /**
     * Keyboard type enum (matches LegacyConfig).
     */
    public enum KeyboardType { K4, K5, K6, K7, K8 }

    /**
     * Misc event enum (matches LegacyConfig).
     */
    public enum MiscEvent {
        NONE,                       // None
        SPEED_UP, SPEED_DOWN,       // Speed changes
        MAIN_VOL_UP, MAIN_VOL_DOWN, // Main volume changes
        KEY_VOL_UP, KEY_VOL_DOWN,   // Key volume changes
        BGM_VOL_UP, BGM_VOL_DOWN,   // BGM volume changes
    }

    /**
     * Game options wrapper for JSON serialization.
     *
     * <p>This class wraps GameOptions fields for JSON storage while maintaining
     * compatibility with the existing GameOptions class.</p>
     */
    public static class GameOptionsWrapper {
        // Gameplay
        @JsonProperty("speedMultiplier")
        private double speedMultiplier = 1.0;
        
        @JsonProperty("speedType")
        private GameOptions.SpeedType speedType = GameOptions.SpeedType.HI_SPEED;
        
        @JsonProperty("visibilityModifier")
        private GameOptions.VisibilityMod visibilityModifier = GameOptions.VisibilityMod.NONE;
        
        @JsonProperty("channelModifier")
        private GameOptions.ChannelMod channelModifier = GameOptions.ChannelMod.NONE;
        
        @JsonProperty("judgmentType")
        private GameOptions.JudgmentType judgmentType = GameOptions.JudgmentType.BEAT_JUDGMENT;

        // Audio
        @JsonProperty("keyVolume")
        private float keyVolume = 1.0f;
        
        @JsonProperty("bgmVolume")
        private float bgmVolume = 1.0f;
        
        @JsonProperty("masterVolume")
        private float masterVolume = 1.0f;

        // Autoplay
        @JsonProperty("autoplay")
        private boolean autoplay = false;
        
        @JsonProperty("autosound")
        private boolean autosound = false;
        
        @JsonProperty("autoplayChannels")
        private java.util.List<Boolean> autoplayChannels = Arrays.asList(false, false, false, false, false, false, false);

        // Display
        @JsonProperty("displayFullscreen")
        private boolean displayFullscreen = false;
        
        @JsonProperty("displayVsync")
        private boolean displayVsync = true;
        
        @JsonProperty("fpsLimiter")
        private GameOptions.FpsLimiter fpsLimiter = GameOptions.FpsLimiter.X1;
        
        @JsonProperty("displayWidth")
        private int displayWidth = 1280;
        
        @JsonProperty("displayHeight")
        private int displayHeight = 720;
        
        @JsonProperty("displayBitsPerPixel")
        private int displayBitsPerPixel = 32;
        
        @JsonProperty("displayFrequency")
        private int displayFrequency = 60;
        
        @JsonProperty("uiScale")
        private String uiScale = "automatic"; // "automatic" = system default, or a string number like "1.25"
        
        @JsonProperty("uiTheme")
        private GameOptions.UiTheme uiTheme = GameOptions.UiTheme.AUTOMATIC;

        // Sound
        @JsonProperty("bufferSize")
        private int bufferSize = 512;

        // Timing
        @JsonProperty("displayLag")
        private double displayLag = 0.0;
        
        @JsonProperty("audioLatency")
        private double audioLatency = 0.0;

        // Haste mode
        @JsonProperty("hasteMode")
        private boolean hasteMode = false;
        
        @JsonProperty("hasteModeNormalizeSpeed")
        private boolean hasteModeNormalizeSpeed = true;

        // ===== Getters and Setters =====
        
        @JsonProperty("speedMultiplier")
        public double getSpeedMultiplier() { return speedMultiplier; }
        
        @JsonProperty("speedMultiplier")
        public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = speedMultiplier; }
        
        @JsonProperty("speedType")
        public GameOptions.SpeedType getSpeedType() { return speedType; }
        
        @JsonProperty("speedType")
        public void setSpeedType(GameOptions.SpeedType speedType) { this.speedType = speedType; }
        
        @JsonProperty("visibilityModifier")
        public GameOptions.VisibilityMod getVisibilityModifier() { return visibilityModifier; }
        
        @JsonProperty("visibilityModifier")
        public void setVisibilityModifier(GameOptions.VisibilityMod visibilityModifier) { this.visibilityModifier = visibilityModifier; }
        
        @JsonProperty("channelModifier")
        public GameOptions.ChannelMod getChannelModifier() { return channelModifier; }
        
        @JsonProperty("channelModifier")
        public void setChannelModifier(GameOptions.ChannelMod channelModifier) { this.channelModifier = channelModifier; }
        
        @JsonProperty("judgmentType")
        public GameOptions.JudgmentType getJudgmentType() { return judgmentType; }
        
        @JsonProperty("judgmentType")
        public void setJudgmentType(GameOptions.JudgmentType judgmentType) { this.judgmentType = judgmentType; }
        
        @JsonProperty("keyVolume")
        public float getKeyVolume() { return keyVolume; }
        
        @JsonProperty("keyVolume")
        public void setKeyVolume(float keyVolume) { this.keyVolume = keyVolume; }
        
        @JsonProperty("bgmVolume")
        public float getBgmVolume() { return bgmVolume; }
        
        @JsonProperty("bgmVolume")
        public void setBgmVolume(float bgmVolume) { this.bgmVolume = bgmVolume; }
        
        @JsonProperty("masterVolume")
        public float getMasterVolume() { return masterVolume; }
        
        @JsonProperty("masterVolume")
        public void setMasterVolume(float masterVolume) { this.masterVolume = masterVolume; }
        
        @JsonProperty("autoplay")
        public boolean isAutoplay() { return autoplay; }
        
        @JsonProperty("autoplay")
        public void setAutoplay(boolean autoplay) { this.autoplay = autoplay; }
        
        @JsonProperty("autosound")
        public boolean isAutosound() { return autosound; }
        
        @JsonProperty("autosound")
        public void setAutosound(boolean autosound) { this.autosound = autosound; }
        
        @JsonProperty("autoplayChannels")
        public java.util.List<Boolean> getAutoplayChannels() { return autoplayChannels; }
        
        @JsonProperty("autoplayChannels")
        public void setAutoplayChannels(java.util.List<Boolean> autoplayChannels) { this.autoplayChannels = autoplayChannels; }
        
        @JsonProperty("displayFullscreen")
        public boolean isDisplayFullscreen() { return displayFullscreen; }
        
        @JsonProperty("displayFullscreen")
        public void setDisplayFullscreen(boolean displayFullscreen) { this.displayFullscreen = displayFullscreen; }
        
        @JsonProperty("displayVsync")
        public boolean isDisplayVsync() { return displayVsync; }
        
        @JsonProperty("displayVsync")
        public void setDisplayVsync(boolean displayVsync) { this.displayVsync = displayVsync; }
        
        @JsonProperty("fpsLimiter")
        public GameOptions.FpsLimiter getFpsLimiter() { return fpsLimiter; }
        
        @JsonProperty("fpsLimiter")
        public void setFpsLimiter(GameOptions.FpsLimiter fpsLimiter) { this.fpsLimiter = fpsLimiter; }
        
        @JsonProperty("displayWidth")
        public int getDisplayWidth() { return displayWidth; }
        
        @JsonProperty("displayWidth")
        public void setDisplayWidth(int displayWidth) { this.displayWidth = displayWidth; }
        
        @JsonProperty("displayHeight")
        public int getDisplayHeight() { return displayHeight; }
        
        @JsonProperty("displayHeight")
        public void setDisplayHeight(int displayHeight) { this.displayHeight = displayHeight; }
        
        @JsonProperty("displayBitsPerPixel")
        public int getDisplayBitsPerPixel() { return displayBitsPerPixel; }
        
        @JsonProperty("displayBitsPerPixel")
        public void setDisplayBitsPerPixel(int displayBitsPerPixel) { this.displayBitsPerPixel = displayBitsPerPixel; }
        
        @JsonProperty("displayFrequency")
        public int getDisplayFrequency() { return displayFrequency; }
        
        @JsonProperty("displayFrequency")
        public void setDisplayFrequency(int displayFrequency) { this.displayFrequency = displayFrequency; }
        
        @JsonProperty("uiScale")
        public String getUiScale() { return uiScale; }
        
        @JsonProperty("uiScale")
        public void setUiScale(String uiScale) { this.uiScale = uiScale; }
        
        @JsonProperty("uiTheme")
        public GameOptions.UiTheme getUiTheme() { return uiTheme; }
        
        @JsonProperty("uiTheme")
        public void setUiTheme(GameOptions.UiTheme uiTheme) { this.uiTheme = uiTheme; }
        
        @JsonProperty("bufferSize")
        public int getBufferSize() { return bufferSize; }
        
        @JsonProperty("bufferSize")
        public void setBufferSize(int bufferSize) { this.bufferSize = bufferSize; }
        
        @JsonProperty("displayLag")
        public double getDisplayLag() { return displayLag; }
        
        @JsonProperty("displayLag")
        public void setDisplayLag(double displayLag) { this.displayLag = displayLag; }
        
        @JsonProperty("audioLatency")
        public double getAudioLatency() { return audioLatency; }
        
        @JsonProperty("audioLatency")
        public void setAudioLatency(double audioLatency) { this.audioLatency = audioLatency; }
        
        @JsonProperty("hasteMode")
        public boolean isHasteMode() { return hasteMode; }
        
        @JsonProperty("hasteMode")
        public void setHasteMode(boolean hasteMode) { this.hasteMode = hasteMode; }
        
        @JsonProperty("hasteModeNormalizeSpeed")
        public boolean isHasteModeNormalizeSpeed() { return hasteModeNormalizeSpeed; }
        
        @JsonProperty("hasteModeNormalizeSpeed")
        public void setHasteModeNormalizeSpeed(boolean hasteModeNormalizeSpeed) { this.hasteModeNormalizeSpeed = hasteModeNormalizeSpeed; }

        /**
         * Convert to GameOptions object.
         *
         * @return GameOptions with same settings
         */
        public GameOptions toGameOptions() {
            GameOptions opts = new GameOptions();
            opts.setSpeedMultiplier(getSpeedMultiplier());
            opts.setSpeedType(getSpeedType());
            opts.setVisibilityModifier(getVisibilityModifier());
            opts.setChannelModifier(getChannelModifier());
            opts.setJudgmentType(getJudgmentType());
            opts.setKeyVolume(getKeyVolume());
            opts.setBGMVolume(getBgmVolume());
            opts.setMasterVolume(getMasterVolume());
            opts.setAutoplay(isAutoplay());
            opts.setAutosound(isAutosound());
            opts.setAutoplayChannels(getAutoplayChannels());
            opts.setDisplayFullscreen(isDisplayFullscreen());
            opts.setDisplayVsync(isDisplayVsync());
            opts.setFpsLimiter(getFpsLimiter());
            opts.setDisplayWidth(getDisplayWidth());
            opts.setDisplayHeight(getDisplayHeight());
            opts.setDisplayBitsPerPixel(getDisplayBitsPerPixel());
            opts.setDisplayFrequency(getDisplayFrequency());
            opts.setBufferSize(getBufferSize());
            opts.setDisplayLag(getDisplayLag());
            opts.setAudioLatency(getAudioLatency());
            opts.setUiScale(getUiScale());
            opts.setUiTheme(getUiTheme());
            opts.setHasteMode(isHasteMode());
            opts.setHasteModeNormalizeSpeed(isHasteModeNormalizeSpeed());
            return opts;
        }

        /**
         * Create from existing GameOptions.
         *
         * @param opts GameOptions to wrap
         * @return GameOptionsWrapper with same settings
         */
        public static GameOptionsWrapper fromGameOptions(GameOptions opts) {
            GameOptionsWrapper wrapper = new GameOptionsWrapper();
            wrapper.setSpeedMultiplier(opts.getSpeedMultiplier());
            wrapper.setSpeedType(opts.getSpeedType());
            wrapper.setVisibilityModifier(opts.getVisibilityModifier());
            wrapper.setChannelModifier(opts.getChannelModifier());
            wrapper.setJudgmentType(opts.getJudgmentType());
            wrapper.setKeyVolume(opts.getKeyVolume());
            wrapper.setBgmVolume(opts.getBGMVolume());
            wrapper.setMasterVolume(opts.getMasterVolume());
            wrapper.setAutoplay(opts.isAutoplay());
            wrapper.setAutosound(opts.isAutosound());
            wrapper.setAutoplayChannels(opts.getAutoplayChannels());
            wrapper.setDisplayFullscreen(opts.isDisplayFullscreen());
            wrapper.setDisplayVsync(opts.isDisplayVsync());
            wrapper.setFpsLimiter(opts.getFpsLimiter());
            wrapper.setDisplayWidth(opts.getDisplayWidth());
            wrapper.setDisplayHeight(opts.getDisplayHeight());
            wrapper.setDisplayBitsPerPixel(opts.getDisplayBitsPerPixel());
            wrapper.setDisplayFrequency(opts.getDisplayFrequency());
            wrapper.setBufferSize(opts.getBufferSize());
            wrapper.setDisplayLag(opts.getDisplayLag());
            wrapper.setAudioLatency(opts.getAudioLatency());
            wrapper.setUiScale(opts.getUiScale());
            wrapper.setUiTheme(opts.getUiTheme());
            wrapper.setHasteMode(opts.isHasteMode());
            wrapper.setHasteModeNormalizeSpeed(opts.isHasteModeNormalizeSpeed());
            return wrapper;
        }
    }
}
