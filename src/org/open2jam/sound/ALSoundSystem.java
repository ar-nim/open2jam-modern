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
 * Features a pool of 200 pre-allocated sources with ticket-based allocation
 * to prevent audio exhaustion and ensure proper source recycling.
 */
public class ALSoundSystem implements SoundSystem {

    private static final Logger logger = Logger.getLogger(ALSoundSystem.class.getName());

    // OpenAL context and device
    private long device;
    private long context;

    // Source pool management
    private static final int MAX_SOURCES = 200;
    final int[] sourcePool = new int[MAX_SOURCES];
    final boolean[] sourceInUse = new boolean[MAX_SOURCES];
    final int[] sourceTickets = new int[MAX_SOURCES];
    private int nextTicket = 0;

    // Volume controls
    float masterVolume = 1.0f;
    float bgmVolume = 1.0f;
    float keyVolume = 1.0f;

    // Speed/pitch control
    float speed = 1.0f;

    // Gain filters for channel groups (applied at play time)
    private final Map<Integer, Sound> loadedSounds = new HashMap<>();

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

        // Create context with desired attributes
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer contextAttribs = stack.mallocInt(1);
            contextAttribs.put(0).flip(); // No special attributes

            DebugLogger.debug("Creating OpenAL context...");
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
        // Pre-allocate all sources
        for (int i = 0; i < MAX_SOURCES; i++) {
            sourcePool[i] = AL10.alGenSources();
            sourceInUse[i] = false;
            sourceTickets[i] = -1;
        }
        DebugLogger.debug("Initialized " + MAX_SOURCES + " OpenAL sources");
    }

    @Override
    public Sound load(SampleData sampleData) throws SoundSystemException {
        try {
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
        // Recycle completed sources
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (sourceInUse[i]) {
                int state = AL10.alGetSourcei(sourcePool[i], AL10.AL_SOURCE_STATE);
                if (state != AL10.AL_PLAYING) {
                    sourceInUse[i] = false;
                    sourceTickets[i] = -1;
                }
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
     * Acquire a source from the pool.
     * @return source index in pool, or -1 if none available
     */
    int acquireSource() {
        // First, try to find an already-free source
        for (int i = 0; i < MAX_SOURCES; i++) {
            if (!sourceInUse[i]) {
                sourceInUse[i] = true;
                sourceTickets[i] = nextTicket++;
                return i;
            }
        }

        // All sources in use - try to steal one that's done playing
        for (int i = 0; i < MAX_SOURCES; i++) {
            int state = org.lwjgl.openal.AL10.alGetSourcei(sourcePool[i], org.lwjgl.openal.AL10.AL_SOURCE_STATE);
            if (state != org.lwjgl.openal.AL10.AL_PLAYING) {
                sourceTickets[i] = nextTicket++;
                return i;
            }
        }

        return -1; // No sources available
    }

    /**
     * Release a source back to the pool.
     * @param sourceIndex index in the pool
     */
    void releaseSource(int sourceIndex) {
        if (sourceIndex >= 0 && sourceIndex < MAX_SOURCES) {
            org.lwjgl.openal.AL10.alSourceStop(sourcePool[sourceIndex]);
            sourceInUse[sourceIndex] = false;
            sourceTickets[sourceIndex] = -1;
        }
    }
}
