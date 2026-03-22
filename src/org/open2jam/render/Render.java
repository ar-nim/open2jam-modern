
package org.open2jam.render;

import org.open2jam.sound.SoundInstance;
import org.open2jam.game.TimingData;
import org.open2jam.game.Latency;
import com.github.dtinth.partytime.Client;
import com.github.dtinth.partytime.server.Connection;
import com.github.dtinth.partytime.server.Server;
import org.open2jam.sound.ALSoundSystem;
import org.open2jam.sound.ALSound;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Map.Entry;
import java.util.*;
import java.util.logging.Level;
import javax.imageio.ImageIO;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import org.open2jam.render.lwjgl.Keyboard;
import org.open2jam.Config;
import org.open2jam.GameOptions;
import org.open2jam.game.speed.SpeedMultiplier;
import org.open2jam.game.speed.Speed;
import org.open2jam.game.position.WSpeed;
import org.open2jam.parsers.Chart;
import org.open2jam.parsers.Event;
import org.open2jam.parsers.EventList;
import org.open2jam.parsers.utils.SampleData;
import org.open2jam.render.entities.*;
import org.open2jam.game.judgment.JudgmentResult;
import org.open2jam.game.judgment.JudgmentStrategy;
import org.open2jam.game.position.HiSpeed;
import org.open2jam.game.position.NoteDistanceCalculator;
import org.open2jam.game.position.RegulSpeed;
import org.open2jam.game.position.XRSpeed;
import org.open2jam.render.lwjgl.TrueTypeFont;
import org.open2jam.sound.Sound;
import org.open2jam.sound.SoundChannel;
import org.open2jam.sound.SoundSystem;
import org.open2jam.sound.SoundSystemException;
import org.open2jam.util.*;
import org.lwjgl.opengl.GL11;


/**
 *
 * @author fox
 */
public class Render implements GameWindowCallback
{
    private String localMatchingServer = "";
    private int rank;
    private final boolean normalizeSpeed;

    private Server server = null;
    public void setServer(Server lastServer) {
        server = lastServer;
    }
    
    
    public interface AutosyncCallback {
        void autosyncFinished(double displayLag);
    }
    
    /** the config xml */
    private static final URL resources_xml = Render.class.getResource("/resources/resources.xml");

    /** 4 beats per minute, 4 * 60 beats per second, 4*60*1000 per millisecond */
    private static final int BEATS_PER_MSEC = 4 * 60 * 1000;
    
    private static final double DELAY_TIME = 1500;
    
    /** player options */
    private final GameOptions opt;
    
    private static final double AUTOPLAY_THRESHOLD = 0;

    /** skin info and entities */
    Skin skin;

    /** the mapping of note channels to KeyEvent keys  */
    final EnumMap<Event.Channel, Integer> keyboard_map;

    /** the mapping of note channels to KeyEvent keys  */
    final EnumMap<Config.MiscEvent, Integer> keyboard_misc;

    /** The window that is being used to render the game */
    final GameWindow window;
    
    /** The sound system to use */
    final SoundSystem soundSystem;
    
    /** The judge to judge the notes */
    private JudgmentStrategy judge;

    /** the chart being rendered */
    private final Chart chart;

    /** The recorded fps */
    int fps;

    /** the current "calculated" speed */
    double speed;
    
    /** the base speed multiplier */
    private Speed speedObj;
    
    /** the note distance calculator */
    private NoteDistanceCalculator distance;
    
    private boolean gameStarted = true;

    /** the layer of the notes */
    private int note_layer;

    /** the bpm at which the entities are falling */
    private double bpm;

    /** maps the Event value to Sound objects */
    private Map<Integer, Sound> sounds;

    /** The time at which the last rendering looped started from the point of view of the game logic */
    double lastLoopTime;

    /** The time since the last record of fps */
    double lastFpsTime = 0;
    
    /** The cumulative time in this game */
    double gameTime = 0;
    int gameMeasure = 0;
    Runnable increaseMeasureRunnable = new Runnable() {
        @Override
        public void run() {
            gameMeasure += 1;
        }
    };

    /** the time it started rendering */
    double start_time;

       /** a list of list of entities.
    ** basically, each list is a layer of entities
    ** the layers are rendered in order
    ** so entities at layer X will always be rendered before layer X+1 */
    final EntityMatrix entities_matrix;

    /** this iterator is used by the update_note_buffer
     * to go through the events on the chart */
    Iterator<Event> buffer_iterator;

    /** this is used by the update_note_buffer
     * to remember the "opened" long-notes */
    private LongNoteEntity[] ln_buffer;

    /** this holds the actual state of the keyboard,
     * whether each is being pressed or not */
    private boolean[] keyboard_key_pressed;

    private Entity[] longflare;

    private NumberEntity[] note_counter;

    /** these are the same notes from the entity_matrix
     * but divided in channels for ease to pull */
    private LinkedList<NoteEntity>[] note_channels;

    /** Object pool for note entities to eliminate per-note allocations */
    private org.open2jam.render.pool.NoteEntityPool noteEntityPool;

    /** entities for the key pressed events
     * need to keep track of then to kill
     * when the key is released */
    private Entity[] key_pressed_entity;

    /** keep track of the long note the player may be
     * holding with the key */
    private LongNoteEntity[] longnote_holded;

    /** keep trap of the last sound of each channel
     * so that the player can re-play the sound when the key is pressed */
    private SampleEntity[] last_sound;

    /** number to display the fps, and note counters on the screen */
    NumberEntity fps_entity;

    NumberEntity score_entity;
    
    /** JamCombo variables */
    ComboCounterEntity jamcombo_entity;
    
    /**
     * Cools: +2
     * Goods: +1
     * Everything else: reset to 0
     * >=50 to add a jam
     */
    BarEntity jambar_entity;

    BarEntity lifebar_entity;

    LinkedList<Entity> pills_draw;
    
    Map<Integer, Sprite> bga_sprites;
    Sprite coverSprite;  // Store cover sprite for loading screen
    BgaEntity bgaEntity;

    int consecutive_cools = 0;

    NumberEntity minute_entity;
    NumberEntity second_entity;

    Entity judgment_entity;

    /** the combo counter */
    ComboCounterEntity combo_entity;

    /** the maxcombo counter */
    NumberEntity maxcombo_entity;

    protected Entity judgment_line;
    
    TrueTypeFont trueTypeFont;

    /** statistics variable */
    double total_notes = 0;
    
    /** display and audio latency */
    private Latency displayLatency;
    private Latency audioLatency;
    
    /** points to a latency that's currenly syncing:
     * either displayLatency, audioLatency, or null.
     */
    private Latency syncingLatency;
    
    /** what to do after autosync? */
    AutosyncCallback autosyncCallback;
    
    /** local matching */
    private Client localMatching;

    /** song finish time - wait for music to end after notes finish */
    long finish_time = -1;

    protected CompositeEntity visibility_entity;

    private final static float VOLUME_FACTOR = 0.05f;
    
