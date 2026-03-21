
package org.open2jam.game.judgment;

import org.open2jam.render.entities.NoteEntity;
import org.open2jam.game.TimingData;

/**
 * Judge hits by distance.
 * @author dtinth
 */
public class BeatJudgment extends AbstractJudgmentStrategy {

    /**
     * Timing thresholds matching original game behavior (tick-based).
     * 1 tick = 1250/BPM ms = 1/48 beats
     * COOL: 6 ticks = 6/48 = 1/8 = 0.125 beats
     * GOOD: 18 ticks = 18/48 = 3/8 = 0.375 beats
     * BAD: 25 ticks = 25/48 ≈ 0.52083 beats
     * 
     * These values are divided by the normalization factor (0.664) used in calculateHit().
     */
    private static final double BAD_THRESHOLD = (25.0 / 48.0) / 0.664;      // ≈ 0.78439
    private static final double GOOD_THRESHOLD = (3.0 / 8.0) / 0.664;       // ≈ 0.56476
    private static final double COOL_THRESHOLD = (1.0 / 8.0) / 0.664;       // ≈ 0.18825

    private double calculateHit(NoteEntity note) {
        double noteTime = note.getTimeToJudge();
        double hitTime = note.getTimeToJudge() - note.getHitTime();
        double noteBeat = timing.getBeat(noteTime);
        double hitBeat = timing.getBeat(hitTime);
        return (noteBeat - hitBeat) / 0.664;
    }

    @Override
    public boolean accept(NoteEntity note) {
        return calculateHit(note) <= BAD_THRESHOLD;
    }

    @Override
    public boolean missed(NoteEntity note) {
        return calculateHit(note) < -BAD_THRESHOLD;
    }

    @Override
    public JudgmentResult judge(NoteEntity note) {
        double hit = Math.abs(calculateHit(note));
        if (hit <= COOL_THRESHOLD) return JudgmentResult.COOL;
        if (hit <= GOOD_THRESHOLD) return JudgmentResult.GOOD;
        if (hit <= BAD_THRESHOLD) return JudgmentResult.BAD;
        return JudgmentResult.MISS;
    }

}
