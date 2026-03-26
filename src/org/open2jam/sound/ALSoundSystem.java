package org.open2jam.sound;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.open2jam.parsers.utils.SampleData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.*;
import java.util.logging.Level;
import org.open2jam.util.DebugLogger;
import java.util.logging.Logger;

/**
 * OpenAL sound system implementation.
 * Features a pool of 200 pre-allocated sources with O(1) free-list allocation
 * for minimal keysound latency.
 * 
 * Low-latency optimizations:
 * - Free-list stack for O(1) source acquisition/release
 * - Active source list for efficient update() polling
 * - Cached per-source state to skip redundant AL calls
 * - Pre-bound buffers for keysounds (alSourcePlay only on trigger)
 */
public class ALSoundSystem implements SoundSystem {

    private static final Logger logger = Logger.getLogger(ALSoundSystem.class.getName());

    // OpenAL context and device
    private long device;
    private long context;

    // Source pool management - O(1) free-list stack
    private static final int MAX_SOURCES = 256;
    final int[] sourcePool = new int[MAX_SOURCES];
    final int[] sourceTickets = new int[MAX_SOURCES];
    private int nextTicket = 0;
    
    // Free-list stack for O(1) allocation
    private final int[] freeStack = new int[MAX_SOURCES];
    private int freeCount = MAX_SOURCES;
    
    // Active source list for efficient update() polling
    private final int[] activeSources = new int[MAX_SOURCES];
    private int activeCount = 0;

    // Volume controls
    float masterVolume = 1.0f;
    float bgmVolume = 1.0f;
    float keyVolume = 1.0f;

    // Speed/pitch control
    float speed = 1.0f;

    // Gain filters for channel groups (applied at play time)
    private final Map<Integer, Sound> loadedSounds = new HashMap<>();
    
    // Per-source state caching to skip redundant AL calls (package-private for ALSoundInstance)
    final float[] sourceLastGain = new float[MAX_SOURCES];
    final float[] sourceLastPitch = new float[MAX_SOURCES];
    final float[] sourceLastPanX = new float[MAX_SOURCES];
    final float[] sourceLastPanY = new float[MAX_SOURCES];
    final float[] sourceLastPanZ = new float[MAX_SOURCES];
    static final float EPSILON = 0.001f;

    public ALSoundSystem() throws SoundSystemException {
        // Don't initialize here - wait for initializeWithCurrentContext()
        // This allows OpenAL to be initialized on the rendering thread
        // with a current OpenGL context
        DebugLogger.debug("OpenAL sound system created (pending initialization)");
    }
    
    /**
     * Initialize OpenAL with a current OpenGL context.
     * Must be called from the thread where OpenAL will be used.
     */
    public void initializeWithCurrentContext() throws SoundSystemException {
        try {
            initializeOpenAL();
            initializeSourcePool();
            DebugLogger.debug("Audio engine: OpenAL (LWJGL 3)");
        } catch (Exception e) {
            throw new SoundSystemInitException("Failed to initialize OpenAL: " + e.getMessage(), e);
        }
    }