    /** timing data */
    private TimingData timing = new TimingData();
    
    /** status list */
    private StatusList statusList = new StatusList();

    /** haste mode: effective pitch */
    private int pitchShift;
    private double gameSpeed = 1;
    private double effectiveSpeed; /* set by updatePitch */
    private double effectiveJudgmentFactor; /* set by updatePitch */

    private boolean haste = false;

    /** adjust the final speed */
    private double speedFactor = 1.0;

    private class AdjustDistance implements NoteDistanceCalculator {
        private final NoteDistanceCalculator distance;

        public AdjustDistance(NoteDistanceCalculator distance) {
            this.distance = distance;
        }

        @Override
        public void update(double now, double delta) {
            distance.update(now, delta);
        }

        @Override
        public double calculate(double now, double target, double speed, NoteEntity noteEntity) {
            return distance.calculate(now, target, speed, noteEntity) * speedFactor;
        }

        @Override
        public String toString() {
            return distance.toString();
        }
        
    }
    
    static {
        ResourceFactory.get().setRenderingType(ResourceFactory.OPENGL_LWJGL);
    }
    
    protected final boolean AUTOSOUND;
    boolean disableAutoSound = false;

    public Render(Chart chart, GameOptions opt, org.open2jam.render.DisplayMode dm) throws SoundSystemException
    {
        keyboard_map = Config.getKeyboardMap(Config.KeyboardType.K7);
        keyboard_misc = Config.getKeyboardMisc();
        window = ResourceFactory.get().getGameWindow();

        soundSystem = new ALSoundSystem();
        soundSystem.setMasterVolume(opt.getMasterVolume());
        soundSystem.setBGMVolume(opt.getBGMVolume());
        soundSystem.setKeyVolume(opt.getKeyVolume());
        
        entities_matrix = new EntityMatrix();
        this.chart = chart;
        this.opt = opt;
        
        // speed multiplier
        speed = opt.getSpeedMultiplier();
        speedObj = new SpeedMultiplier(speed);
        
        distance = new HiSpeed(timing, 385);
        
        // TODO: refactor this
        switch(opt.getSpeedType())
        {
            case xRSpeed:
                distance = new XRSpeed(distance);
                break;
            case WSpeed:
                distance = new WSpeed(distance, speedObj);
                break;
            case RegulSpeed:
                distance = new RegulSpeed(385);
                break;
        }
        
        distance = new AdjustDistance(distance);
	
	AUTOSOUND = opt.isAutosound();
	
	//TODO Should get values from gameoptions, but i'm lazy as hell
	if(opt.isAutoplay()) 
	{
	    for(Event.Channel c : Event.Channel.values())
	    {
		if(c.toString().startsWith(("NOTE_")))
		    c.enableAutoplay();
	    }

//	    Event.Channel.NOTE_4.enableAutoplay();
//	    Event.Channel.NOTE_1.enableAutoplay();
	} else {
            
	    for(Event.Channel c : Event.Channel.values())
	    {
		if(c.toString().startsWith(("NOTE_")))
		    c.disableAutoplay();
	    }
        }
        
        displayLatency = new Latency(opt.getDisplayLag());
        audioLatency = new Latency(opt.getAudioLatency());
        
        statusList.add(new StatusItem() {

            @Override
            public String getText() {
                return distance + ": " + speedObj;
            }

            @Override
            public boolean isVisible() { return true; }
        });
        
        statusList.add(new StatusItem() {

            @Override
            public String getText() {
                return "Current Measure: " + gameMeasure;
            }

            @Override
            public boolean isVisible() { return true; }
        });
        
        statusList.add(new StatusItem() {

            @Override
            public String getText() {
                return "Game Speed: " + String.format("%+d", pitchShift);
            }

            @Override
            public boolean isVisible() { return true; }
        });
        
        haste = opt.isHasteMode();
        normalizeSpeed = opt.isHasteModeNormalizeSpeed();
        window.setDisplay(dm,opt.isDisplayVsync(),opt.isDisplayFullscreen());
    }

    public void setAutosyncCallback(AutosyncCallback autosyncDelegate) {
        this.autosyncCallback = autosyncDelegate;
    }
    
    public void setJudge(JudgmentStrategy judge) {
        this.judge = judge;
    }

    public void setAutosyncDisplay() {
        this.syncingLatency = displayLatency;
    }
    
    public void setAutosyncAudio() {
        this.syncingLatency = audioLatency;
    }
    
    public void setStartPaused() {
        this.gameStarted = false;
    }
    
