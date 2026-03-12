
package org.open2jam.game.judgment;

import org.open2jam.render.entities.NoteEntity;
import org.open2jam.render.entities.LongNoteEntity;

/**
 * Judge hits by time (milliseconds).
 * Alternative to BeatJudgment, faithful to CXO2's TimeJudgementStrategy.
 * 
 * Based on 60 FPS frame time (16.667ms per frame):
 * - COOL:  3.2 frames = ±53.33ms
 * - GOOD:  8   frames = ±133.33ms
 * - BAD:   15  frames = ±250.00ms (tap)
 * - BAD:   24/25 frames ≈ ±240-250ms (release)
 * 
 * @author dttvb (original), fox294 (CXO2-faithful revision)
 */
public class TimeJudgment extends AbstractJudgmentStrategy {

    // CXO2-faithful thresholds in milliseconds
    private static final double COOL_TOLERANCE = 53.33;    // 3.2 frames @ 60fps
    private static final double GOOD_TOLERANCE = 133.33;   // 8 frames @ 60fps
    private static final double BAD_TAP_TOLERANCE = 250.0; // 15 frames @ 60fps (tap/hold)
    private static final double BAD_RELEASE_TOLERANCE = 240.0; // ~14.4 frames (release)

    /**
     * Get the hit time in milliseconds (absolute time difference).
     */
    private double getHitTime(NoteEntity note) {
        return Math.abs(note.getHitTime());
    }

    /**
     * Check if note is within BAD judgment window (accept as hit).
     */
    @Override
    public boolean accept(NoteEntity note) {
        return getHitTime(note) <= getBadTolerance(note);
    }

    /**
     * Check if note has been missed (passed judgment window).
     */
    @Override
    public boolean missed(NoteEntity note) {
        // Missed if beyond tolerance AND note has passed the judgment line
        return getHitTime(note) > getBadTolerance(note) && 
               note.getHitTime() < 0;
    }

    /**
     * Judge the note and return the accuracy result.
     */
    @Override
    public JudgmentResult judge(NoteEntity note) {
        double hit = getHitTime(note);
        double tolerance = getBadTolerance(note);
        
        if (hit <= COOL_TOLERANCE) return JudgmentResult.COOL;
        if (hit <= GOOD_TOLERANCE) return JudgmentResult.GOOD;
        if (hit <= tolerance) return JudgmentResult.BAD;
        return JudgmentResult.MISS;
    }

    /**
     * Get the BAD tolerance for this note (release notes have stricter tolerance).
     */
    private double getBadTolerance(NoteEntity note) {
        // Release notes (from long notes) use stricter tolerance
        if (note instanceof LongNoteEntity) {
            LongNoteEntity lne = (LongNoteEntity) note;
            if (lne.getState() == NoteEntity.State.LN_HOLD || 
                lne.getState() == NoteEntity.State.JUDGE) {
                return BAD_RELEASE_TOLERANCE;
            }
        }
        return BAD_TAP_TOLERANCE;
    }
}
