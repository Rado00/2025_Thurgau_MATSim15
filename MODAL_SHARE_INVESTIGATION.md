# DRT Modal Share Investigation

## Problem Statement

When comparing MATSim simulations with and without DRT (Demand Responsive Transport), the **car modal share is 1% higher in the DRT scenario** despite DRT having minimal impact (only 1 vehicle in a small municipality serving ~700,000 agents).

### Expected Behavior
- Baseline simulation: Car modal share = X%
- DRT simulation: Car modal share ≈ X% (same as baseline, since DRT impact should be negligible)

### Actual Behavior
- Baseline simulation: Car modal share = X%
- DRT simulation: Car modal share = X + 1%

## Setup Details

### Simulation Configuration
- **Population**: 700,000 agents in simulated area
- **DRT Service**: 1 vehicle in small municipality (minimal coverage)
- **DRT Constraints**: Shapefile-based service area restrictions (both origin AND destination must be within service area)
- **Iterations**: `LAST_ITERATION = 100`
- **Actual convergence**:
  - Baseline stops at iteration ~80
  - DRT stops at iteration ~83-84

### Key Configuration Differences

#### 1. Termination Module (`eqasim:termination`)

**Baseline:**
```xml
<param name="modes" value="walk,bike,pt,car,car_passenger,truck,outside" />
```

**DRT:**
```xml
<param name="modes" value="walk,bike,pt,car,car_passenger,truck,outside,drt" />
```

#### 2. Cached Modes (`DiscreteModeChoice`)

**Baseline:**
```xml
<param name="cachedModes" value="car, bike, truck, pt, walk, car_passenger" />
```

**DRT:**
```xml
<param name="cachedModes" value="car, bike, pt, walk, truck, car_passenger, drt" />
```

**Note**: Already tested removing DRT from cachedModes - no change in results.

#### 3. Mode Availability

**Baseline** (`AstraModeAvailability_Baseline.java`):
```java
public Collection<String> getAvailableModes(Person person, List<DiscreteModeChoiceTrip> trips) {
    Collection<String> modes = delegate.getAvailableModes(person, trips);
    // DRT not added
    return modes;
}
```

**DRT** (`AstraModeAvailability_DRT.java`):
```java
public Collection<String> getAvailableModes(Person person, List<DiscreteModeChoiceTrip> trips) {
    Collection<String> modes = delegate.getAvailableModes(person, trips);
    List<String> mutableModes = new java.util.ArrayList<>(modes);

    if (modes.contains(TransportMode.walk)) {
        mutableModes.add(TransportMode.drt);  // DRT added for all agents who can walk
    }

    return mutableModes;
}
```

**BUT**: DRT is then filtered by shapefile constraint, so agents outside service area shouldn't be able to choose it.

#### 4. Utility Estimators

Both scenarios use different utility estimator classes:
- Baseline: `AstraCarUtilityEstimator_Baseline`, `AstraPtUtilityEstimator_Baseline`, etc.
- DRT: `AstraCarUtilityEstimator_DRT`, `AstraPtUtilityEstimator_DRT`, etc.

However, the code is **identical** and they use the **same parameter values** from `AstraModeParameters.buildFrom6Feb2020()`.

## Investigation Performed

### 1. Cost Parameters ✅ VERIFIED IDENTICAL

**Finding**: Both scenarios use the same car cost parameters.

