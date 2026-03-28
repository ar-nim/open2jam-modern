package org.open2jam;

/**
 * Application context container for shared state.
 * 
 * <p>Provides dependency injection without framework overhead.
 * Pass AppContext through constructors instead of using Config singleton.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>
 * // Main.java (composition root)
 * Config config = Config.load();
 * AppContext context = new AppContext(config);
 * new Interface(context);
 * 
 * // Other classes
 * public class MusicSelection {
 *     private final AppContext context;
 *     
 *     public MusicSelection(AppContext context) {
 *         this.context = context;
 *     }
 *     
 *     void loadSongs() {
 *         Config config = context.config;
 *     }
 * }
 * </pre>
 * 
 * <h2>Future Extensions:</h2>
 * <p>Add more shared state as needed:</p>
 * <ul>
 *   <li>PlayerStats - High scores, play counts</li>
 *   <li>AudioManager - Global audio state</li>
 *   <li>MultiplayerState - Online multiplayer session</li>
 * </ul>
 * 
 * @author open2jam-modern team
 */
public final class AppContext {
    /** Configuration (key bindings, game options) */
    public final Config config;
    
    /**
     * Create application context with configuration.
     * 
     * @param config Loaded configuration (must not be null)
     */
    public AppContext(Config config) {
        if (config == null) {
            throw new IllegalArgumentException("Config must not be null");
        }
        this.config = config;
    }
}
