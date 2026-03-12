/*
 * BgaEntity - Background Animation Entity
 * Updated for vlcj 4.x API
 */
package org.open2jam.render.entities;

import com.sun.jna.Memory;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.logging.Level;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.render.Sprite;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.component.CallbackMediaPlayerComponent;

/**
 * Background Animation Entity supporting video playback.
 *
 * @author CdK
 */
public class BgaEntity extends Entity implements TimeEntity {

    public boolean isVideo = false;
    public File videoFile;
    boolean isPlaying = false;
    boolean newBuffer = false;

    // vlcj 4.x components
    CallbackMediaPlayerComponent mediaPlayerComponent;
    MediaPlayer mediaPlayer;
    ByteBuffer videoBuffer = null;

    private static final int WIDTH = 320;
    private static final int HEIGHT = 240;
    private static final int DEPTH = 4;
    private static final int BUFFER_SIZE = WIDTH * HEIGHT * DEPTH;

    private LinkedList<Double> times;
    private LinkedList<Sprite> next_sprites;
    private double scale_w = 0, scale_h = 0;

    public BgaEntity(Sprite s, double x, double y) {
        super(s, x, y);
        scale_w = width;
        scale_h = height;
        next_sprites = new LinkedList<Sprite>();
        times = new LinkedList<Double>();
    }

    public void initVideo() {
        try {
            // Initialize vlcj 4.x media player
            mediaPlayerComponent = new CallbackMediaPlayerComponent();
            mediaPlayer = mediaPlayerComponent.mediaPlayer();

            // Play the video file
            mediaPlayer.media().play(videoFile.toURI().toString());

            isPlaying = true;
        } catch (Exception e) {
            Logger.global.log(Level.WARNING, "Failed to initialize video: {0}", e.getMessage());
            isVideo = false;
        }
    }

    @Override
    public void judgment() {
    }

    @Override
    public double getTime() {
        if (times.isEmpty()) return 0;
        return times.getFirst();
    }

    public void addSprite(Sprite s, double time) {
        next_sprites.add(s);
        times.add(time);
    }

    public void setSprite(Sprite s) {
        if (this.frames != null && !this.frames.isEmpty()) {
            this.frames.set(0, s);
        }
        this.sprite = s;
    }

    public void setTime(double time) {
        if (!times.isEmpty()) {
            times.set(0, time);
        } else {
            times.add(time);
        }
    }

    @Override
    public void move(double delta) {
        if (!times.isEmpty()) {
            if (times.getFirst() <= 0) {
                times.removeFirst();
                if (!next_sprites.isEmpty()) {
                    Sprite old = next_sprites.removeFirst();
                    // Sprites don't have setDead, just remove from list
                }
                if (!next_sprites.isEmpty()) {
                    Sprite s = next_sprites.getFirst();
                    // Sprites don't have setPos, draw at entity position instead
                }
            } else {
                times.set(0, times.getFirst() - delta);
            }
        }
    }

    @Override
    public void draw() {
        if (!next_sprites.isEmpty()) {
            Sprite s = next_sprites.getFirst();
            s.draw(x, y, (float)scale_w, (float)scale_h);
        }
        super.draw();
    }

    @Override
    public boolean isDead() {
        return dead;
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.controls().stop();
            mediaPlayer.release();
        }
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.release();
        }
    }
}