**Evidence**:
- Baseline uses default `SwissCostParameters` from `SwissModeChoiceModule`
- DRT explicitly binds `SwissCostParameters` to `DrtCostParameters`
- Both have `carCost_CHF_km = 0.26` (verified in [eqasim-java source code](https://github.com/eqasim-org/eqasim-java/blob/ad293ff/switzerland/src/main/java/org/eqasim/switzerland/mode_choice/parameters/SwissCostParameters.java))

**Code added for verification**:
- Logging in `AstraCarUtilityEstimator_Baseline.java` and `AstraCarUtilityEstimator_DRT.java`
- Will print actual cost/km being used at runtime

### 2. Mode Parameters ✅ VERIFIED IDENTICAL

**Finding**: All beta coefficients for car, PT, bike, walk are identical.

**Evidence**:
- Both `AstraModeParameters_Baseline` and `AstraModeParameters_DRT` use same `buildFrom6Feb2020()` method
- Checked: car.alpha_u, car.betaTravelTime_u_min, pt coefficients, etc.
- Only difference: DRT parameters exist in DRT version but don't affect other modes

### 3. Cached Modes ✅ TESTED - NO EFFECT

**Action taken**: Removed DRT from cachedModes in DRT config
**Result**: Modal share difference persisted (still 1% higher car share)

## Current Leading Hypothesis

**Different convergence iterations (80 vs 83-84) due to termination monitoring 7 vs 8 modes.**

### The Theory

The termination module monitors mode shares and stops when they stabilize (change < 0.001). By including DRT in the monitored modes:

1. **Baseline**: Monitors 7 modes → converges at iteration 80
2. **DRT**: Monitors 8 modes (including DRT) → converges at iteration 83-84

The extra 3-4 iterations allow the system to settle into a slightly different equilibrium, causing the 1% car modal share difference.

### Why This Makes Sense

Even though DRT serves only ~0.01% of trips, including it in termination monitoring:
- Adds one more variable to the convergence calculation
- May delay convergence trigger by a few iterations
- Those extra iterations could shift equilibrium slightly

### Counter-Argument

If both scenarios truly converged (met the same convergence criteria), they should reach stable equilibria regardless of iteration count. The fact that there's a difference suggests either:
1. The convergence criterion is too loose (smoothing hides ongoing drift)
2. The equilibria are genuinely different due to some structural difference

## What Has Been Ruled Out

- ❌ Different car cost parameters
- ❌ Different mode choice parameters (betas)
- ❌ Cached modes affecting results

## Recommended Next Steps

### Test 1: Make Termination Criteria Identical

**Action**: In DRT config, change:
```xml
<param name="modes" value="walk,bike,pt,car,car_passenger,truck,outside" />
```
(Remove "drt" from termination monitoring)

**Expected outcome if hypothesis is correct**:
- Both simulations stop at ~iteration 80
- The 1% modal share difference disappears

**Expected outcome if hypothesis is wrong**:
- Both simulations stop at ~iteration 80
- The 1% modal share difference **persists** → indicates structurally different equilibria

### Test 2: Verify Cost Parameters at Runtime

**Action**: Run both simulations with logging code and check output logs for:

```
=== BASELINE CAR COST DEBUG ===
CarVariables.cost: [value]
Distance (km): [value]
Cost per km: [should be ~0.26]
betaCost_u_MU: -0.0888
```

```
=== DRT CAR COST DEBUG ===
CarVariables.cost: [value]
Distance (km): [value]
Cost per km: [should be ~0.26]
betaCost_u_MU: -0.0888
```

**Purpose**: Confirm at runtime that both scenarios actually use 0.26 CHF/km

### Test 3: If Test 1 Shows Persistent Difference

If removing DRT from termination doesn't fix the issue, investigate:

1. **Mode availability effects**: Even though shapefile filters DRT, does having it in the initial choice set affect multinomial logit calculations?

2. **Numerical precision**: Are there floating-point differences accumulating over iterations?

3. **Random seed effects**: Though both use same random seed (1234), does the additional DRT mode evaluation introduce different random number generator calls?

4. **Eqasim framework interactions**: Are there any hidden interactions in how eqasim handles mode availability and filtering?

## Code Changes Made

### Files Modified

1. **`AstraCarUtilityEstimator_Baseline.java`**
   - Added logging to print cost information once at startup
   - Logs: actual cost, distance, cost/km ratio, betaCost parameter

2. **`AstraCarUtilityEstimator_DRT.java`**
   - Added identical logging as Baseline for comparison

3. **`AstraModule_Baseline.java`**
   - No changes (remains using default SwissCostParameters from framework)

### Branch Information

- **GitHub Branch**: `claude/debug-drt-modal-share-qOrJv`
- **Repository**: Rado00/2025_Thurgau_MATSim15
- **Commits**:
  - "Remove redundant cost parameter binding from Baseline" (00330a0)
  - "Add cost parameter logging and explicit binding in Baseline" (d1c8fc1)

## Additional Context

### Why Small DRT Service Shouldn't Affect Overall Modal Share

With only 1 vehicle serving a tiny area:
- DRT ridership ≈ 0.01% of total trips
- 99.99% of agents cannot use DRT (outside service area)
- Even if all DRT trips came from car, the effect should be << 1%

Therefore, a 1% shift in **car** modal share across the entire 700,000-agent population suggests a systemic issue, not a direct DRT substitution effect.

### Convergence Parameters

```xml
<module name="eqasim:termination">
    <param name="historyFile" value="null" />
    <param name="horizon" value="10" />
    <param name="smoothing" value="20" />
    <param name="threshold" value="0.001" />
</module>
```

- **Smoothing = 20**: Averages mode shares over last 20 iterations
- **Horizon = 10**: Monitors last 10 smoothed values
- **Threshold = 0.001**: Stops when smoothed changes < 0.1%

This creates a "windowed" view of convergence that may hide small ongoing drifts.

## Questions for Further Investigation

1. Does removing DRT from termination monitoring make both scenarios stop at the same iteration?
2. Does the modal share difference persist even when stopping at the same iteration?
3. Are the logged cost/km values actually identical at runtime?
4. Could the multinomial logit calculation be affected by DRT being in the choice set (before shapefile filtering)?
5. Should we try running both to a fixed iteration (e.g., 100) without termination to eliminate convergence dynamics?

## Summary

The investigation has ruled out cost parameters and mode choice parameters as causes. The leading hypothesis is that **different termination criteria** (monitoring 7 vs 8 modes) causes different convergence iterations (80 vs 83-84), leading to slightly different equilibria and the observed 1% car modal share difference. Testing this hypothesis by removing DRT from termination monitoring is the recommended next step.
