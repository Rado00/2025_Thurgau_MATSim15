# Feeder DRT Implementation Analysis

## Overview

This document describes the implementation of **feeder_drt** mode in MATSim 15, which allows DRT (Demand Responsive Transport) to be used as access/egress for PT (Public Transport) trips.

---

## 1. Current Situation

### 1.1 Goal
Implement a `feeder_drt` mode that:
- Uses DRT as access to PT stops (first mile)
- Uses DRT as egress from PT stops (last mile)
- Appears as a distinct mode in output_trips.csv with `main_mode = "feeder_drt"`

### 1.2 Implementation Components

| Component | File | Purpose |
|-----------|------|---------|
| Config | `FeederDrtConfigGroup.java` | Configuration parameters (maxAccessEgressDistance, useDrtForAccess, etc.) |
| Routing | `FeederDrtRoutingModule.java` | Creates DRT+PT routes |
| Estimator | `FeederDrtUtilityEstimator.java` | Calculates utility for feeder_drt trips |
| Constraint | `FeederDrtConstraint.java` | Validates routes have both DRT and PT legs |
| Module | `FeederDrtModule.java` | Guice bindings |
| Mode Availability | `AstraModeAvailability_DRT.java` | Offers feeder_drt to eligible agents |

### 1.3 Key Configuration Parameters

```xml
<!-- SwissRailRaptor config -->
<param name="useIntermodalAccessEgress" value="false" />

<!-- FeederDrt config -->
<param name="useDrtForAccess" value="true" />
<param name="maxAccessEgressDistance_m" value="10000.0" />

<!-- DMC cachedModes -->
<param name="cachedModes" value="pt, car, truck, drt, feeder_drt, car_passenger, walk, bike" />
```

---

## 2. Problem Description

### 2.1 Primary Issue
Despite valid feeder_drt routes being created with positive utilities, **0 feeder_drt trips** appear in output_trips.csv.

### 2.2 Observations from Simulations

| Simulation | Branch | DRT Vehicles | DRT Trips | PT+DRT Trips | feeder_drt Trips |
|------------|--------|--------------|-----------|--------------|------------------|
| 6695 | our branch | 100 | 253 | 6 | 0 |
| 6696 | main | 1000 | 5 | 0 | 0 |

### 2.3 Key Findings

1. **63 valid feeder_drt routes** pass the constraint with utilities between 2.0 and 6.35 (including +10 ASC bonus)

2. **6 PT trips contain DRT legs** in our branch (vs 0 in main):
   - `walk-drt-walk-walk-pt-walk-walk` (DRT as access)
   - `walk-walk-pt-walk-walk-drt-walk` (DRT as egress)

3. These trips are labeled as `main_mode = "pt"` instead of `"feeder_drt"`

4. **Main branch has only 5 DRT trips** despite having 1000 vehicles, because the mode availability code was different

---

## 3. Root Cause Analysis

### 3.1 Why feeder_drt trips appear as "pt"

In MATSim, `main_mode` in output is determined by the `routingMode` attribute on legs.

**Before fix:**
- PT legs created by `ptRoutingModule` → `routingMode = "pt"`
- DRT legs created by `drtRoutingModule` → `routingMode = "drt"`
- MATSim chooses "pt" as main_mode (longest distance)

**Result:** feeder_drt routes are recorded as "pt" trips.

### 3.2 Why main branch has few DRT trips

Original `AstraModeAvailability_DRT.java`:
```java
if (modes.contains(TransportMode.walk)) {
    mutableModes.add(TransportMode.drt);
}
mutableModes.add("drt");  // Added unconditionally
```

Our branch:
```java
if (modes.contains(TransportMode.walk)) {
    mutableModes.add(TransportMode.drt);
    if (modes.contains(TransportMode.pt)) {
        mutableModes.add("feeder_drt");
    }
}
// No unconditional add
```

The difference in DRT trips (5 vs 253) is likely due to other factors in mode choice/routing, not just this code.

### 3.3 The `useIntermodalAccessEgress` parameter