    public void setLocalMatchingServer(String text) {
        this.localMatchingServer = text;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    
    /**
    * initialize the common elements for the game.
    * this is called by the window render
    */
    @Override
    public void initialise()
    {
        lastLoopTime = SystemTimer.getTime();

        // Initialize OpenAL sound system with current OpenGL context
        // This MUST be done on the rendering thread with a current context
        try {
            if (soundSystem instanceof ALSoundSystem) {
                ((ALSoundSystem) soundSystem).initializeWithCurrentContext();
            }
        } catch (SoundSystemException e) {
            Logger.global.log(Level.SEVERE, "Failed to initialize OpenAL: {0}", e.getMessage());
        }

        // skin load
        try {
            SkinParser sb = new SkinParser(window.getResolutionWidth(), window.getResolutionHeight());
            SAXParserFactory.newInstance().newSAXParser().parse(resources_xml.openStream(), sb);
            if((skin = sb.getResult("o2jam")) == null){
                Logger.global.log(Level.SEVERE, "Skin load error There is no o2jam skin");
            }
        } catch (ParserConfigurationException ex) {
            Logger.global.log(Level.SEVERE, "Skin load error {0}", ex);
        } catch (org.xml.sax.SAXException ex) {
            Logger.global.log(Level.SEVERE, "Skin load error {0}", ex);
        } catch (java.io.IOException ex) {
            Logger.global.log(Level.SEVERE, "Skin load error {0}", ex);
        }

        // cover image load
        try{
            BufferedImage img = chart.getCover();
            coverSprite = ResourceFactory.get().getSprite(img);  // Store for loading screen
            coverSprite.setScale(skin.getScreenScaleX(), skin.getScreenScaleY());
            coverSprite.draw(0, 0);
            window.update();
        } catch (NullPointerException e){
            Logger.global.log(Level.INFO, "No cover image on file: {0}", chart.getSource().getName());
        }

        changeSpeed(0);

        bpm = chart.getBPM();

        note_layer = skin.getEntityMap().get("NOTE_1").getLayer();

        // Initialize note entity pool
        // Pool size: chart note count + 20% buffer for safety
        int chartNotes = chart.getNoteCount();
        int poolSize = (int)(chartNotes * 1.2) + 50;
        noteEntityPool = new org.open2jam.render.pool.NoteEntityPool(poolSize);

        // Initialize pool with prototypes from skin
        NoteEntity[] notePrototypes = new NoteEntity[7];
        LongNoteEntity[] longNotePrototypes = new LongNoteEntity[7];
        for (int i = 1; i <= 7; i++) {
            String channelId = "NOTE_" + i;
            try {
                Entity e = skin.getEntityMap().get(channelId);
                if (e instanceof NoteEntity) {
                    notePrototypes[i-1] = (NoteEntity) e;
                }
                Entity ln = skin.getEntityMap().get("LONG_" + channelId);
                if (ln instanceof LongNoteEntity) {
                    longNotePrototypes[i-1] = (LongNoteEntity) ln;
                }
            } catch (Exception ex) {
                // Channel may not exist for this key mode
            }
        }
        noteEntityPool.initializePrototypes(notePrototypes, longNotePrototypes);

        // Initialize channel arrays (primitive arrays replace EnumMap)
        int numChannels = Event.Channel.values().length;
        ln_buffer = new LongNoteEntity[numChannels];
        keyboard_key_pressed = new boolean[numChannels];
        note_channels = new LinkedList[numChannels];
        key_pressed_entity = new Entity[numChannels];
        longnote_holded = new LongNoteEntity[numChannels];
        longflare = new Entity[numChannels];
        last_sound = new SampleEntity[numChannels];

        // Initialize note_channels lists for each channel
        for (int i = 0; i < numChannels; i++) {
            note_channels[i] = new LinkedList<NoteEntity>();
        }

        fps_entity = (NumberEntity) skin.getEntityMap().get("FPS_COUNTER");
        entities_matrix.add(fps_entity);

        score_entity = (NumberEntity) skin.getEntityMap().get("SCORE_COUNTER");
        entities_matrix.add(score_entity);

        jamcombo_entity = (ComboCounterEntity) skin.getEntityMap().get("JAM_COUNTER");
        jamcombo_entity.setThreshold(1);
        entities_matrix.add(jamcombo_entity);

        jambar_entity = (BarEntity) skin.getEntityMap().get("JAM_BAR");
        jambar_entity.setLimit(50);
        entities_matrix.add(jambar_entity);

        lifebar_entity = (BarEntity) skin.getEntityMap().get("LIFE_BAR");
        entities_matrix.add(lifebar_entity);

        combo_entity = (ComboCounterEntity) skin.getEntityMap().get("COMBO_COUNTER");
        combo_entity.setThreshold(2);
        entities_matrix.add(combo_entity);

        maxcombo_entity = (NumberEntity) skin.getEntityMap().get("MAXCOMBO_COUNTER");
        entities_matrix.add(maxcombo_entity);

        minute_entity = (NumberEntity) skin.getEntityMap().get("MINUTE_COUNTER");
        entities_matrix.add(minute_entity);

        second_entity = (NumberEntity) skin.getEntityMap().get("SECOND_COUNTER");
        second_entity.showDigits(2);//show 2 digits
        entities_matrix.add(second_entity);

        pills_draw = new LinkedList<Entity>();

        visibility_entity = new CompositeEntity();
        if(opt.getVisibilityModifier() != GameOptions.VisibilityMod.None)
            visibility(opt.getVisibilityModifier());

        judgment_line = skin.getEntityMap().get("JUDGMENT_LINE");
        entities_matrix.add(judgment_line);

        initLifeBar();

        // Initialize note_counter array (primitive array replaces EnumMap)
        int numJudgmentResults = JudgmentResult.values().length;
        note_counter = new NumberEntity[numJudgmentResults];
        for (JudgmentResult s : JudgmentResult.values()) {
            NumberEntity e = (NumberEntity) skin.getEntityMap().get("COUNTER_" + s).copy();
            note_counter[s.ordinal()] = e;
            entities_matrix.add(note_counter[s.ordinal()]);
        }
        start_time = lastLoopTime = SystemTimer.getTime();

        EventList event_list = construct_velocity_tree(chart.getEvents());
	event_list.fixEventList(EventList.FixMethod.OPEN2JAM, true);
        
        judge.setTiming(timing);

	//Let's randomize "-"
        switch(opt.getChannelModifier())
        {
            case Mirror:
		event_list.channelMirror();
            break;
            case Shuffle:
                event_list.channelShuffle();
            break;
            case Random:
                event_list.channelRandom();
            break;
        }
	
	bgaEntity = (BgaEntity) skin.getEntityMap().get("BGA");
	entities_matrix.add(bgaEntity);
	
	bga_sprites = new HashMap<Integer, Sprite>();
	if(chart.hasVideo()) {
	    bgaEntity.isVideo = true;
	    bgaEntity.videoFile = chart.getVideo();
	    bgaEntity.initVideo();
	} else if(!chart.getBgaIndex().isEmpty()) {
	    // get all the bgaEntity sprites
	    
	    for(Entry<Integer, File> entry: chart.getImages().entrySet()) {
		BufferedImage img;
		try {
		    img = ImageIO.read(entry.getValue());
		    Sprite s = ResourceFactory.get().getSprite(img);
		    bga_sprites.put(entry.getKey(), s);
		} catch (IOException ex) {
		    java.util.logging.Logger.getLogger(Render.class.getName()).log(Level.SEVERE, "{0}", ex);
		}    
	    }
	}
	
        // adding static entities
        for(Entity e : skin.getEntityList()){
            entities_matrix.add(e);
        }
	
        // get a new iterator
        buffer_iterator = event_list.iterator();

        // load up initial buffer
        update_note_buffer(0, 0);

        // get the chart sound samples
	sounds = new HashMap<Integer, Sound>();
        for(Entry<Integer, SampleData> entry : chart.getSamples().entrySet())
        {
            SampleData sampleData = entry.getValue();
            try {
                Sound sound = soundSystem.load(sampleData);
                sounds.put(entry.getKey(), sound);
            } catch (SoundSystemException ex) {
                java.util.logging.Logger.getLogger(Render.class.getName()).log(Level.SEVERE, "{0}", ex);
            }
	    try {
		entry.getValue().dispose();
	    } catch (IOException ex) {
		java.util.logging.Logger.getLogger(Render.class.getName()).log(Level.SEVERE, "{0}", ex);
	    }
	}
	
        trueTypeFont = new TrueTypeFont(new Font("Tahoma", Font.BOLD, 14), false);

        //clean up
        System.gc();

        // Non-blocking loading pause (5 seconds minimum)
        // Keep window visible with cover image, poll events to keep compositor happy
        double loadStartTime = SystemTimer.getTime();
        double loadDuration = 5000; // 5 seconds
        while (SystemTimer.getTime() - loadStartTime < loadDuration) {
            // Clear screen and render cover image
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL11.glLoadIdentity();

            // Render cover image (centered)
            if (coverSprite != null) {
                coverSprite.draw(0, 0);
            }

            // Poll events to keep compositor happy
            window.update();

            // Small sleep to prevent CPU spinning
            try {
                Thread.sleep(16); // ~60 FPS
            } catch (InterruptedException e) {
                break;
            }
        }

        lastLoopTime = SystemTimer.getTime();
        start_time = lastLoopTime + DELAY_TIME;
        
        try {
            String[] data = localMatchingServer.trim().split(":");
            if (data.length == 2) {
                String host = data[0];
                int port = Integer.parseInt(data[1]);
                localMatching = new Client(host, port, (long)audioLatency.getLatency());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        
        if (localMatching != null) {
            
            gameStarted = false;
            new Thread(localMatching).start();
            statusList.add(new StatusItem() {

                @Override
                public String getText() {
                    return "" + localMatching.getStatus();
                }

                @Override
                public boolean isVisible() { return true; }
            });
	
        } else if (!gameStarted) {
            
            statusList.add(new StatusItem() {

                @Override
                public String getText() {
                    return "Press any note button to start the game.";
                }

                @Override
                public boolean isVisible() { return !gameStarted; }
            });
            
        }
        
    }
    
    /**
     * HP values for each judgment and difficulty (original game specs).
     * Index: [difficulty][judgment] where difficulty: 0=Easy, 1=Normal, 2=Hard
     * Judgments: COOL=0, GOOD=1 (positive = gain), BAD=2, MISS=3 (negative = loss)
     */
    private static final int[][] HP_VALUES = {
        // COOL  GOOD   BAD   MISS
        {   3,    2,  -10,   -50},  // Easy (rank 0)
        {   2,    1,   -7,   -40},  // Normal (rank 1)
        {   1,    0,   -5,   -30},  // Hard (rank 2+)
    };

    /**
     * Initializes the life bar based on rank.
     * Uses original game scale: max life = 1000 units.
     */
    private void initLifeBar() {
        int maxLife = 1000; // Original game max life
        lifebar_entity.setLimit(maxLife);
        lifebar_entity.setNumber(maxLife);
    }

    /* make the rendering start */
    public void startRendering()
    {
        window.setGameWindowCallback(this);
        window.setTitle(chart.getArtist()+" - "+chart.getTitle());

        try{
            window.startRendering();
        }catch(OutOfMemoryError e) {
            System.gc();
            Logger.global.log(Level.SEVERE, "System out of memory ! baillin out !!{0}", e.getMessage());
            JOptionPane.showMessageDialog(null, "Fatal Error", "System out of memory ! baillin out !!",JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }

    }


    /**
    * Notification that a frame is being rendered. Responsible for
    * running game logic and rendering the scene.
    */
    @Override
    public void frameRendering()
    {
        // work out how long its been since the last update, this
        // will be used to calculate how far the entities should
        // move this loop
        double now = SystemTimer.getTime();
        double delta = now - lastLoopTime;
        lastLoopTime = now;
        lastFpsTime += delta;
        fps++;

        update_fps_counter();

        check_misc_keyboard();

        changeSpeed(delta); // TODO: is everything here really needed every frame ?
        updateGameSpeed(delta);

        if (!gameStarted && localMatching != null) {
            if (localMatching.isReady()) gameStarted = true;
        }

        if (gameStarted) {
            gameTime += delta * effectiveSpeed;
        }

        now = gameTime;

        if (AUTOSOUND) now -= audioLatency.getLatency();

        double now_display = now + displayLatency.getLatency();

        update_note_buffer(now, now_display);
        distance.update(now_display, delta);

        soundSystem.update();
	do_autoplay(now);
        Keyboard.poll();
        check_keyboard(now);

        // Create final copies for lambda capture
        final double finalNow = now;
        final double finalNowDisplay = now_display;

        // Process all layers and entities using optimized flat array iteration
        // Zero allocation, O(1) removals via shift-remove
        // Dead entities are released back to the pool for reuse
        entities_matrix.processAll(e -> {
            e.move(delta); // move the entity

            if (e instanceof TimeEntity) {
                TimeEntity te = (TimeEntity) e;
                // autoplays sounds play

                double timeToJudge = finalNow;

                if (e instanceof SoundEntity && AUTOSOUND) {
                    timeToJudge += audioLatency.getLatency();
                }

                if (te.getTime() - timeToJudge <= 0 && gameStarted) te.judgment();

                NoteEntity ne = e instanceof NoteEntity ? (NoteEntity) e : null;

                double y = getViewport() - distance.calculate(finalNowDisplay, te.getTime(), speed, ne);

                // TODO Fix this, maybe an option in the skin
                // o2jam overlaps 1 px of the note with the measure and, because of this
                // our skin should do it too xD
                if (e instanceof MeasureEntity) y -= 1;

                if (!(e instanceof BgaEntity))
                    e.setPos(e.getX(), y);

                if (e instanceof LongNoteEntity) {
                    LongNoteEntity lne = (LongNoteEntity) e;
                    double ey = getViewport() - distance.calculate(finalNowDisplay, lne.getEndTime(), speed, ne);
                    lne.setEndDistance(Math.abs(ey - y));
                }

                if (e instanceof NoteEntity) check_judgment((NoteEntity) e, finalNow);
            }

            // CRITICAL: Match original behavior exactly
            // Original: if(e.isDead()) j.remove(); else e.draw();
            // Entity marked dead during processing (e.g., in check_judgment) should NOT be drawn
            if (!e.isDead()) {
                e.draw();
            }
        }, e -> {
            // Release dead notes back to the pool for reuse
            if (e instanceof NoteEntity) {
                NoteEntity ne = (NoteEntity) e;
                // Remove from note_channels tracking list before releasing to pool
                int chIndex = ne.getChannel().ordinal();
                LinkedList<NoteEntity> channelList = note_channels[chIndex];
                if (channelList != null) {
                    channelList.remove(ne);
                }
                noteEntityPool.release(ne);
            } else if (e instanceof LongNoteEntity) {
                LongNoteEntity lne = (LongNoteEntity) e;
                // Remove from note_channels tracking list before releasing to pool
                int chIndex = lne.getChannel().ordinal();
                LinkedList<NoteEntity> channelList = note_channels[chIndex];
                if (channelList != null) {
                    channelList.remove(lne);
                }
                noteEntityPool.release(lne);
            }
        });

        int y = 300;
        
        for (String s : statusList) {
            trueTypeFont.drawString(780, y, s, 1, -1, TrueTypeFont.ALIGN_RIGHT);
            y += 30;
        }
        
        // TODO: THIS IS SPAGHETTI. IMPROVE SOON.
        y = 64;
        if (server != null) {
            trueTypeFont.drawString(780, y, "Server: " + server.getStatus(), 1, -1, TrueTypeFont.ALIGN_RIGHT);
            y += 24;
            for (Connection conn : server.getConnections()) {
                String s = conn.toString() + ": " + conn.getStatus();
                trueTypeFont.drawString(780, y, s, 1, -1, TrueTypeFont.ALIGN_RIGHT);
                y += 18;
            }
        }
        
        // Check if notes have ended (buffer empty and note layer empty)
        // Wait for music to finish playing before closing window
        if(!buffer_iterator.hasNext() && entities_matrix.isEmpty(note_layer)) {
            if (finish_time == -1) {
                // Notes ended - wait for audio tail to finish
                // Add a buffer time for audio to complete (original used 10 seconds)
                // Calculate remaining time based on chart duration vs current game time
                double remainingMusicTime = (chart.getDuration() * 1000.0) - gameTime;
                // Wait at least 3 seconds, or remaining music time if longer
                long waitTime = Math.max(5000, (long)remainingMusicTime + 5000);
                finish_time = System.currentTimeMillis() + waitTime;
                Logger.global.info("=== Notes ended === gameTime=" + gameTime + "ms, musicDuration=" + (chart.getDuration()*1000) + "ms, remaining=" + remainingMusicTime + "ms, waiting " + waitTime + "ms for audio tail");
            } else if (System.currentTimeMillis() > finish_time) {
                // Wait time has elapsed - music should have finished
                Logger.global.info("=== Music finished === currentTime=" + System.currentTimeMillis() + "ms, finish_time=" + finish_time + "ms - closing window...");
                // Song ended - stop audio FIRST, then close window
                // This prevents PipeWire crash from concurrent audio access
                soundSystem.release();

                // Small delay to ensure audio threads stop
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // Ignore
                }

                // Signal window to close - gameLoop will call destroy() automatically
                // destroy() will apply 2-second delay (or instant if ESC was pressed)
                window.stopRendering();
                Logger.global.info("Window stop signal sent (music ended naturally)");
            } else {
                // Still waiting for music to finish
                long timeUntilFinish = finish_time - System.currentTimeMillis();
                if (timeUntilFinish % 1000 < 50) {  // Log every second
                    Logger.global.info("Waiting for music... " + timeUntilFinish + "ms remaining");
                }
            }
        }
    }

    private void update_fps_counter()
    {
        // update our FPS counter if a second has passed
        if (lastFpsTime >= 1000) {
            Logger.global.log(Level.FINEST, "FPS: {0}", fps);
            fps_entity.setNumber(fps);
            lastFpsTime = lastFpsTime-1000;
            fps = 0;

            //the timer counter
            if(second_entity.getNumber() >= 59)
            {
                second_entity.setNumber(0);
                minute_entity.incNumber();
            }
            else
                second_entity.incNumber();
        }
    }
    
    int lastSpeedChangeMeasure = 0;
    int lastUpdateMeasure = 0;
    double lastSpeedChangeTime = 0;
    void updateGameSpeed(double delta) {
        
        int pitch = (int)Math.round(12.0 * Math.log(gameSpeed) / Math.log(2));
        
        effectiveSpeed = Math.pow(2, pitch / 12.0);
        effectiveJudgmentFactor = effectiveSpeed;
        
        if (pitchShift != pitch) {
            pitchShift = pitch;
            soundSystem.setSpeed((float)effectiveSpeed);
        }
        
        if (haste) {
            double maxSpeed = Math.min(2, Math.max(0.5, 3 * (double)lifebar_entity.getNumber() / lifebar_entity.getLimit()));
            if (gameMeasure > lastUpdateMeasure) {
                int measureDelta = gameMeasure - lastSpeedChangeMeasure;
                
                boolean increase = false;
    
                if (gameTime - lastSpeedChangeTime >= 5333 * Math.pow(Math.min(gameSpeed, 1.0), 4) && gameMeasure >= 6) {
                    if ((measureDelta & (measureDelta - 1)) == 0) increase = true;
                    if (measureDelta >= 8) increase = true;
                    if (lastSpeedChangeMeasure == 0) increase = true;
                }
                
                if (increase) {
                    gameSpeed = gameSpeed * Math.pow(2, 1 / 12.0);
                    lastSpeedChangeMeasure = gameMeasure;
                    lastSpeedChangeTime = gameTime;
                }
                
                lastUpdateMeasure = gameMeasure;
            }
            if (gameSpeed > maxSpeed) gameSpeed = maxSpeed;
            if (normalizeSpeed) {
                double target = 1 / gameSpeed;
                speedFactor += (target - speedFactor) * 0.1;
            }
        }
        
    }

    void do_autoplay(double now)
    {
        for (Event.Channel c : keyboard_map.keySet()) {
            if (!c.isAutoplay()) continue;
            NoteEntity ne = nextNoteKey(c);

            if (ne == null) continue;

            double hit = ne.testTimeHit(now);
            if (hit > AUTOPLAY_THRESHOLD) continue;
            ne.updateHit(now, effectiveJudgmentFactor);

            int chIndex = c.ordinal();
            if (ne instanceof LongNoteEntity) {
                if (ne.getState() == NoteEntity.State.NOT_JUDGED) {
                    disableAutoSound = false;
                    ne.keysound();
                    ne.setState(NoteEntity.State.LN_HEAD_JUDGE);
                    Entity ee = skin.getEntityMap().get("PRESSED_" + ne.getChannel()).copy();
                    entities_matrix.add(ee);
                    Entity to_kill = key_pressed_entity[chIndex];
                    key_pressed_entity[chIndex] = ee;
                    if (to_kill != null) to_kill.setDead(true);
                } else if (ne.getState() == NoteEntity.State.LN_HOLD) {
                    ne.setState(NoteEntity.State.JUDGE);
                    Entity lf = longflare[chIndex];
                    if (lf != null) lf.setDead(true);
                    longflare[chIndex] = null;
                    Entity kp = key_pressed_entity[chIndex];
                    if (kp != null) kp.setDead(true);
                }
            } else {
                disableAutoSound = false;
                ne.keysound();
                ne.setState(NoteEntity.State.JUDGE);
            }
        }
    }

    public boolean isDisableAutoSound() {
        return disableAutoSound;
    }

    public void check_keyboard(double now)
    {
        if (window.isKeyDown(Keyboard.KEY_RETURN) && server != null) {
            server.startGame();
            server = null;
        }

        // Iterate over keyboard_map using array indexing for primitive arrays
        for (Map.Entry<Event.Channel, Integer> entry : keyboard_map.entrySet()) {
            Event.Channel c = entry.getKey();
            if (c.isAutoplay()) continue;

            int chIndex = c.ordinal();
            boolean keyDown = window.isKeyDown(entry.getValue());
            boolean keyWasDown = keyboard_key_pressed[chIndex];

            if (keyDown && !keyWasDown) { // started holding now
                if (!gameStarted && localMatching == null) gameStarted = true;

                keyboard_key_pressed[chIndex] = true;
                Entity baseEntity = skin.getEntityMap().get("PRESSED_" + c);
                Entity to_kill = null;

                if (baseEntity != null) {
                    Entity ee = baseEntity.copy();
                    entities_matrix.add(ee);
                    to_kill = key_pressed_entity[chIndex];
                    key_pressed_entity[chIndex] = ee;
                }

                if (to_kill != null) to_kill.setDead(true);

                NoteEntity e = nextNoteKey(c);
                if (e == null) {
                    SampleEntity i = last_sound[chIndex];
                    if (i != null) i.extrasound();
                    continue;
                }

                e.updateHit(now, effectiveJudgmentFactor);

                // don't continue if the note is too far
                if (judge.accept(e)) {
                    disableAutoSound = false;
                    e.keysound();
                    if (e instanceof LongNoteEntity) {
                        longnote_holded[chIndex] = (LongNoteEntity) e;
                        if (e.getState() == NoteEntity.State.NOT_JUDGED)
                            e.setState(NoteEntity.State.LN_HEAD_JUDGE);
                    } else {
                        e.setState(NoteEntity.State.JUDGE);
                    }
                } else {
                    e.getSampleEntity().extrasound();
                }

            } else if (!keyDown && keyWasDown) { // key released now
                keyboard_key_pressed[chIndex] = false;
                Entity to_kill = key_pressed_entity[chIndex];

                if (to_kill != null) to_kill.setDead(true);

                Entity lf = longflare[chIndex];
                longflare[chIndex] = null;
                if (lf != null) lf.setDead(true);

                LongNoteEntity e = longnote_holded[chIndex];
                longnote_holded[chIndex] = null;
                if (e == null || e.getState() != NoteEntity.State.LN_HOLD) continue;

                e.updateHit(now, effectiveJudgmentFactor);
                e.setState(NoteEntity.State.JUDGE);
            }
        }
    }
    
    private void autosync(double hit) {
        if (syncingLatency == null) return;
        syncingLatency.autosync(hit);
    }
    
    public void check_judgment(NoteEntity ne, double now)
    {
        JudgmentResult result;
        
        switch (ne.getState())
        {
            case NOT_JUDGED: // you missed it (no keyboard input)
                ne.updateHit(now, effectiveJudgmentFactor);
                if (judge.missed(ne)) {
                    disableAutoSound = true;
                    setNoteJudgment(ne, JudgmentResult.MISS);
                }
                break;
                
            case JUDGE: //LN & normal ones: has finished with good result
                result = judge.judge(ne);
                setNoteJudgment(ne, result);
                
                if (!(ne instanceof LongNoteEntity)) {
                    autosync(ne.getHitTime());
                }
                break;
                
            case LN_HOLD:    // You kept too much time the note held that it misses
                ne.updateHit(now, effectiveJudgmentFactor);
                if (judge.missed(ne)) {
                    setNoteJudgment(ne, JudgmentResult.MISS);

                    // kill the long flare
                    int chIndex = ne.getChannel().ordinal();
                    Entity lf = longflare[chIndex];
                    longflare[chIndex] = null;
                    if (lf != null) lf.setDead(true);
                }
                break;

            case LN_HEAD_JUDGE: //LN: Head has been played

                result = judge.judge(ne);
                setNoteJudgment(ne, result);

                // display the long flare and kill the old one
                if (result != JudgmentResult.MISS) {
                    Entity ee = skin.getEntityMap().get("EFFECT_LONGFLARE").copy();
                    ee.setPos(ne.getX() + ne.getWidth() / 2 - ee.getWidth() / 2, ee.getY());
                    entities_matrix.add(ee);
                    int chIndex = ne.getChannel().ordinal();
                    Entity to_kill = longflare[chIndex];
                    longflare[chIndex] = ee;
                    if (to_kill != null) to_kill.setDead(true);

                    ne.setState(NoteEntity.State.LN_HOLD);
                } else {
                    System.out.println(ne.getTimeToJudge() + " - " + now);
                }
                break;
                
            case TO_KILL: // this is the "garbage collector", it just removes the notes off window
                
                if(ne.getY() >= window.getResolutionHeight())
                {
                    // kill it
                    ne.setDead(true);
                }
                
            break;
                
        }
        
    }
    
    public void setNoteJudgment(NoteEntity ne, JudgmentResult result) {
        
        result = handleJudgment(result);
        
        // stop the sound if missed
        if (result == JudgmentResult.MISS) {
            ne.missed();
        }
        
        // display the judgment
        if (judgment_entity != null) judgment_entity.setDead(true);
        judgment_entity = skin.getEntityMap().get("EFFECT_" + result).copy();
        entities_matrix.add(judgment_entity);

        // add to the statistics
        note_counter[result.ordinal()].incNumber();
        
        // for cool: display the effect
        if (result == JudgmentResult.COOL || result == JudgmentResult.GOOD) {
            Entity ee = skin.getEntityMap().get("EFFECT_CLICK").copy();
            ee.setPos(ne.getX()+ne.getWidth()/2-ee.getWidth()/2,
            getViewport()-ee.getHeight()/2);
            entities_matrix.add(ee);
        }
        
        // delete the note
        if (result == JudgmentResult.MISS || (ne instanceof LongNoteEntity)) {
            ne.setState(NoteEntity.State.TO_KILL);
        } else {
            ne.setDead(true);
        }
        
        // update combo
        if (shouldIncreaseCombo(result)) {
            combo_entity.incNumber();
        } else {
            combo_entity.resetNumber();
        }
        

    }

    public boolean shouldIncreaseCombo(JudgmentResult result) {
        if (result == null) return false;
        switch (result) {
            case BAD: case MISS: return false;
        }
        return true;
    }
    
    public JudgmentResult handleJudgment(JudgmentResult result) {

        int score_value = 0;

        switch(result)
        {
            case COOL:
                jambar_entity.changeNumber(2);
                consecutive_cools++;
                // HP gain: +3 (Easy), +2 (Normal), +1 (Hard)
                lifebar_entity.changeNumber(HP_VALUES[rank >= 2 ? 2 : rank][0]);
                score_value = 200 + (jamcombo_entity.getNumber()*10);
                break;

            case GOOD:
                jambar_entity.changeNumber(1);
                consecutive_cools = 0;
                // HP gain: +2 (Easy), +1 (Normal), +0 (Hard)
                lifebar_entity.changeNumber(HP_VALUES[rank >= 2 ? 2 : rank][1]);
                score_value = 100;
                break;

            case BAD:
                // Pill auto-activates on BAD - converts to COOL
                if(pills_draw.size() > 0)
                {
                    result = JudgmentResult.COOL;  // BAD → COOL conversion
                    jambar_entity.changeNumber(2);    // COOL gives 2 jamBar points
                    // consecutive_cools NOT incremented - pill save doesn't extend streak
                    // Streak is maintained but not extended (reset to 0 would break it)
                    pills_draw.removeLast().setDead(true);  // Consume 1 pill

                    // Full COOL score and HP gain
                    lifebar_entity.changeNumber(HP_VALUES[rank >= 2 ? 2 : rank][0]);
                    score_value = 200 + (jamcombo_entity.getNumber() * 10);
                }
                else
                {
                    jambar_entity.setNumber(0);
                    jamcombo_entity.resetNumber();
                    // HP loss: -10 (Easy), -7 (Normal), -5 (Hard)
                    lifebar_entity.changeNumber(HP_VALUES[rank >= 2 ? 2 : rank][2]);

                    score_value = 4;
                }
                consecutive_cools = 0;  // Reset streak (pill save doesn't count as real COOL)
            break;

            case MISS:
                jambar_entity.setNumber(0);
                jamcombo_entity.resetNumber();
                consecutive_cools = 0;

                // HP loss: -50 (Easy), -40 (Normal), -30 (Hard)
                lifebar_entity.changeNumber(HP_VALUES[rank >= 2 ? 2 : rank][3]);

                if(score_entity.getNumber() >= 10)score_value = -10;
                else score_value = -score_entity.getNumber();
            break;
        }
        
        score_entity.addNumber(score_value);

        if(jambar_entity.getNumber() >= jambar_entity.getLimit())
        {
            jambar_entity.setNumber(0); //reset
            jamcombo_entity.incNumber();
        }
        
        if(consecutive_cools >= 15 && pills_draw.size() < 5)
        {
            consecutive_cools -= 15;
            Entity ee = skin.getEntityMap().get("PILL_"+(pills_draw.size()+1)).copy();
            entities_matrix.add(ee);
            pills_draw.add(ee);
        }

        if(maxcombo_entity.getNumber()<(combo_entity.getNumber()))
        {
            maxcombo_entity.incNumber();
        }
        
        return result;

    }
    
    /* play a sample */
    public SoundInstance queueSample(Event.SoundSample soundSample)
    {
        if(soundSample == null) return null;
	
	Sound sound = sounds.get(soundSample.sample_id);
        if(sound == null)return null;
        
        try {
            return sound.play(soundSample.isBGM() ? SoundChannel.BGM : SoundChannel.KEY,
                    1.0f, soundSample.pan);
        } catch (SoundSystemException ex) {
            java.util.logging.Logger.getLogger(Render.class.getName()).log(Level.SEVERE, "{0}", ex);
            return null;
        }
    }
    
    private void change_bgm_volume(float factor)
    {
        opt.setBGMVolume(opt.getBGMVolume() + factor);
        soundSystem.setBGMVolume(opt.getBGMVolume());
    }
    
    private void change_key_volume(float factor)
    {
        opt.setKeyVolume(opt.getKeyVolume() + factor);
        soundSystem.setKeyVolume(opt.getKeyVolume());
    }
            
    private void changeSpeed(double delta)
    {
        speedObj.update(delta);
        speed = speedObj.getCurrentSpeed();
    }

    double getViewport() { return skin.getJudgmentLine(); }

    /* this returns the next note that needs to be played
     ** of the defined channel or NULL if there's
     ** no such note in the moment **/
    NoteEntity nextNoteKey(Event.Channel c)
    {
        int chIndex = c.ordinal();
        if (note_channels[chIndex].isEmpty()) return null;
        NoteEntity ne = note_channels[chIndex].getFirst();
        while (ne.getState() != NoteEntity.State.NOT_JUDGED &&
               ne.getState() != NoteEntity.State.LN_HOLD) {
            note_channels[chIndex].removeFirst();
            if (note_channels[chIndex].isEmpty()) return null;
            ne = note_channels[chIndex].getFirst();
        }
        last_sound[chIndex] = ne.getSampleEntity();
        return ne;
    }

    private double buffer_timer = 0;
    

    /* update the note layer of the entities_matrix.
    *** note buffering is equally distributed between the frames
    **/
    void update_note_buffer(double now, double now_display)
    {
        while (buffer_iterator.hasNext() && getViewport() - distance.calculate(now_display, buffer_timer, speed, null) > -10) {
            Event e = buffer_iterator.next();

            buffer_timer = e.getTime();

            switch (e.getChannel()) {
                case MEASURE:
                    MeasureEntity m = (MeasureEntity) skin.getEntityMap().get("MEASURE_MARK").copy();
                    m.setTime(e.getTime());
                    m.setOnJudge(increaseMeasureRunnable);
                    entities_matrix.add(m);
                break;

                case NOTE_1: case NOTE_2:
                case NOTE_3: case NOTE_4:
                case NOTE_5: case NOTE_6: case NOTE_7:
                if (e.getFlag() == Event.Flag.NONE) {
                    int chIndex = e.getChannel().ordinal();
                    if (ln_buffer[chIndex] != null)
                        Logger.global.log(Level.WARNING, "There is a none in the current long {0} @ " + e.getTotalPosition(), e.getChannel());
                    // Use pooled note entity instead of copy()
                    NoteEntity n = noteEntityPool.acquireNote(e.getChannel(), e.getTime());
                    if (n != null) {
                        assignSample(n, e);
                        entities_matrix.add(n);
                        note_channels[chIndex].add(n);
                    } else {
                        Logger.global.log(Level.SEVERE, "Note pool exhausted! Channel: " + e.getChannel());
                    }
                } else if (e.getFlag() == Event.Flag.HOLD) {
                    int chIndex = e.getChannel().ordinal();
                    if (ln_buffer[chIndex] != null)
                        Logger.global.log(Level.WARNING, "There is a hold in the current long {0} @ " + e.getTotalPosition(), e.getChannel());
                    // Use pooled long note entity instead of copy()
                    LongNoteEntity ln = noteEntityPool.acquireLongNote(e.getChannel(), e.getTime());
                    if (ln != null) {
                        assignSample(ln, e);
                        entities_matrix.add(ln);
                        ln_buffer[chIndex] = ln;
                        note_channels[chIndex].add(ln);
                    } else {
                        Logger.global.log(Level.SEVERE, "Long note pool exhausted! Channel: " + e.getChannel());
                    }
                } else if (e.getFlag() == Event.Flag.RELEASE) {
                    int chIndex = e.getChannel().ordinal();
                    LongNoteEntity lne = ln_buffer[chIndex];
                    ln_buffer[chIndex] = null;
                    if (lne == null) {
                        Logger.global.log(Level.WARNING, "Attempted to RELEASE note {0} @ " + e.getTotalPosition(), e.getChannel());
                    } else {
                        lne.setEndTime(e.getTime());
                    }
                }
                break;
                case BGA:
                    if (!bgaEntity.isVideo) {
                        Sprite sprite = null;
                        if (bga_sprites.containsKey((int) e.getValue()))
                            sprite = bga_sprites.get((int) e.getValue());
                        if (sprite == null) break;
                        sprite.setScale(1f, 1f);
                        bgaEntity.setSprite(sprite);
                    }

                    bgaEntity.setTime(e.getTime());
                break;

                //TODO ADD SUPPORT
                case NOTE_SC:
                case NOTE_8: case NOTE_9:
                case NOTE_10: case NOTE_11:
                case NOTE_12: case NOTE_13: case NOTE_14:
                case NOTE_SC2:

                case AUTO_PLAY:
                    autoSound(e, true);
                break;
            }
        }
    }
    
    private void assignSample(NoteEntity n, Event e) {
        SampleEntity sampleEntity = createSampleEntity(e, false);
        if(AUTOSOUND) {
            autoSound(sampleEntity);
            sampleEntity.setNote(true);
        }
        n.setSampleEntity(sampleEntity);
    }
    
    private SampleEntity autoSound(Event e, boolean bgm)
    {
        return autoSound(createSampleEntity(e, bgm));
    }
    
    private SampleEntity autoSound(SampleEntity se) {
        entities_matrix.add(se);
        return se;
    }
    
    private SampleEntity createSampleEntity(Event e, boolean bgm) {
	if(bgm) e.getSample().toBGM();
        SampleEntity s = new SampleEntity(this,e.getSample(),0);
        s.setTime(e.getTime());
        return s;
    }

    private final List<Integer> misc_keys = new LinkedList<Integer>();

    void check_misc_keyboard()
    {
	    for(Map.Entry<Config.MiscEvent,Integer> entry : keyboard_misc.entrySet())
        {
            Config.MiscEvent event  = entry.getKey();

            if(window.isKeyDown(entry.getValue()) && !misc_keys.contains(entry.getValue())) // this key is being pressed
            {
                misc_keys.add(entry.getValue());
                switch(event)
                {
                    case SPEED_UP:
                        speedObj.increase();
                    break;
                    case SPEED_DOWN:
                        speedObj.decrease();
                    break;
                    case MAIN_VOL_UP:
                        opt.setMasterVolume(opt.getMasterVolume() + VOLUME_FACTOR);
                        soundSystem.setMasterVolume(opt.getMasterVolume());
                    break;
                    case MAIN_VOL_DOWN:
                        opt.setMasterVolume(opt.getMasterVolume() - VOLUME_FACTOR);
                        soundSystem.setMasterVolume(opt.getMasterVolume());
                    break;
                    case KEY_VOL_UP:
                        change_key_volume(VOLUME_FACTOR);
                    break;
                    case KEY_VOL_DOWN:
                        change_key_volume(-VOLUME_FACTOR);
                    break;
                    case BGM_VOL_UP:
                        change_bgm_volume(VOLUME_FACTOR);
                    break;
                    case BGM_VOL_DOWN:
                        change_bgm_volume(-VOLUME_FACTOR);
                    break;
                }
            }
            else if(!window.isKeyDown(entry.getValue()) && misc_keys.contains(entry.getValue()))
            {
                misc_keys.remove(entry.getValue());
            }
        }
    }

    private EventList construct_velocity_tree(EventList list)
    {
        int measure = 0;
        double timer = DELAY_TIME;
        double my_bpm = this.bpm;
        double frac_measure = 1;
        double measure_pointer = 0;
        double measure_size = 0.8 * getViewport();
        double my_note_speed = (my_bpm * measure_size) / BEATS_PER_MSEC;
        
	double event_position;

        EventList new_list = new EventList();
	
        timing.add(timer, bpm);
        
	//there is always a 1st measure
	Event m = new Event(Event.Channel.MEASURE, measure, 0, 0, Event.Flag.NONE);
	m.setTime(timer);
	new_list.add(m);

        for(Event e : list)
        {
            while(e.getMeasure() > measure)
            {
                timer += (BEATS_PER_MSEC * (frac_measure-measure_pointer)) / my_bpm;
                m = new Event(Event.Channel.MEASURE, measure, 0, 0, Event.Flag.NONE);
                m.setTime(timer);
                new_list.add(m);
                measure++;
                frac_measure = 1;
                measure_pointer = 0;
            }

	    if(chart.type == Chart.TYPE.OJN) {
		event_position = e.getPosition();
	    } else {
		event_position = e.getPosition() * frac_measure;
	    }
            timer += (BEATS_PER_MSEC * (event_position-measure_pointer)) / my_bpm;
            measure_pointer = event_position;

            switch(e.getChannel())
            {
		case STOP:
                    timing.add(timer, 0);
		    double stop_time = e.getValue();
		    if(chart.type == Chart.TYPE.BMS) {
			stop_time = (e.getValue() / 192) * BEATS_PER_MSEC / my_bpm;
		    }
                    timing.add(timer + stop_time, my_bpm);
		    timer += stop_time;
		break;
		case BPM_CHANGE:
                    my_bpm = e.getValue();
                    timing.add(timer, my_bpm);
                break;
                case TIME_SIGNATURE:
                    frac_measure = e.getValue();
                break;

                case NOTE_1: case NOTE_2:
                case NOTE_3: case NOTE_4:
                case NOTE_5: case NOTE_6: case NOTE_7:
                case NOTE_SC:
                case NOTE_8: case NOTE_9:
                case NOTE_10: case NOTE_11:
                case NOTE_12: case NOTE_13: case NOTE_14:
                case NOTE_SC2:
                case AUTO_PLAY:
                case BGA:
                {
                    int chIndex = e.getChannel().ordinal();
                    if (last_sound[chIndex] == null && e.getSample() != null) {
                        last_sound[chIndex] = createSampleEntity(e, false);
                    }

                    e.setTime(timer + e.getOffset());
                    if (e.getOffset() != 0) System.out.println("offset: " + e.getOffset() + " timer: " + (timer + e.getOffset()));
                }
                break;
                    
                case MEASURE:
                    Logger.global.log(Level.WARNING, "...THE FUCK? Why is a measure event here?");
                break;
            }
            
            new_list.add(e);
        }
        
        timing.finish();
        
        return new_list;
    }

    private void visibility(GameOptions.VisibilityMod value)
    {
        int height = 0;
        int width  = 0;

        Sprite rec = null;
        // We will make a new entity with the masking rectangle for each note lane
        // because we can't know for sure where the notes will be,
        // meaning that they may not be together
        for(Event.Channel ev : Event.Channel.values())
        {
            if(ev.toString().startsWith("NOTE_") && skin.getEntityMap().get(ev.toString()) != null)
            {
                height = (int)Math.round(getViewport());
                width = (int)Math.round(skin.getEntityMap().get(ev.toString()).getWidth());
                rec  = ResourceFactory.get().doRectangle(width, height, value);
                visibility_entity.getEntityList().add(new Entity(rec, skin.getEntityMap().get(ev.toString()).getX(), 0));
            }
        }

        int layer = note_layer+1;

        for(Entity e : skin.getEntityList())
            if(e.getLayer() > layer) layer++;

        visibility_entity.setLayer(++layer);

        for(Entity e : skin.getAllEntities())
        {
            int l = e.getLayer();
            if(l >= layer)
                e.setLayer(++l);
        }

        // FIXME this is a hack
        if(value != GameOptions.VisibilityMod.Sudden)skin.getEntityMap().get("JUDGMENT_LINE").setLayer(layer);
        skin.getEntityMap().get("MEASURE_MARK").setLayer(layer);
        
        entities_matrix.add(visibility_entity);
    }

    /**
     * Notification that the game window has been closed
     */
    @Override
    public void windowClosed() {
        // First signal the render thread to stop
        window.stopRendering();
        
        // Small delay to let render thread exit gracefully
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // Ignore
        }
        
        // Now safe to release audio
        bgaEntity.release();
        soundSystem.release();
        System.gc();
        
        if (syncingLatency != null && autosyncCallback != null) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    autosyncCallback.autosyncFinished(syncingLatency.getLatency());
                }
            });
        }
    }
    
    private double clamp(double value, double min, double max)
    {
        return Math.min(Math.max(value, min), max);
    }
}