    private void initializeOpenAL() throws SoundSystemException {
        DebugLogger.debug("Opening OpenAL device...");
        // Open default device
        device = ALC10.alcOpenDevice((ByteBuffer) null);
        if (device == 0) {
            throw new SoundSystemException("Failed to open OpenAL device");
        }
        DebugLogger.debug("OpenAL device opened: " + device);

        // Create context with low-latency attributes for rhythm game keysounds
        // These attributes optimize OpenAL Soft for minimal audio latency
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // Low-latency context attributes:
            // - ALC_FREQUENCY: Match sample rate (44100Hz) to avoid resampling latency
            // Note: period_size and periods should be set in ~/.alsoftrc or ALSOFT_CONF env:
            //   [general]
            //   period_size = 256   (lower = lower latency, default is 1024)
            //   periods = 2         (minimum double-buffering)
            IntBuffer contextAttribs = stack.mallocInt(5);
            contextAttribs.put(ALC10.ALC_FREQUENCY).put(44100);
            contextAttribs.put(0); // Terminator (first null)
            contextAttribs.put(0); // Second null for alignment
            contextAttribs.flip();

            DebugLogger.debug("Creating OpenAL context with low-latency attributes...");
            DebugLogger.debug("  - Frequency: 44100Hz");
            DebugLogger.debug("  Note: For lowest latency, set period_size=256 in ~/.alsoftrc");
            
            context = ALC10.alcCreateContext(device, contextAttribs);
            if (context == 0) {
                ALC10.alcCloseDevice(device);
                throw new SoundSystemException("Failed to create OpenAL context");
            }
            DebugLogger.debug("OpenAL context created: " + context);

            // Make context current FIRST - this is critical for thread-local state
            DebugLogger.debug("Making context current on thread: " + Thread.currentThread().getName());
            if (!ALC10.alcMakeContextCurrent(context)) {
                ALC10.alcDestroyContext(context);
                ALC10.alcCloseDevice(device);
                throw new SoundSystemException("Failed to make OpenAL context current");
            }
            DebugLogger.debug("Context is now current");

            // 1. Create the hardware device capabilities (ALC)
            DebugLogger.debug("Creating ALC capabilities for device...");
            org.lwjgl.openal.ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            DebugLogger.debug("ALC capabilities created: OpenALC10=" + deviceCaps.OpenALC10);

            // 2. Create the audio library capabilities (AL) for the current thread
            // This is REQUIRED - AL10 functions won't work without this!
            DebugLogger.debug("Creating AL capabilities for current thread...");
            AL.createCapabilities(deviceCaps);
            DebugLogger.debug("AL capabilities created");

            // Verify AL is working by calling a simple function
            int err = AL10.alGetError();
            DebugLogger.debug("OpenAL initialized, alGetError() = " + err);
        }
    }

    private void initializeSourcePool() {
        // Pre-allocate all sources and initialize free-list stack
        for (int i = 0; i < MAX_SOURCES; i++) {
            sourcePool[i] = AL10.alGenSources();
            sourceTickets[i] = -1;
            freeStack[i] = i; // Initialize free-list stack
            sourceLastGain[i] = -1.0f; // Invalidate cache
            sourceLastPitch[i] = -1.0f;
            sourceLastPanX[i] = -999.0f;
            sourceLastPanY[i] = -999.0f;
            sourceLastPanZ[i] = -999.0f;
        }
        freeCount = MAX_SOURCES;
        activeCount = 0;
        DebugLogger.debug("Initialized " + MAX_SOURCES + " OpenAL sources with free-list stack");
    }

    @Override
    public Sound load(SampleData sampleData) throws SoundSystemException {
        try {
            // Direct ByteBuffer loading - skip double-copy
            // Stream directly from SampleData to native ByteBuffer
            // Note: We still need to copy via ByteArrayOutputStream since SampleData
            // doesn't expose size upfront, but this is a load-time operation only
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sampleData.copyTo(out);
            byte[] audioData = out.toByteArray();
            ByteBuffer buffer = MemoryUtil.memAlloc(audioData.length);
            buffer.put(audioData);
            buffer.flip();

            ALSound sound = new ALSound(this, buffer);
            loadedSounds.put(sound.getBufferId(), sound);
            return sound;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load sound: {0}", e.getMessage());
            throw new SoundSystemException("Failed to load sound: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() {
        // DO NOT actually release OpenAL - it should stay alive for the entire app lifetime
        // Only stop sources and clear buffers, but keep the context/device alive
        // This allows multiple songs to be played without re-initializing OpenAL
        
        // 1. Stop all sources (don't delete - reuse them)
        for (int i = 0; i < sourcePool.length; i++) {
            if (sourcePool[i] != 0) {
                org.lwjgl.openal.AL10.alSourceStop(sourcePool[i]);
                // Don't delete sources - reuse them for next song
            }
        }

        // 2. Clean up sound buffers
        for (Sound sound : loadedSounds.values()) {
            if (sound instanceof ALSound) {
                ((ALSound) sound).dispose();
            }
        }
        loadedSounds.clear();

        // DO NOT destroy context/device/ALC - keep them alive for next song!
        // Only destroy at application shutdown via shutdown hook

        DebugLogger.debug("OpenAL sound system stopped (context kept alive)");
    }

    @Override
    public void update() {
        // Recycle completed sources using active list (more efficient than scanning all 200)
        for (int i = 0; i < activeCount; i++) {
            int sourceIndex = activeSources[i];
            int state = AL10.alGetSourcei(sourcePool[sourceIndex], AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                // Source finished - release back to pool
                releaseSource(sourceIndex);
                // Decrement index since we swapped with last
                i--;
            }
        }
    }

    @Override
    public void setBGMVolume(float factor) {
        this.bgmVolume = Math.max(0, Math.min(1, factor));
    }

    @Override
    public void setKeyVolume(float factor) {
        this.keyVolume = Math.max(0, Math.min(1, factor));
    }

    @Override
    public void setMasterVolume(float factor) {
        this.masterVolume = Math.max(0, Math.min(1, factor));
    }

    @Override
    public void setSpeed(float factor) {
        this.speed = Math.max(0.5f, Math.min(2.0f, factor));
    }

    /**
     * Acquire a source from the pool using O(1) free-list stack.
     * @return source index in pool, or -1 if none available
     */
    int acquireSource() {
        // O(1) acquire from free-list stack
        if (freeCount > 0) {
            int sourceIndex = freeStack[--freeCount];
            sourceTickets[sourceIndex] = nextTicket++;
            
            // Add to active sources list
            if (activeCount < MAX_SOURCES) {
                activeSources[activeCount++] = sourceIndex;
            }
            
            return sourceIndex;
        }

        // No free sources - try to steal one from active list that's done playing
        for (int i = 0; i < activeCount; i++) {
            int sourceIndex = activeSources[i];
            int state = AL10.alGetSourcei(sourcePool[sourceIndex], AL10.AL_SOURCE_STATE);
            if (state != AL10.AL_PLAYING) {
                sourceTickets[sourceIndex] = nextTicket++;
                return sourceIndex;
            }
        }

        return -1; // No sources available
    }

    /**
     * Release a source back to the pool using O(1) free-list stack.
     * @param sourceIndex index in the pool
     */
    void releaseSource(int sourceIndex) {
        if (sourceIndex >= 0 && sourceIndex < MAX_SOURCES) {
            AL10.alSourceStop(sourcePool[sourceIndex]);
            sourceTickets[sourceIndex] = -1;
            
            // Invalidate cached state
            sourceLastGain[sourceIndex] = -1.0f;
            sourceLastPitch[sourceIndex] = -1.0f;
            sourceLastPanX[sourceIndex] = -999.0f;
            sourceLastPanY[sourceIndex] = -999.0f;
            sourceLastPanZ[sourceIndex] = -999.0f;
            
            // Return to free-list stack (O(1))
            if (freeCount < MAX_SOURCES) {
                freeStack[freeCount++] = sourceIndex;
            }
            
            // Remove from active sources list (swap with last for O(1))
            for (int i = 0; i < activeCount; i++) {
                if (activeSources[i] == sourceIndex) {
                    activeSources[i] = activeSources[--activeCount];
                    break;
                }
            }
        }
    }
}
