# Gameplay Mechanics - Exact Numerical Verification

**Project**: open2jam-modern  
**Reference**: Original game behavior specifications  
**Analysis Date**: March 2026  
**Git Commit**: `89b0742` - "fix: pill conversion should not increment consecutive cools"

---

## Executive Summary

This report performs **exact numerical verification** of open2jam-modern gameplay mechanics against specifications from the original game behavior.

**Important Notes**:
- **TimeJudgment** is intentionally NOT BPM-dependent (it's a configurable alternative mode)
- **BeatJudgment** is the default mode that should match the original game behavior
- **HP values** from the original game are in absolute points (not percentages)

**Overall Result**: **BeatJudgment timing has discrepancies**. HP values are proportionally similar but Good judgment HP is missing.

| Mechanic | Match Status | Discrepancy |
|----------|-------------|-------------|
| Scoring Base Points | ✅ **EXACT** | None |
| Pill System Logic | ✅ **EXACT** | None |
| Combo Reset Logic | ✅ **EXACT** | None |
| BeatJudgment Timing | ❌ **MISMATCH** | Up to 11.5% deviation |
| TimeJudgment Timing | ℹ️ **INTENTIONAL** | Different by design |
| HP Values (Cool) | ⚠️ **CLOSE** | Proportional but Good HP missing |
| HP Values (Good) | ❌ **MISSING** | No HP gain implemented |
| HP Values (Bad/Miss) | ⚠️ **CLOSE** | Proportional values |

---

## 1. BeatJudgment Timing Windows ❌ MISMATCH

### Original Game Specification (Tick-Based)

```
1 tick = 1250/BPM ms
1 beat = 60000/BPM ms
Therefore: 1 tick = 1250/60000 = 1/48 beats ≈ 0.02083 beats
```

| Judgment | Ticks | In Beats (Exact) | In Beats (Decimal) |
|----------|-------|-----------------|-------------------|
| **COOL** | 6 | 6/48 = **1/8** | 0.12500 |
| **GOOD** | 18 | 18/48 = **3/8** | 0.37500 |
| **BAD** | 25 | 25/48 | 0.52083... |

### BeatJudgment Implementation

```java
// BeatJudgment.java:12-14
private static final double BAD_THRESHOULD = 0.8;
private static final double GOOD_THRESHOULD = 0.5;
private static final double COOL_THRESHOULD = 0.2;

// BeatJudgment.java:21
return (noteBeat - hitBeat) / 0.664;  // Normalization factor
```

**Actual Beat Thresholds** (threshold × 0.664):

| Judgment | Raw Threshold | × 0.664 | Actual Beats |
|----------|--------------|---------|--------------|
| **COOL** | 0.2 | 0.2 × 0.664 | **0.1328** |
| **GOOD** | 0.5 | 0.5 × 0.664 | **0.3320** |
| **BAD** | 0.8 | 0.8 × 0.664 | **0.5312** |

### Numerical Comparison

| Judgment | Original Game | BeatJudgment | Absolute Diff | % Deviation | Status |
|----------|--------------|--------------|---------------|-------------|--------|
| **COOL** | 0.12500 | 0.1328 | +0.0078 | **+6.24%** | ❌ More lenient |
| **GOOD** | 0.37500 | 0.3320 | -0.0430 | **-11.47%** | ❌ Stricter |
| **BAD** | 0.52083 | 0.5312 | +0.0104 | **+2.00%** | ⚠️ Close |

### Verdict: ❌ MISMATCH

**Impact**: 
- GOOD judgment is **11.5% stricter** than the original game - players will get fewer GOODs
- COOL judgment is **6.2% more lenient** - slightly easier to hit cools
- BAD judgment is nearly identical (+2%)

**Root Cause**: The normalization factor `0.664` and raw thresholds `(0.2, 0.5, 0.8)` do not produce the correct beat values `(1/8, 3/8, 25/48)`.

**Fix Required**:
```java
// Correct thresholds to match original game exactly:
private static final double COOL_THRESHOULD = 0.125 / 0.664;  // ≈ 0.1883
private static final double GOOD_THRESHOULD = 0.375 / 0.664;  // ≈ 0.5648
private static final double BAD_THRESHOULD  = (25.0/48.0) / 0.664; // ≈ 0.7844
```

---

## 2. TimeJudgment Timing Windows ℹ️ INTENTIONAL DESIGN

### Original Game Specification (BPM-Dependent)

| Judgment | Formula | At 130 BPM | At 150 BPM | At 180 BPM |
|----------|---------|------------|------------|------------|
| **COOL** | 7500/BPM ms | 57.69 ms | 50.00 ms | 41.67 ms |
| **GOOD** | 22500/BPM ms | 173.08 ms | 150.00 ms | 125.00 ms |
| **BAD** | 31250/BPM ms | 240.38 ms | 208.33 ms | 173.61 ms |

### TimeJudgment Implementation

```java
// TimeJudgment.java:11-13
private static final double BAD_THRESHOULD = 173;    // ms (fixed)
private static final double GOOD_THRESHOULD = 125;   // ms (fixed)
private static final double COOL_THRESHOULD = 41;    // ms (fixed)
```

### Design Intent

**TimeJudgment is intentionally BPM-independent**. This is an alternative game mode where timing windows remain constant regardless of song BPM, allowing players to configure fixed timing thresholds.

### Verdict: ℹ️ BY DESIGN

No fix required. This is an intentional alternative to BeatJudgment's BPM-dependent timing.

---

## 3. Scoring Base Points ✅ EXACT MATCH

### Original Game Specification

| Judgment | Base Points |
|----------|-------------|
| **COOL** | 200 |
| **GOOD** | 100 |
| **BAD** | 4 |
| **MISS** | -10 |

### Implementation

```java
// Render.java:1208-1254
case COOL:
    score_value = 200 + (jamcombo_entity.getNumber()*10);  // Base: 200 ✅
    break;

case GOOD:
    score_value = 100;  // Base: 100 ✅
    break;

case BAD:
    if(pills_draw.size() > 0) {
        score_value = 200 + (jamcombo_entity.getNumber() * 10);  // As COOL ✅
    } else {
        score_value = 4;  // Base: 4 ✅
    }
    break;

case MISS:
    if(score_entity.getNumber() >= 10)
        score_value = -10;  // Base: -10 ✅
    else 
        score_value = -score_entity.getNumber();  // Safeguard (minor deviation)
    break;
```

### Verdict: ✅ EXACT MATCH

**Note**: MISS penalty includes a safeguard preventing negative total score (minor deviation from the original game's flat -10).

---

## 4. Pill System ✅ EXACT MATCH

### Original Game Specification

| Aspect | Value |
|--------|-------|
| **Acquisition Threshold** | 15 consecutive cools |
| **Maximum Pills** | 5 |
| **Usage** | Bad → Cool transformation |
| **Streak on Use** | Reset to 0 |
| **Streak Increment** | No (pill save doesn't extend streak) |

### Implementation

```java
// Pill Acquisition (Render.java:1263-1269)
if(consecutive_cools >= 15 && pills_draw.size() < 5)
{
    consecutive_cools -= 15;
    Entity ee = skin.getEntityMap().get("PILL_"+(pills_draw.size()+1)).copy();
    entities_matrix.add(ee);
    pills_draw.add(ee);
}

// Pill Usage (Render.java:1220-1242)
case BAD:
    if(pills_draw.size() > 0) {
        result = JudgmentResult.COOL;  // Bad → Cool ✅
        jambar_entity.addNumber(2);
        // consecutive_cools NOT incremented ✅
        pills_draw.removeLast().setDead(true);
        score_value = 200 + (jamcombo_entity.getNumber() * 10);
    }
    // ...
    consecutive_cools = 0;  // Reset streak ✅
break;
```

### Verdict: ✅ EXACT MATCH

All pill system behaviors match the original game specifications exactly.

---

## 5. Combo Reset Logic ✅ EXACT MATCH

### Original Game Specification

| Judgment | Combo Reset | Jam Reset |
|----------|-------------|-----------|
| **COOL** | No (increment) | No (increment) |
| **GOOD** | No (increment) | No (increment) |
| **BAD** | Yes | Yes |
| **MISS** | Yes | Yes |

### Implementation

```java
// Render.java:1208-1254
case COOL:
    consecutive_cools++;  // ✅ No reset
    jambar_entity.addNumber(2);  // ✅ Jam increment
    break;

case GOOD:
    consecutive_cools = 0;  // ⚠️ Resets cool streak (correct)
    jambar_entity.addNumber(1);  // ✅ Jam increment
    break;

case BAD:
    jambar_entity.setNumber(0);  // ✅ Jam reset
    jamcombo_entity.resetNumber();  // ✅ Jam combo reset
    consecutive_cools = 0;  // ✅ Cool streak reset
    break;

case MISS:
    jambar_entity.setNumber(0);  // ✅ Jam reset
    jamcombo_entity.resetNumber();  // ✅ Jam combo reset
    consecutive_cools = 0;  // ✅ Cool streak reset
    break;
```

### Verdict: ✅ EXACT MATCH

**Note**: GOOD resets `consecutive_cools` but NOT `jambar` or `jamcombo` - this is correct behavior (GOOD maintains jam combo but breaks cool streak).

---

## 6. HP (Lifebar) Values ⚠️ PARTIAL MATCH

### Original Game Specification

**Max Life**: 1000 units (points)

| Judgment | Easy | Normal | Hard |
|----------|------|--------|------|
| **COOL** | +3 | +2 | +1 |
| **GOOD** | +2 | +1 | 0 |
| **BAD** | -10 | -7 | -5 |
| **MISS** | -50 | -40 | -30 |

### open2jam-modern Implementation

**Max Life** (`Render.java:707-718`):
```java
int base = 12000;
int multiplier = (rank >= 2) ? 4 : (rank >= 1) ? 3 : 2;
int maxLife = base * multiplier;
// Easy: 24000, Normal: 36000, Hard: 48000
```

**HP Changes** (`Render.java:1209-1250`):

| Judgment | Easy | Normal | Hard |
|----------|------|--------|------|
| **COOL** | +96 | +96 | +48 |
| **GOOD** | 0 | 0 | 0 |
| **BAD** | -240 | -240 | -240 |
| **MISS** | -1440 | -1440 | -1440 |

### Normalized Comparison (% of max life)

To compare fairly, convert both to percentages of max life:

**Original Game** (as % of max 1000):

| Judgment | Easy | Normal | Hard |
|----------|------|--------|------|
| **COOL** | +3/1000 = **+0.30%** | +2/1000 = **+0.20%** | +1/1000 = **+0.10%** |
| **GOOD** | +2/1000 = **+0.20%** | +1/1000 = **+0.10%** | 0/1000 = **0.00%** |
| **BAD** | -10/1000 = **-1.00%** | -7/1000 = **-0.70%** | -5/1000 = **-0.50%** |
| **MISS** | -50/1000 = **-5.00%** | -40/1000 = **-4.00%** | -30/1000 = **-3.00%** |

**open2jam-modern** (as % of max life):

| Judgment | Easy (24000) | Normal (36000) | Hard (48000) |
|----------|--------------|----------------|--------------|
| **COOL** | +96/24000 = **+0.40%** | +96/36000 = **+0.27%** | +48/48000 = **+0.10%** |
| **GOOD** | 0/24000 = **0.00%** | 0/36000 = **0.00%** | 0/48000 = **0.00%** |
| **BAD** | -240/24000 = **-1.00%** | -240/36000 = **-0.67%** | -240/48000 = **-0.50%** |
| **MISS** | -1440/24000 = **-6.00%** | -1440/36000 = **-4.00%** | -1440/48000 = **-3.00%** |

### Direct Comparison (% values)

| Judgment | Original Game (E/N/H) | open2jam (E/N/H) | Match |
|----------|----------------------|------------------|-------|
| **COOL** | +0.30% / +0.20% / +0.10% | +0.40% / +0.27% / +0.10% | ⚠️ **Close** (Easy/Normal slightly higher) |
| **GOOD** | +0.20% / +0.10% / 0.00% | 0.00% / 0.00% / 0.00% | ❌ **MISSING** (no HP gain) |
| **BAD** | -1.00% / -0.70% / -0.50% | -1.00% / -0.67% / -0.50% | ⚠️ **Close** (Normal slightly less penalty) |
| **MISS** | -5.00% / -4.00% / -3.00% | -6.00% / -4.00% / -3.00% | ⚠️ **Close** (Easy slightly more penalty) |

### Verdict: ⚠️ PARTIAL MATCH

**Issues**:
1. **GOOD judgment gives 0 HP** in all difficulties (original game: +0.20%/+0.10%/0%)
2. **COOL HP is slightly higher** for Easy/Normal difficulties (+0.40% vs +0.30%, +0.27% vs +0.20%)
3. **MISS penalty is slightly higher** for Easy difficulty (-6.00% vs -5.00%)
4. **BAD penalty is slightly lower** for Normal difficulty (-0.67% vs -0.70%)

**Impact**: 
- Game is **slightly easier** for Easy/Normal difficulties (higher COOL gain, lower BAD penalty)
- GOOD judgments are **worthless** for survival (no HP gain) - this is the most significant difference
- Hard difficulty is nearly identical to the original game

**Root Cause**: Implementation appears to use simplified HP values that approximate original game proportions but omit GOOD judgment HP entirely.

**Fix Required** (for exact match):
```java
// Add HP tables matching original game percentages
// Scale: HP = maxLife × percentage / 100
private static final double[][] HP_PERCENT = {
    // COOL    GOOD     BAD     MISS
    { 0.30,  0.20,  -1.00,  -5.00},  // Easy
    { 0.20,  0.10,  -0.70,  -4.00},  // Normal
    { 0.10,  0.00,  -0.50,  -3.00},  // Hard
};

// Usage in handleJudgment:
int maxLife = lifebar_entity.getLimit();
int hpChange = (int)(maxLife * HP_PERCENT[rank][judgmentIndex] / 100.0);
lifebar_entity.addNumber(hpChange);
```

---

## Summary Table

| Mechanic | Original Game | open2jam-modern | Match? | Severity |
|----------|--------------|-----------------|--------|----------|
| **COOL Timing (Beat)** | 0.125 beats | 0.1328 beats | ❌ +6.2% | ⚠️ Medium |
| **GOOD Timing (Beat)** | 0.375 beats | 0.332 beats | ❌ -11.5% | ⚠️ High |
| **BAD Timing (Beat)** | 0.5208 beats | 0.5312 beats | ⚠️ +2.0% | ✅ Low |
| **TimeJudgment** | BPM-dependent | Fixed ms | ℹ️ Intentional | ✅ N/A |
| **COOL Score** | 200 | 200 + jam*10 | ✅ Exact | ✅ |
| **GOOD Score** | 100 | 100 | ✅ Exact | ✅ |
| **BAD Score** | 4 | 4 | ✅ Exact | ✅ |
| **MISS Score** | -10 | -10 (with safeguard) | ✅ ~Exact | ✅ |
| **Pill Threshold** | 15 cools | 15 cools | ✅ Exact | ✅ |
| **Pill Max** | 5 | 5 | ✅ Exact | ✅ |
| **Pill Effect** | Bad→Cool | Bad→Cool | ✅ Exact | ✅ |
| **Pill Streak Reset** | Yes | Yes | ✅ Exact | ✅ |
| **Combo Reset (BAD)** | Yes | Yes | ✅ Exact | ✅ |
| **Combo Reset (MISS)** | Yes | Yes | ✅ Exact | ✅ |
| **COOL HP (Easy)** | +0.30% | +0.40% | ⚠️ +0.10% | ✅ Low |
| **COOL HP (Normal)** | +0.20% | +0.27% | ⚠️ +0.07% | ✅ Low |
| **COOL HP (Hard)** | +0.10% | +0.10% | ✅ Exact | ✅ |
| **GOOD HP (All)** | +0.20%/+0.10%/0% | 0%/0%/0% | ❌ Missing | ⚠️ Medium |
| **BAD HP (Easy)** | -1.00% | -1.00% | ✅ Exact | ✅ |
| **BAD HP (Normal)** | -0.70% | -0.67% | ⚠️ +0.03% | ✅ Low |
| **BAD HP (Hard)** | -0.50% | -0.50% | ✅ Exact | ✅ |
| **MISS HP (Easy)** | -5.00% | -6.00% | ⚠️ -1.00% | ✅ Low |
| **MISS HP (Normal)** | -4.00% | -4.00% | ✅ Exact | ✅ |
| **MISS HP (Hard)** | -3.00% | -3.00% | ✅ Exact | ✅ |

---

## Discrepancies Requiring Fix

### 1. BeatJudgment Timing Windows (Priority: HIGH)

**The only critical discrepancy for BeatJudgment (default mode)**:

```java
// BeatJudgment.java - Current (WRONG):
private static final double BAD_THRESHOULD = 0.8;
private static final double GOOD_THRESHOULD = 0.5;
private static final double COOL_THRESHOULD = 0.2;

// Should be (original game exact):
private static final double COOL_THRESHOULD = 0.125 / 0.664;      // ≈ 0.18825
private static final double GOOD_THRESHOULD = 0.375 / 0.664;      // ≈ 0.56476
private static final double BAD_THRESHOULD  = (25.0 / 48.0) / 0.664; // ≈ 0.78439
```

**Impact**: GOOD judgment is 11.5% stricter than the original game, affecting gameplay balance.

### 2. GOOD Judgment HP Gain (Priority: MEDIUM)

```java
// Render.java:1213-1217 - Current (MISSING HP):
case GOOD:
    jambar_entity.addNumber(1);
    consecutive_cools = 0;
    score_value = 100;
    // Missing: lifebar HP gain
    break;

// Should be:
case GOOD:
    jambar_entity.addNumber(1);
    consecutive_cools = 0;
    // Add HP gain for Easy/Normal (Hard gets 0)
    int goodHp = (rank == 0) ? (int)(lifebar_entity.getLimit() * 0.002) :  // Easy: +0.20%
                 (rank == 1) ? (int)(lifebar_entity.getLimit() * 0.001) :  // Normal: +0.10%
                               0;                                           // Hard: 0%
    lifebar_entity.addNumber(goodHp);
    score_value = 100;
    break;
```

### 3. Minor HP Adjustments (Priority: LOW)

For exact original game match, adjust COOL and MISS HP values slightly:

```java
// Current HP percentages:
private static final double[][] HP_PERCENT = {
    // COOL   GOOD    BAD    MISS
    { 0.40,  0.00,  -1.00,  -6.00},  // Easy (WRONG)
    { 0.27,  0.00,  -0.67,  -4.00},  // Normal (WRONG)
    { 0.10,  0.00,  -0.50,  -3.00},  // Hard (OK)
};

// Should be (original game exact):
private static final double[][] HP_PERCENT = {
    // COOL   GOOD    BAD    MISS
    { 0.30,  0.20,  -1.00,  -5.00},  // Easy
    { 0.20,  0.10,  -0.70,  -4.00},  // Normal
    { 0.10,  0.00,  -0.50,  -3.00},  // Hard
};
```

---

## Conclusion

**Exact Match Rate**: 15 of 23 mechanics (65%)

**Categories**:
- ✅ **Exact Match** (15): Score values, Pill system, Combo reset logic, Hard difficulty HP
- ⚠️ **Minor Deviation** (5): COOL HP (Easy/Normal), BAD HP (Normal), MISS HP (Easy), BAD beat timing
- ❌ **Significant Mismatch** (2): GOOD beat timing (-11.5%), GOOD HP missing
- ℹ️ **Intentional Difference** (1): TimeJudgment (by design)

**Gameplay Impact**:
1. **BeatJudgment timing is different** - GOOD window is 11.5% stricter than the original game
2. **GOOD judgments don't restore HP** - Players can't recover health with consistent GOOD hits
3. **Easy/Normal difficulties are slightly easier** - Higher COOL HP gain, lower BAD penalty

**Recommendation**:
- **Fix BeatJudgment thresholds** for exact original game timing compatibility
- **Add GOOD judgment HP gain** to match original game survival mechanics
- **TimeJudgment does NOT need changes** - fixed timing is intentional design

---

## References

- **Original Game Specifications**: Behavior from v1.8
- **open2jam-modern Source**: `src/org/open2jam/`
- **Health Points Documentation**: `docs/health_points.md`
- **Judgment Implementation**: `src/org/open2jam/game/judgment/`
- **Render Logic**: `src/org/open2jam/render/Render.java`

---

**Report Generated**: March 2026  
**Analysis Method**: Exact numerical comparison against original game behavior  
**Confidence Level**: High (direct source code verification)

**Change Log**:
- Initial analysis: Identified BeatJudgment timing discrepancy and missing GOOD HP
- Post-89b0742: Pill system verified - Bad→Cool transformation with proper streak handling
