
package org.open2jam.game.judgment;

import org.open2jam.render.entities.NoteEntity;

/**
 * Judge hits by time.
 * @author dttvb
 */
public class TimeJudgment extends AbstractJudgmentStrategy {

    private static final double BAD_THRESHOLD = 173;    // ms
    private static final double GOOD_THRESHOLD = 125;   // ms
    private static final double COOL_THRESHOLD = 41;    // ms

    @Override
    public boolean accept(NoteEntity note) {
        return note.getHitTime() <= BAD_THRESHOLD;
    }

    @Override
    public boolean missed(NoteEntity note) {
        return note.getHitTime() < -BAD_THRESHOLD;
    }

    @Override
    public JudgmentResult judge(NoteEntity note) {
        double hit = Math.abs(note.getHitTime());
        if (hit <= COOL_THRESHOLD) return JudgmentResult.COOL;
        if (hit <= GOOD_THRESHOLD) return JudgmentResult.GOOD;
        if (hit <= BAD_THRESHOLD) return JudgmentResult.BAD;
        return JudgmentResult.MISS;
    }

}
