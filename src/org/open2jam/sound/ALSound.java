package org.open2jam.sound;

import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

/**
 * OpenAL sound buffer wrapper.
 * Supports both PCM WAV and OGG Vorbis formats.
 */
public class ALSound implements Sound {

    private final ALSoundSystem soundSystem;
    private final int bufferId;
    private final ByteBuffer rawData;
    private boolean disposed = false;

    public ALSound(ALSoundSystem soundSystem, ByteBuffer rawData) throws SoundSystemException {
        this.soundSystem = soundSystem;
        this.rawData = rawData;
        this.bufferId = createBuffer(rawData);
    }

    private int createBuffer(ByteBuffer audioData) throws SoundSystemException {
        int bufferId = AL10.alGenBuffers();
        if (bufferId == 0) {
            throw new SoundSystemException("Failed to generate OpenAL buffer");
        }

        // Check if this is OGG data (look for OggS signature)
        audioData.mark();
        boolean isOgg = audioData.remaining() >= 4 && 
                        audioData.get() == 'O' && 
                        audioData.get() == 'g' && 
                        audioData.get() == 'g' && 
                        audioData.get() == 'S';
        audioData.reset();

        if (isOgg) {
            // Decode OGG Vorbis to PCM using STB Vorbis
            createBufferFromOgg(audioData, bufferId);
        } else {
            // Assume PCM WAV
            createBufferFromWav(audioData, bufferId);
        }
        
        return bufferId;
    }

    private void createBufferFromWav(ByteBuffer audioData, int bufferId) throws SoundSystemException {
        // 1. WAV files are strictly Little-Endian
        audioData.order(ByteOrder.LITTLE_ENDIAN);

        int format = AL10.AL_FORMAT_MONO16;
        int sampleRate = 44100;

        // Try to detect format from WAV header
        if (audioData.remaining() > 44) {
            // Read format info from WAV header
            audioData.position(22);
            int channels = audioData.getShort() & 0xFFFF;
            
            audioData.position(24);
            sampleRate = audioData.getInt();
            
            audioData.position(34);
            int bitsPerSample = audioData.getShort() & 0xFFFF;

            if (channels == 1 && bitsPerSample == 8) {
                format = AL10.AL_FORMAT_MONO8;
            } else if (channels == 1 && bitsPerSample == 16) {
                format = AL10.AL_FORMAT_MONO16;
            } else if (channels == 2 && bitsPerSample == 8) {
                format = AL10.AL_FORMAT_STEREO8;
            } else if (channels == 2 && bitsPerSample == 16) {
                format = AL10.AL_FORMAT_STEREO16;
            }
            
            // Skip the header entirely. Move position to start of audio data.
            audioData.position(44);
        }

        // alBufferData reads from current position (44) to limit
        AL10.alBufferData(bufferId, format, audioData, sampleRate);
        
        // Check for OpenAL errors
        int err = AL10.alGetError();
        if (err != AL10.AL_NO_ERROR) {
            throw new SoundSystemException("OpenAL buffer error: " + err);
        }
    }

    private void createBufferFromOgg(ByteBuffer audioData, int bufferId) throws SoundSystemException {
        // Use MemoryStack for temporary native allocations
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuffer = stack.mallocInt(1);
            IntBuffer sampleRateBuffer = stack.mallocInt(1);

            // Decode the Ogg file into raw 16-bit PCM samples
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(audioData, channelsBuffer, sampleRateBuffer);
            
            if (pcm == null) {
                throw new SoundSystemException("Failed to decode Ogg Vorbis file. Is the file corrupt?");
            }

            int channels = channelsBuffer.get(0);
            int sampleRate = sampleRateBuffer.get(0);
            
            // STB Vorbis always decodes to 16-bit audio
            int format;
            if (channels == 1) {
                format = AL10.AL_FORMAT_MONO16;
            } else if (channels == 2) {
                format = AL10.AL_FORMAT_STEREO16;
            } else {
                MemoryUtil.memFree(pcm); // Clean up before throwing
                throw new SoundSystemException("Unsupported Ogg Vorbis channel count: " + channels);
            }

            // Hand the raw, decoded PCM data to OpenAL
            AL10.alBufferData(bufferId, format, pcm, sampleRate);

            // Free the decoded PCM buffer now that OpenAL has copied it into its own memory
            MemoryUtil.memFree(pcm);
            
            // Check for OpenAL errors
            int err = AL10.alGetError();
            if (err != AL10.AL_NO_ERROR) {
                throw new SoundSystemException("OpenAL buffer error: " + err);
            }
        }
    }

    public int getBufferId() {
        return bufferId;
    }

    @Override
    public SoundInstance play(SoundChannel channel, float volume, float pan) throws SoundSystemException {
        if (disposed) {
            throw new SoundSystemException("Cannot play disposed sound");
        }
        return new ALSoundInstance(soundSystem, this, channel, volume, pan);
    }

    public void dispose() {
        if (!disposed) {
            AL10.alDeleteBuffers(bufferId);
            // Free native memory (already direct, no double-copy)
            if (rawData != null && rawData.isDirect()) {
                MemoryUtil.memFree(rawData);
            }
            disposed = true;
        }
    }

    public boolean isDisposed() {
        return disposed;
    }
}
