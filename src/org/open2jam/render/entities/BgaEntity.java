/*
 * BgaEntity - Background Animation Entity
 * O2Jam-only: No video playback support
 */
package org.open2jam.render.entities;

import java.io.File;
import java.util.LinkedList;
import java.util.logging.Level;
import org.open2jam.parsers.utils.Logger;
import org.open2jam.render.Sprite;

/**
 * Background Animation Entity for O2Jam.
 * Video playback removed - OJM/OJN files don't contain video.
 *
 * @author CdK
 */
public class BgaEntity extends Entity implements TimeEntity {

    boolean isPlaying = false;
    boolean newBuffer = false;

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
}
