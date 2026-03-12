package org.open2jam.sound;

import org.lwjgl.openal.AL10;

/**
 * OpenAL sound instance with source pooling.
 * Manages playback of a single sound on an OpenAL source from the pool.
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
            float channelVolume = channel == SoundChannel.BGM ?
                    soundSystem.bgmVolume : soundSystem.keyVolume;
            float finalVolume = volume * channelVolume * soundSystem.masterVolume;
            AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0, Math.min(1, finalVolume)));

            // Set pan (-1.0 = left, 0.0 = center, 1.0 = right)
            AL10.alSource3f(sourceId, AL10.AL_POSITION, pan, 0.0f, 0.0f);

            // Set pitch based on speed
            AL10.alSourcef(sourceId, AL10.AL_PITCH, soundSystem.speed);

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
