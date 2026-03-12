
package org.open2jam.game.judgment;

import org.open2jam.render.entities.NoteEntity;
import org.open2jam.render.entities.LongNoteEntity;
import org.open2jam.game.TimingData;

/**
 * Judge hits by position (measure-based).
 * Faithful to original O2Jam via CXO2's RenderPositionJudgementStrategy.
 * 
 * Uses 192 TPB (ticks per beat) system:
 * - COOL:  ±6/192  measures = ±0.03125 measures ≈ ±50ms @ 150 BPM
 * - GOOD:  ±18/192 measures = ±0.09375 measures ≈ ±150ms @ 150 BPM
 * - BAD:   ±25/192 measures = ±0.13021 measures ≈ ±208ms @ 150 BPM (tap)
 * - BAD:   ±24/192 measures = ±0.125   measures ≈ ±200ms @ 150 BPM (release)
 * 
 * @author dtinth (original), fox294 (CXO2-faithful revision)
 */
public class BeatJudgment extends AbstractJudgmentStrategy {

    // CXO2-faithful thresholds in measures (192 TPB system)
    private static final double COOL_TOLERANCE = 6.0 / 192.0;      // ±0.03125 measures
    private static final double GOOD_TOLERANCE = 18.0 / 192.0;     // ±0.09375 measures
    private static final double BAD_TAP_TOLERANCE = 25.0 / 192.0;  // ±0.13021 measures (tap/hold)
    private static final double BAD_RELEASE_TOLERANCE = 24.0 / 192.0; // ±0.125 measures (release)

    /**
     * Calculate hit accuracy as absolute position difference in measures.
     * @param note The note being judged
     * @return Absolute difference between note position and hit position (in measures)
     */
    private double calculateHit(NoteEntity note) {
        double noteTime = note.getTimeToJudge();
        double hitTime = note.getTimeToJudge() - note.getHitTime();
        double noteMeasure = timing.getMeasure(noteTime);
        double hitMeasure = timing.getMeasure(hitTime);
        return Math.abs(noteMeasure - hitMeasure);
    }

    /**
     * Check if note is within BAD judgment window (accept as hit).
     */
    @Override
    public boolean accept(NoteEntity note) {
        return calculateHit(note) <= getBadTolerance(note);
    }

    /**
     * Check if note has been missed (passed judgment window).
     */
    @Override
    public boolean missed(NoteEntity note) {
        // Missed if beyond tolerance AND note has passed the judgment line
        return calculateHit(note) > getBadTolerance(note) && 
               (note.getTimeToJudge() - note.getHitTime()) < 0;
    }

    /**
     * Judge the note and return the accuracy result.
     */
    @Override
    public JudgmentResult judge(NoteEntity note) {
        double hit = calculateHit(note);
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
            // Check if this is a release event (end of long note)
            if (lne.getState() == NoteEntity.State.LN_HOLD || 
                lne.getState() == NoteEntity.State.JUDGE) {
                return BAD_RELEASE_TOLERANCE;
            }
        }
        return BAD_TAP_TOLERANCE;
    }
}
