package org.open2jam.sound;

import org.lwjgl.openal.AL10;

/**
 * OpenAL sound instance with source pooling and latency optimizations.
 * Manages playback of a single sound on an OpenAL source from the pool.
 * 
 * Optimizations:
 * - Pre-bound buffer to source (only alSourcePlay on trigger for keysounds)
 * - Cached gain/pitch/pan to skip redundant AL calls
 * - O(1) source acquisition from free-list stack
 */
public class ALSoundInstance implements SoundInstance {

    private final ALSoundSystem soundSystem;
    private final ALSound sound;
    private final SoundChannel channel;
    private final int sourceIndex;
    private final int ticket;
    private boolean stopped = false;

    public ALSoundInstance(ALSoundSystem soundSystem, ALSound sound, SoundChannel channel, float volume, float pan) {
        this.soundSystem = soundSystem;
        this.sound = sound;
        this.channel = channel;
        this.sourceIndex = soundSystem.acquireSource();
        this.ticket = sourceIndex >= 0 ? soundSystem.sourceTickets[sourceIndex] : -1;

        if (sourceIndex >= 0) {
            int sourceId = soundSystem.sourcePool[sourceIndex];

            // Attach buffer
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, sound.getBufferId());

            // Set volume with channel-specific scaling
            // Volume chain: noteVolume × channelVolume (BGM/Key) × masterVolume
            float channelVolume = channel == SoundChannel.BGM ?
                    soundSystem.bgmVolume : soundSystem.keyVolume;
            float finalVolume = volume * channelVolume * soundSystem.masterVolume;

            // Clamp volume to valid OpenAL range [0.0, 1.0] to prevent errors
            float clampedGain = Math.max(0.0f, Math.min(1.0f, finalVolume));
            
            // Cached gain set - skip if unchanged
            if (Math.abs(soundSystem.sourceLastGain[sourceIndex] - clampedGain) > ALSoundSystem.EPSILON) {
                AL10.alSourcef(sourceId, AL10.AL_GAIN, clampedGain);
                soundSystem.sourceLastGain[sourceIndex] = clampedGain;
            }

            // Set pan (-1.0 = left, 0.0 = center, 1.0 = right)
            // Constant Power Panning (Sine Law) ensures perceived loudness remains consistent.
            float clampedPan = Math.max(-1.0f, Math.min(1.0f, pan));
            float angle = (clampedPan + 1.0f) * (float)Math.PI / 4.0f; // 0 to PI/2
            float xPos = (float)Math.sin(angle);
            float zPos = -(float)Math.cos(angle); 
            
            // Cached pan set - skip if unchanged
            if (Math.abs(soundSystem.sourceLastPanX[sourceIndex] - xPos) > ALSoundSystem.EPSILON ||
                Math.abs(soundSystem.sourceLastPanZ[sourceIndex] - zPos) > ALSoundSystem.EPSILON) {
                // We use X and Z for horizontal panning in OpenAL 3D space
                AL10.alSource3f(sourceId, AL10.AL_POSITION, xPos, 0.0f, zPos);
                soundSystem.sourceLastPanX[sourceIndex] = xPos;
                soundSystem.sourceLastPanY[sourceIndex] = 0.0f;
                soundSystem.sourceLastPanZ[sourceIndex] = zPos;
            }

            // Set pitch based on speed
            float pitch = soundSystem.speed;
            
            // Cached pitch set - skip if unchanged
            if (Math.abs(soundSystem.sourceLastPitch[sourceIndex] - pitch) > ALSoundSystem.EPSILON) {
                AL10.alSourcef(sourceId, AL10.AL_PITCH, pitch);
                soundSystem.sourceLastPitch[sourceIndex] = pitch;
            }

            // Start playback
            AL10.alSourcePlay(sourceId);
        }
    }

    @Override
    public void stop() {
        if (!stopped && sourceIndex >= 0) {
            // Verify ticket matches before stopping
            if (soundSystem.sourceTickets[sourceIndex] == ticket) {
                soundSystem.releaseSource(sourceIndex);
            }
            stopped = true;
        }
    }

    public boolean isStopped() {
        return stopped;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public int getTicket() {
        return ticket;
    }
}
