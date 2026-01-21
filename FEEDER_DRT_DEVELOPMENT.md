# Feeder DRT Development Documentation

This document summarizes the development of DRT as a feeder mode for PT in MATSim 15, including lessons learned, configuration details, and two alternative approaches for future development.

---

## Table of Contents
1. [Project Overview](#project-overview)
2. [Current Custom Implementation](#current-custom-implementation)
3. [What Worked](#what-worked)
4. [What Didn't Work / Known Issues](#what-didnt-work--known-issues)
5. [Configuration Parameters](#configuration-parameters)
6. [Branch 1: SwissRailRaptor Intermodality Approach](#branch-1-swissrailraptor-intermodality-approach)
7. [Branch 2: Continue Custom Feeder DRT Development](#branch-2-continue-custom-feeder-drt-development)
8. [Key Files Reference](#key-files-reference)

---

## Project Overview

**Goal**: Enable DRT (Demand Responsive Transit) as a first/last mile feeder mode for PT (Public Transport) in the Thurgau MATSim model.

**Two Approaches**:
1. **Custom Implementation** (current): Custom routing module that chains DRT + PT + DRT/walk legs
2. **SwissRailRaptor Intermodality** (alternative): Use built-in MATSim intermodal access/egress with DRT

---

## Current Custom Implementation

### Architecture

The custom feeder DRT implementation consists of:

| File | Purpose |
|------|---------|
| `FeederDrtConfigGroup.java` | Configuration parameters |
| `FeederDrtRoutingModule.java` | Routes feeder_drt trips as: DRT → PT → DRT/walk |
| `FeederDrtUtilityEstimator.java` | Calculates utility by summing DRT and PT segment utilities |
| `FeederDrtConstraint.java` | Validates routes contain both DRT and PT legs |
| `FeederDrtModule.java` | Guice module for dependency injection |
| `AstraModeAvailability_DRT.java` | Makes feeder_drt mode available to agents |

### Routing Logic

```
Origin → [DRT access] → PT Stop → [PT leg(s)] → PT Stop → [DRT/walk egress] → Destination
```

The routing module:
1. Finds nearest stop (any type) AND nearest train station
2. Computes routes via both options (if `useTrainStationRouting=true`)
3. Selects the route with lowest travel time
4. Falls back to walk if DRT routing fails

### Utility Calculation

```java
feeder_drt_utility = DRT_segment_utility + PT_segment_utility + FEEDER_ASC_BONUS
```

**Important**: The `FEEDER_ASC_BONUS` is hardcoded in `FeederDrtUtilityEstimator.java:40`:
```java
private static final double FEEDER_ASC_BONUS = 0.0;
```

This is NOT read from config - you must change it in Java code and recompile.

---

## What Worked

### 1. Service Area Validation
Checking if PT stops are within the DRT service area polygon before routing prevents OOM errors from pathological network paths.

```java
if (drtServiceArea != null) {
    boolean fromInside = isInsideDrtServiceArea(fromCoord);
    boolean toInside = isInsideDrtServiceArea(toCoord);
    if (!fromInside || !toInside) {
        return null; // Skip this route
    }
}
```

### 2. Distance Limits
`MAX_DRT_LEG_DISTANCE = 5000m` prevents routing attempts for unreasonably long DRT legs.

### 3. Train Station Routing
Routing via train stations (stopCategory ≤ 3) produces more successful routes because train stations have better PT network connectivity.

### 4. Simulation Results

| Simulation | Iterations | FEEDER_ASC_BONUS | Result |
|------------|------------|------------------|--------|
| sim 6714 | 10 | 0.0 | Success (10 multi-modal trips) |
| sim 6717 | 100 | 0.0 | Success |
| sim 6716 | 100 | 10.0 | OOM at iteration 6 |

**Key insight**: With bonus=10, more agents choose feeder_drt, increasing the chance of hitting problematic routing scenarios.

---

## What Didn't Work / Known Issues

### 1. OOM Errors (java.lang.OutOfMemoryError)

**Symptom**: `Requested array size exceeds VM limit` in `SpeedyALT.constructPath()` during route re-computation.

**Root Cause**: DRT routing attempts to find paths to PT stops that have pathological network connectivity (billions of nodes in path).

**Partial Solutions Implemented**:
- Service area polygon check
- MAX_DRT_LEG_DISTANCE limit
- Train station routing toggle

**Still Unsolved**: Some OOM errors persist with high bonus values. The exact problematic PT stops haven't been identified.

### 2. feeder_drt Not Competitive Without Bonus

**Problem**: When `FEEDER_ASC_BONUS = 0`, no agents choose feeder_drt.

**Reason**: feeder_drt utility stacks BOTH DRT and PT disutilities:
- `drt` utility = `-cost_drt - time_drt`
- `feeder_drt` utility = `-cost_drt - time_drt + -cost_pt - time_pt + bonus`

Without a bonus, feeder_drt is always worse than using drt or pt alone.

**Note**: The disutilities should theoretically be smaller (partial trips), but in practice they don't compensate enough.

### 3. useTrainStationRouting=false May Break Routing

When train station routing is disabled, the module routes to nearest stop of ANY type (including bus stops). Bus stops may have poor PT connectivity, causing:
- PT routing failures
- Walk-only routes (rejected by constraint)
- No valid feeder_drt routes available

---

## Configuration Parameters

### FeederDrtConfigGroup Parameters

```xml
<module name="feederDrt">
    <!-- Mode name for feeder DRT -->
    <param name="modeName" value="feeder_drt"/>

    <!-- Base DRT mode to use -->
    <param name="baseDrtMode" value="drt"/>

    <!-- Base PT mode to use -->
    <param name="basePtMode" value="pt"/>

    <!-- Maximum distance to search for PT stops (meters) -->
    <param name="maxAccessEgressDistance_m" value="5000.0"/>

    <!-- Use DRT for access leg -->
    <param name="useDrtForAccess" value="true"/>

    <!-- Use DRT for egress leg -->
    <param name="useDrtForEgress" value="true"/>

    <!-- Path to DRT service area shape file (optional but recommended) -->
    <param name="drtServiceAreaShapeFile" value="${DRT_SHAPE_FILE_PATH}"/>

    <!-- Try routing via train stations in addition to nearest stop -->
    <param name="useTrainStationRouting" value="true"/>
</module>
```

### Key Constants in Code

| Constant | Value | Location | Purpose |
|----------|-------|----------|---------|
| `MAX_DRT_LEG_DISTANCE` | 5000m | FeederDrtRoutingModule.java:45 | Max Euclidean distance for DRT leg |
| `TRAIN_STATION_CATEGORY_THRESHOLD` | 3 | FeederDrtRoutingModule.java:48 | stopCategory ≤ 3 = train station |
| `MAX_WALK_FALLBACK_DISTANCE` | 2000m | FeederDrtRoutingModule.java:248 | Max walk when DRT fails |
| `FEEDER_ASC_BONUS` | 0.0 | FeederDrtUtilityEstimator.java:40 | **HARDCODED** bonus for testing |

### Verifying Config is Read Correctly

Check logs for this line:
```
FeederDrtRoutingModule initialized with maxAccessEgressDistance=5000 m, X transit stops available, serviceAreaCheck=true, trainStationRouting=false
```

---

## Branch 1: SwissRailRaptor Intermodality Approach

### Overview

Instead of custom routing, use SwissRailRaptor's built-in intermodal access/egress feature with DRT as an access mode.

### Why Try This?

1. **Native MATSim support**: Less custom code to maintain
2. **Proven implementation**: Used in other projects (Vienna, Switzerland)
3. **Better integration**: Works with MATSim's scoring and mode choice
4. **Performance**: SwissRailRaptor is 20-30x faster than default router

### Configuration

```xml
<module name="swissRailRaptor">
    <!-- Enable intermodal access/egress -->
    <param name="useIntermodalAccessEgress" value="true"/>

    <!-- Selection method -->
    <param name="intermodalAccessEgressModeSelection" value="CalcLeastCostModePerStop"/>

    <!-- Walk configuration (REQUIRED when intermodal is enabled) -->
    <parameterset type="intermodalAccessEgress">
        <param name="mode" value="walk"/>
        <param name="radius" value="1000"/>
    </parameterset>

    <!-- DRT configuration -->
    <parameterset type="intermodalAccessEgress">
        <param name="mode" value="drt"/>
        <param name="maxRadius" value="5000"/>
        <!-- Optional: filter to specific stops -->
        <param name="stopFilterAttribute" value="drtAccessible"/>
        <param name="stopFilterValue" value="true"/>
    </parameterset>
</module>
```

### Implementation Steps

1. **Remove or disable custom feeder modules**:
   - Comment out `FeederDrtModule` binding
   - Remove `feeder_drt` from mode availability

2. **Enable SwissRailRaptor intermodal**:
   ```xml
   <param name="useIntermodalAccessEgress" value="true"/>
   ```

3. **Configure DRT as access/egress mode** (see config above)

4. **Ensure MainModeIdentifier compatibility**:
   - SwissRailRaptorModule provides adapted MainModeIdentifier
   - May conflict with DRT's MainModeIdentifier
   - Test carefully with MATSim 15

5. **Mark PT stops as DRT-accessible** (optional):
   - Add `drtAccessible=true` attribute to transit stops within DRT service area
   - Use `stopFilterAttribute` to filter

### Known Issues

- **MainModeIdentifier conflict**: DRT and SwissRailRaptor both use custom MainModeIdentifiers. May need custom implementation combining both.
- **Version compatibility**: Ensure MATSim 15 and matsim-sbb-extensions versions are compatible.

### References

- [matsim-sbb-extensions GitHub](https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions)
- [Intermodal DRT trips issue #363](https://github.com/matsim-org/matsim-code-examples/issues/363)

---

## Branch 2: Continue Custom Feeder DRT Development

### Overview

Continue developing the custom feeder DRT implementation, focusing on fixing the remaining issues.

### Recommended Next Steps

#### 1. Make FEEDER_ASC_BONUS Configurable

Add to `FeederDrtConfigGroup.java`:
```java
private static final String FEEDER_ASC_BONUS = "feederAscBonus";
private double feederAscBonus = 0.0;

@StringGetter(FEEDER_ASC_BONUS)
public double getFeederAscBonus() {
    return feederAscBonus;
}

@StringSetter(FEEDER_ASC_BONUS)
public void setFeederAscBonus(double feederAscBonus) {
    this.feederAscBonus = feederAscBonus;
}
```

Update `FeederDrtUtilityEstimator.java` to read from config instead of hardcoded value.

#### 2. Identify Problematic PT Stops

Add detailed logging to identify which PT stops cause OOM:
```java
log.error("OOM-RISK: Attempting route to stop {} at ({}, {})",
    stop.getId(), stop.getCoord().getX(), stop.getCoord().getY());
```

Create a blacklist of problematic stops.

#### 3. Implement Routing Timeout

Wrap DRT routing in a timeout:
```java
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<List<? extends PlanElement>> future = executor.submit(() ->
    drtRoutingModule.calcRoute(request));
try {
    return future.get(5, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    future.cancel(true);
    log.warn("DRT routing timeout for stop {}", stopId);
    return null;
}
```

**Caveat**: Timeout doesn't cleanly interrupt SpeedyALT - may still cause memory issues.

#### 4. Pre-filter PT Stops

Before routing, validate PT stop accessibility:
- Check if stop has valid link in network
- Check if stop's link is connected to DRT service area network
- Pre-compute reachable stops at startup

#### 5. Test Different Scenarios

| Test | Config | Expected Outcome |
|------|--------|------------------|
| Baseline | bonus=0, trainStation=true | Few/no feeder_drt trips |
| Bonus test | bonus=5, trainStation=true | Some feeder_drt trips |
| No train | bonus=5, trainStation=false | Test if bus stops work |
| High bonus | bonus=10, trainStation=true | OOM risk - identify stops |

---

## Key Files Reference

### Configuration
- `abmt2025/src/main/java/abmt2025/project/mode_choice/feeder/FeederDrtConfigGroup.java`

### Routing
- `abmt2025/src/main/java/abmt2025/project/mode_choice/feeder/FeederDrtRoutingModule.java`

### Utility Estimation
- `abmt2025/src/main/java/abmt2025/project/mode_choice/feeder/FeederDrtUtilityEstimator.java`

### Constraints
- `abmt2025/src/main/java/abmt2025/project/mode_choice/feeder/FeederDrtConstraint.java`

### Mode Availability
- `abmt2025/src/main/java/abmt2025/project/mode_choice/AstraModeAvailability_DRT.java`

### Main Configurator
- `abmt2025/src/main/java/abmt2025/project/config/AstraConfigurator_DRT.java`

### Example Configs
- `abmt2025/copyOfCurrentConfigs/Thurgau_config_DRT_M15_06_MultiMod.xml`
- `abmt2025/copyOfCurrentConfigs/Thurgau_config_DRT_M15_06_MultiMod02.xml`

---

## Methodological Contributions

### 1. Hierarchical Stop Selection with Train Station Prioritization

**Baseline**: Route to nearest PT stop regardless of type

**Our approach**: Dual-path routing that computes routes via:
- Nearest stop (any type: bus, tram, etc.)
- Nearest train station (stopCategory ≤ 3)

Then selects the route with lowest travel time. This recognizes that train stations have superior PT network connectivity, making them more effective transfer points even if slightly farther.

### 2. Service Area-Constrained Routing

**Baseline**: Attempt DRT routing to any PT stop within search radius

**Our approach**: Pre-validates that both origin and PT stop are within the DRT service area polygon before routing. This:
- Ensures operational feasibility (DRT can actually serve the location)
- Prevents pathological network routing that causes computational failures
- Reflects real-world constraints where DRT operates in defined zones

---

## Summary Slide Content

> **Methodological Advancement**: A hierarchical feeder routing algorithm that prioritizes high-connectivity train stations while enforcing DRT service area constraints to ensure operationally feasible multi-modal routes.

---

*Last updated: January 2026*
*Branch: claude/complete-drt-feeder-Neoio*
