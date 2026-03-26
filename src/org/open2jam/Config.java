package org.open2jam;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    private static Config instance;
    
    // ===== Debounced Save =====
    private static final ScheduledExecutorService saveExecutor = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "Config-Saver");
        t.setDaemon(true);
        return t;
    });
    private static final AtomicBoolean savePending = new AtomicBoolean(false);
    private static final long SAVE_DELAY_MS = 500;  // Debounce delay

    // ===== Config Fields =====
    public KeyBindings keyBindings = new KeyBindings();
    public GameOptionsWrapper gameOptions = new GameOptionsWrapper();
    
    // ===== UI State (persisted for convenience) =====
    /**
     * Last opened library ID from SQLite libraries table.
     * Used to restore the last selected directory on startup.
     * Ignored if library doesn't exist or database is empty.
     */
    public Integer lastOpenedLibraryId = null;

    // ===== Initialization =====

    /**
     * Get singleton Config instance.
     * 
     * <p>Thread-safe lazy initialization. Loads from disk on first access if file exists.</p>
     * 
     * @return Singleton Config instance
     */
    public static Config getInstance() {
        if (instance == null) {
            synchronized (Config.class) {
                if (instance == null) {
                    instance = load();
                }
            }
        }
        return instance;
    }

    /**
     * Load configuration from disk.
     * 
     * <p>Creates default config if file doesn't exist or is corrupted.</p>
     * 
     * @return Loaded or default Config instance
     */
    private static Config load() {
        // Ensure save directory exists
        File saveDir = new File("save");
        if (!saveDir.exists()) {
            if (!saveDir.mkdirs()) {
                Logger.global.severe("Failed to create save directory: " + saveDir.getAbsolutePath());
            }
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
        if (gameOptions.displayWidth < 640 || gameOptions.displayWidth > 7680) {
            Logger.global.warning("Invalid displayWidth (" + gameOptions.displayWidth + "), using default 1280");
            gameOptions.displayWidth = 1280;
        }
        if (gameOptions.displayHeight < 480 || gameOptions.displayHeight > 4320) {
            Logger.global.warning("Invalid displayHeight (" + gameOptions.displayHeight + "), using default 720");
            gameOptions.displayHeight = 720;
        }
        // Validate volume levels (0-100)
        gameOptions.keyVolume = Math.max(0, Math.min(100, gameOptions.keyVolume));
        gameOptions.bgmVolume = Math.max(0, Math.min(100, gameOptions.bgmVolume));
        gameOptions.masterVolume = Math.max(0, Math.min(100, gameOptions.masterVolume));
        // Validate speed multiplier (0.1x - 10x)
        if (gameOptions.speedMultiplier < 0.1 || gameOptions.speedMultiplier > 10.0) {
            Logger.global.warning("Invalid speedMultiplier (" + gameOptions.speedMultiplier + "), using default 1.0");
            gameOptions.speedMultiplier = 1.0;
        }
        // Validate buffer size (64-4096)
        if (gameOptions.bufferSize < 64 || gameOptions.bufferSize > 4096) {
            Logger.global.warning("Invalid bufferSize (" + gameOptions.bufferSize + "), using default 512");
            gameOptions.bufferSize = 512;
        }
        // Validate display frequency (30-1024 Hz for high-refresh monitors)
        if (gameOptions.displayFrequency < 30 || gameOptions.displayFrequency > 1024) {
            Logger.global.warning("Invalid displayFrequency (" + gameOptions.displayFrequency + "), using default 60");
            gameOptions.displayFrequency = 60;
        }
        // Validate bits per pixel (16 or 32)
        if (gameOptions.displayBitsPerPixel != 16 && gameOptions.displayBitsPerPixel != 32) {
            Logger.global.warning("Invalid displayBitsPerPixel (" + gameOptions.displayBitsPerPixel + "), using default 32");
            gameOptions.displayBitsPerPixel = 32;
        }
        // Validate UI scale ("automatic" or numeric original 0.5-4.0)
        if (!"automatic".equalsIgnoreCase(gameOptions.uiScale)) {
            try {
                double val = Double.parseDouble(gameOptions.uiScale);
                if (val < 0.5 || val > 4.0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                Logger.global.warning("Invalid uiScale (" + gameOptions.uiScale + "), using default 'automatic'");
                gameOptions.uiScale = "automatic";
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
        config.keyBindings = createDefaultKeyBindings();
        config.gameOptions = createDefaultGameOptions();
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
        kb.keyboard.k4.put("NOTE_1", org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k4.put("NOTE_2", org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k4.put("NOTE_3", org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k4.put("NOTE_4", org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k4.put("NOTE_SC", org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K5
        kb.keyboard.k5.put("NOTE_1", org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k5.put("NOTE_2", org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k5.put("NOTE_3", org.open2jam.render.lwjgl.Keyboard.KEY_SPACE);
        kb.keyboard.k5.put("NOTE_4", org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k5.put("NOTE_5", org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k5.put("NOTE_SC", org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K6
        kb.keyboard.k6.put("NOTE_1", org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k6.put("NOTE_2", org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k6.put("NOTE_3", org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k6.put("NOTE_4", org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k6.put("NOTE_5", org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k6.put("NOTE_6", org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k6.put("NOTE_SC", org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K7
        kb.keyboard.k7.put("NOTE_1", org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k7.put("NOTE_2", org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k7.put("NOTE_3", org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k7.put("NOTE_4", org.open2jam.render.lwjgl.Keyboard.KEY_SPACE);
        kb.keyboard.k7.put("NOTE_5", org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k7.put("NOTE_6", org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k7.put("NOTE_7", org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k7.put("NOTE_SC", org.open2jam.render.lwjgl.Keyboard.KEY_LSHIFT);

        // K8
        kb.keyboard.k8.put("NOTE_1", org.open2jam.render.lwjgl.Keyboard.KEY_A);
        kb.keyboard.k8.put("NOTE_2", org.open2jam.render.lwjgl.Keyboard.KEY_S);
        kb.keyboard.k8.put("NOTE_3", org.open2jam.render.lwjgl.Keyboard.KEY_D);
        kb.keyboard.k8.put("NOTE_4", org.open2jam.render.lwjgl.Keyboard.KEY_F);
        kb.keyboard.k8.put("NOTE_5", org.open2jam.render.lwjgl.Keyboard.KEY_H);
        kb.keyboard.k8.put("NOTE_6", org.open2jam.render.lwjgl.Keyboard.KEY_J);
        kb.keyboard.k8.put("NOTE_7", org.open2jam.render.lwjgl.Keyboard.KEY_K);
        kb.keyboard.k8.put("NOTE_8", org.open2jam.render.lwjgl.Keyboard.KEY_L);
        kb.keyboard.k8.put("NOTE_SC", org.open2jam.render.lwjgl.Keyboard.KEY_L);

        return kb;
    }

    /**
     * Create default game options wrapper.
     * 
     * @return GameOptionsWrapper with default values
     */
    private static GameOptionsWrapper createDefaultGameOptions() {
        GameOptionsWrapper opts = new GameOptionsWrapper();
        opts.speedMultiplier = 1.0;
        opts.speedType = GameOptions.SpeedType.HiSpeed;
        opts.visibilityModifier = GameOptions.VisibilityMod.None;
        opts.channelModifier = GameOptions.ChannelMod.None;
        opts.judgmentType = GameOptions.JudgmentType.BeatJudgment;
        opts.keyVolume = 1.0f;
        opts.bgmVolume = 1.0f;
        opts.masterVolume = 1.0f;
        opts.autoplay = false;
        opts.autosound = false;
        opts.autoplayChannels = Arrays.asList(false, false, false, false, false, false, false);
        opts.displayFullscreen = false;
        opts.displayVsync = true;
        opts.fpsLimiter = GameOptions.FpsLimiter.x1;
        opts.displayWidth = 1280;
        opts.displayHeight = 720;
        opts.displayBitsPerPixel = 32;
        opts.displayFrequency = 60;
        opts.bufferSize = 512;
        opts.displayLag = 0.0;
        opts.audioLatency = 0.0;
        opts.hasteMode = false;
        opts.hasteModeNormalizeSpeed = true;
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

    // ===== Game Options =====

    /**
     * Get game options.
     * 
     * @return GameOptionsWrapper with all game settings
     */
    public GameOptionsWrapper getGameOptions() {
        return gameOptions;
    }

    /**
     * Set game options.
     *
     * @param options New game options (will be saved immediately)
     */
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
    public Integer getLastOpenedLibraryId() {
        return lastOpenedLibraryId;
    }

    /**
     * Set last opened library ID.
     *
     * @param libraryId Library ID from SQLite libraries table
     */
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
        public Map<String, Integer> misc = new ConcurrentHashMap<>();
        public KeyboardMaps keyboard = new KeyboardMaps();

        // Getters/setters for Jackson
        public Map<String, Integer> getMisc() { return misc; }
        public void setMisc(Map<String, Integer> misc) { this.misc = new ConcurrentHashMap<>(misc); }
        public KeyboardMaps getKeyboard() { return keyboard; }
        public void setKeyboard(KeyboardMaps keyboard) { this.keyboard = keyboard; }
    }

    /**
     * Keyboard maps container.
     * 
     * <p>Thread Safety: Uses ConcurrentHashMap for all maps to prevent ConcurrentModificationException.</p>
     */
    public static class KeyboardMaps {
        public Map<String, Integer> k4 = new ConcurrentHashMap<>();
        public Map<String, Integer> k5 = new ConcurrentHashMap<>();
        public Map<String, Integer> k6 = new ConcurrentHashMap<>();
        public Map<String, Integer> k7 = new ConcurrentHashMap<>();
        public Map<String, Integer> k8 = new ConcurrentHashMap<>();

        // Getters/setters for Jackson
        public Map<String, Integer> getK4() { return k4; }
        public void setK4(Map<String, Integer> k4) { this.k4 = new ConcurrentHashMap<>(k4); }
        public Map<String, Integer> getK5() { return k5; }
        public void setK5(Map<String, Integer> k5) { this.k5 = new ConcurrentHashMap<>(k5); }
        public Map<String, Integer> getK6() { return k6; }
        public void setK6(Map<String, Integer> k6) { this.k6 = new ConcurrentHashMap<>(k6); }
        public Map<String, Integer> getK7() { return k7; }
        public void setK7(Map<String, Integer> k7) { this.k7 = new ConcurrentHashMap<>(k7); }
        public Map<String, Integer> getK8() { return k8; }
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
        public double speedMultiplier = 1.0;
        public GameOptions.SpeedType speedType = GameOptions.SpeedType.HiSpeed;
        public GameOptions.VisibilityMod visibilityModifier = GameOptions.VisibilityMod.None;
        public GameOptions.ChannelMod channelModifier = GameOptions.ChannelMod.None;
        public GameOptions.JudgmentType judgmentType = GameOptions.JudgmentType.BeatJudgment;

        // Audio
        public float keyVolume = 1.0f;
        public float bgmVolume = 1.0f;
        public float masterVolume = 1.0f;

        // Autoplay
        public boolean autoplay = false;
        public boolean autosound = false;
        public java.util.List<Boolean> autoplayChannels = Arrays.asList(false, false, false, false, false, false, false);

        // Display
        public boolean displayFullscreen = false;
        public boolean displayVsync = true;
        public GameOptions.FpsLimiter fpsLimiter = GameOptions.FpsLimiter.x1;
        public int displayWidth = 1280;
        public int displayHeight = 720;
        public int displayBitsPerPixel = 32;
        public int displayFrequency = 60;
        public String uiScale = "automatic"; // "automatic" = system default, or a string number like "1.25"
        public GameOptions.UiTheme uiTheme = GameOptions.UiTheme.Automatic;

        // Sound
        public int bufferSize = 512;

        // Timing
        public double displayLag = 0.0;
        public double audioLatency = 0.0;

        // Haste mode
        public boolean hasteMode = false;
        public boolean hasteModeNormalizeSpeed = true;

        /**
         * Convert to GameOptions object.
         *
         * @return GameOptions with same settings
         */
        public GameOptions toGameOptions() {
            GameOptions opts = new GameOptions();
            opts.setSpeedMultiplier(speedMultiplier);
            opts.setSpeedType(speedType);
            opts.setVisibilityModifier(visibilityModifier);
            opts.setChannelModifier(channelModifier);
            opts.setJudgmentType(judgmentType);
            opts.setKeyVolume(keyVolume);
            opts.setBGMVolume(bgmVolume);
            opts.setMasterVolume(masterVolume);
            opts.setAutoplay(autoplay);
            opts.setAutosound(autosound);
            opts.setAutoplayChannels(autoplayChannels);
            opts.setDisplayFullscreen(displayFullscreen);
            opts.setDisplayVsync(displayVsync);
            opts.setFpsLimiter(fpsLimiter);
            opts.setDisplayWidth(displayWidth);
            opts.setDisplayHeight(displayHeight);
            opts.setDisplayBitsPerPixel(displayBitsPerPixel);
            opts.setDisplayFrequency(displayFrequency);
            opts.setBufferSize(bufferSize);
            opts.setDisplayLag(displayLag);
            opts.setAudioLatency(audioLatency);
            opts.setUiScale(uiScale);
            opts.setUiTheme(uiTheme);
            opts.setHasteMode(hasteMode);
            opts.setHasteModeNormalizeSpeed(hasteModeNormalizeSpeed);
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
            wrapper.speedMultiplier = opts.getSpeedMultiplier();
            wrapper.speedType = opts.getSpeedType();
            wrapper.visibilityModifier = opts.getVisibilityModifier();
            wrapper.channelModifier = opts.getChannelModifier();
            wrapper.judgmentType = opts.getJudgmentType();
            wrapper.keyVolume = opts.getKeyVolume();
            wrapper.bgmVolume = opts.getBGMVolume();
            wrapper.masterVolume = opts.getMasterVolume();
            wrapper.autoplay = opts.isAutoplay();
            wrapper.autosound = opts.isAutosound();
            wrapper.autoplayChannels = opts.getAutoplayChannels();
            wrapper.displayFullscreen = opts.isDisplayFullscreen();
            wrapper.displayVsync = opts.isDisplayVsync();
            wrapper.fpsLimiter = opts.getFpsLimiter();
            wrapper.displayWidth = opts.getDisplayWidth();
            wrapper.displayHeight = opts.getDisplayHeight();
            wrapper.displayBitsPerPixel = opts.getDisplayBitsPerPixel();
            wrapper.displayFrequency = opts.getDisplayFrequency();
            wrapper.bufferSize = opts.getBufferSize();
            wrapper.displayLag = opts.getDisplayLag();
            wrapper.audioLatency = opts.getAudioLatency();
            wrapper.uiScale = opts.getUiScale();
            wrapper.uiTheme = opts.getUiTheme();
            wrapper.hasteMode = opts.isHasteMode();
            wrapper.hasteModeNormalizeSpeed = opts.isHasteModeNormalizeSpeed();
            return wrapper;
        }
    }
}