| Value | Effect |
|-------|--------|
| `false` (current) | PT routing uses only walk for access/egress. DRT+PT only via our feeder_drt mode. |
| `true` | SwissRailRaptor can use DRT as access/egress automatically. Trips labeled as "pt". |

**Recommendation:** Keep `false` to have explicit control over feeder_drt as a distinct mode.

---

## 4. Solutions Implemented

### 4.1 Fix: Set routingMode on all legs (LATEST)

**File:** `FeederDrtRoutingModule.java`

```java
// Set routingMode to "feeder_drt" on all legs so that main_mode is correctly recorded
for (PlanElement element : route) {
    if (element instanceof Leg) {
        ((Leg) element).setRoutingMode(FEEDER_DRT_MODE);
    }
}
```

**Expected result:** All legs in feeder_drt routes will have `routingMode = "feeder_drt"`, so `main_mode` in output will be "feeder_drt".

### 4.2 Previous fixes applied

1. **Added feeder_drt to cachedModes** - Required for DMC route caching
2. **Added "feeder interaction" activity to scoring config** - Prevents scoring errors
3. **Added FEEDER_ASC_BONUS = 10.0** - Makes feeder_drt competitive (TODO: calibrate later)
4. **Added debug logging** - To constraint and estimator for diagnostics

---

## 5. Possible Alternative Solutions

### 5.1 Enable useIntermodalAccessEgress = true

**Pros:**
- Automatic DRT+PT routing by SwissRailRaptor
- No custom routing module needed

**Cons:**
- Trips labeled as "pt", not "feeder_drt"
- Cannot track feeder trips separately
- Less control over routing logic

### 5.2 Modify how main_mode is determined

Could modify the trip writer or add post-processing to relabel trips based on leg modes.

### 5.3 Use Tarek's implementation directly

Reference: https://github.com/tkchouaki/eqasim-java/tree/develop/ile_de_france/src/main/java/org/eqasim/ile_de_france/feeder

Tarek's implementation uses similar logic but may have additional handling.

---

## 6. Verification Steps

After running a new simulation with the `setRoutingMode` fix:

```bash
# Count feeder_drt trips
awk -F';' 'NR>1 && $9=="feeder_drt"' output_trips.csv | wc -l

# Count PT trips with DRT legs (should be 0 now)
awk -F';' 'NR>1 && $9=="pt" && $11 ~ /drt/' output_trips.csv | wc -l

# Show feeder_drt trip details
awk -F';' 'NR>1 && $9=="feeder_drt" {print $1, $9, $11}' output_trips.csv | head -10
```

---

## 7. Open Questions

1. **Utility calibration:** FEEDER_ASC_BONUS is set to 10.0 for testing. Needs proper calibration.

2. **Walk-only routes:** 890 out of 945 routes are walk-only because:
   - Origin/destination within 500m of PT stops
   - DRT routing fails for other reasons

3. **Limited eligible trips:** Only ~63 valid feeder_drt routes created. May need to:
   - Adjust distance thresholds
   - Expand DRT service area
   - Review why DRT routing fails

---

## 8. File Summary

| File | Status | Description |
|------|--------|-------------|
| `FeederDrtRoutingModule.java` | MODIFIED | Added `setRoutingMode(FEEDER_DRT_MODE)` on all legs |
| `FeederDrtUtilityEstimator.java` | OK | FEEDER_ASC_BONUS = 10.0, debug logging |
| `FeederDrtConstraint.java` | OK | Debug logging for validation |
| `FeederDrtConfigGroup.java` | OK | Configuration parameters |
| `FeederDrtModule.java` | OK | Guice bindings |
| `AstraModeAvailability_DRT.java` | OK | Offers feeder_drt when walk+pt available |
| `RunSimulation_DRT.java` | OK | Adds feeder_drt to cachedModes, scoring config |

---

## 9. Next Steps

1. **Test the setRoutingMode fix** - Run simulation and verify feeder_drt appears in output
2. **If successful:** Remove/calibrate FEEDER_ASC_BONUS
3. **If unsuccessful:** Investigate further or try alternative solutions
4. **Long-term:** Consider removing debug logging after issue is resolved

---

*Last updated: December 2024*
