# DRT as PT Feeder via SwissRailRaptor Intermodal Access/Egress

This document describes how to enable DRT as a feeder mode for PT using SwissRailRaptor's built-in intermodal access/egress feature, as an alternative to the custom `feeder_drt` implementation.

---

## Overview

### Goal
Enable DRT as first/last mile access/egress mode for PT trips using SwissRailRaptor's native intermodal routing, avoiding custom routing module complexity and OOM issues.

### Key Difference from Custom Feeder DRT
| Aspect | Custom Feeder DRT | SwissRailRaptor Intermodal |
|--------|-------------------|---------------------------|
| Main mode | `feeder_drt` (separate mode) | `pt` (DRT is access/egress within PT) |
| Routing module | Custom `FeederDrtRoutingModule` | SwissRailRaptor handles everything |
| Utility estimation | Custom `FeederDrtUtilityEstimator` | PT estimator handles whole trip |
| Mode choice | Agent chooses `feeder_drt` explicitly | Agent chooses `pt`, router decides access mode |

---

## Analysis

### Current Setup
- **MATSim version**: 2025.0-PR3223 (MATSim 15)
- **SwissRailRaptor**: Already imported via `ch.sbb.matsim.config.SwissRailRaptorConfigGroup`
- **DRT**: Working standalone via `MultiModeDrtModule` with door-to-door service
- **PT router**: SwissRailRaptor (default in eqasim)

### MainModeIdentifier Conflict Status
The MainModeIdentifier conflict between SwissRailRaptor and DRT was **fixed in MATSim 13.1** (commit c7f1761). Since you're using MATSim 15, this issue should be resolved. The fix ensures that:
- SwissRailRaptor only binds its MainModeIdentifier when actually needed
- The `routingMode` attribute reduces dependency on MainModeIdentifier
- Custom MainModeIdentifiers can coexist with SwissRailRaptor

**References**:
- [Issue #1562: Incompatibility of SwissRailRaptor with custom MainModeIdentifier](https://github.com/matsim-org/matsim-libs/issues/1562)
- [PR #1564: Backport fix to 13.x](https://github.com/matsim-org/matsim-libs/pull/1564)

---

## Configuration Changes

### 1. SwissRailRaptor Config (add to your config.xml)

```xml
<module name="swissRailRaptor">
    <!-- Enable intermodal access/egress -->
    <param name="useIntermodalAccessEgress" value="true"/>

    <!-- Mode selection strategy -->
    <param name="intermodalAccessEgressModeSelection" value="CalcLeastCostModePerStop"/>

    <!-- REQUIRED: Walk as base access/egress mode -->
    <parameterset type="intermodalAccessEgress">
        <param name="mode" value="walk"/>
        <param name="maxRadius" value="1000"/>
        <!-- radius in meters for walk access/egress -->
    </parameterset>

    <!-- DRT as intermodal access/egress -->
    <parameterset type="intermodalAccessEgress">
        <param name="mode" value="drt"/>
        <param name="maxRadius" value="5000"/>
        <!-- Optional: Filter to specific stops (train stations) -->
        <!-- <param name="stopFilterAttribute" value="stopCategory"/> -->
        <!-- <param name="stopFilterValue" value="1,2,3"/> -->
    </parameterset>
</module>
```

### 2. Mode Selection Strategies

Available values for `intermodalAccessEgressModeSelection`:
- `CalcLeastCostModePerStop` (Recommended): Calculates least-cost mode for each stop
- `RandomSelectOneModePerRoutingRequestAndDirection`: Random selection per routing request

### 3. Optional: Stop Filtering

To restrict DRT access to specific stops (e.g., train stations only):

```xml
<parameterset type="intermodalAccessEgress">
    <param name="mode" value="drt"/>
    <param name="maxRadius" value="5000"/>
    <param name="stopFilterAttribute" value="stopCategory"/>
    <param name="stopFilterValue" value="1"/>  <!-- Only train stations -->
</parameterset>
```

This requires transit stops to have the `stopCategory` attribute set.

---

## Java Code Changes

### Required Module Setup (IMPORTANT FIX)

**Issue Found**: In `RunSimulation_DRT.java`, the `DvrpModule` and `MultiModeDrtModule` were imported but **not added** as overriding modules. This has been fixed in this branch.

Add these modules in `RunSimulation_DRT.java` after other modules:

```java
// Add DRT modules for DRT routing and DVRP infrastructure
// These are required for both standalone DRT and intermodal access/egress
controller.addOverridingModule(new DvrpModule());
controller.addOverridingModule(new MultiModeDrtModule());
```

These modules are required because:
1. `DvrpModule` - provides DVRP infrastructure (vehicle routing, dispatching)
2. `MultiModeDrtModule` - provides DRT routing module that SwissRailRaptor will use for intermodal legs

### Option A: Config Changes Only (Basic Test)

After the module fix above, you can test intermodal access/egress with just config changes. The setup works because:

1. `MultiModeDrtModule` registers DRT routing
2. SwissRailRaptor will call the DRT router for access/egress legs
3. DRT legs will be simulated by the existing DRT QSim components

**Limitation**: The PT utility estimator won't know about DRT-specific costs (waiting time, DRT fare, etc.)

### Option B: Enhanced PT Utility Estimator (Recommended)

To properly estimate utility for PT trips with DRT access/egress, modify `AstraPtUtilityEstimator_DRT.java`:

```java
// In AstraPtUtilityEstimator_DRT.java

@Override
public double estimateUtility(Person person, DiscreteModeChoiceTrip trip,
                              List<? extends PlanElement> elements) {
    // Existing PT utility calculation
    double utility = calculateBasePtUtility(person, trip, elements);

    // Check for DRT access/egress legs
    for (PlanElement pe : elements) {
        if (pe instanceof Leg) {
            Leg leg = (Leg) pe;
            if (leg.getMode().equals("drt")) {
                // Add DRT-specific utility components
                utility += estimateDrtAccessEgressUtility(person, leg);
            }
        }
    }

    return utility;
}

private double estimateDrtAccessEgressUtility(Person person, Leg drtLeg) {
    // DRT waiting time disutility
    DrtRoute route = (DrtRoute) drtLeg.getRoute();
    double waitingTime_min = route.getMaxWaitTime() / 60.0;
    double inVehicleTime_min = route.getMaxTravelTime() / 60.0;

    double utility = 0.0;
    utility += parameters.astraDRT.betaWaitingTime * waitingTime_min;
    utility += parameters.astraDRT.betaInVehicleTime * inVehicleTime_min;
    // Add DRT cost if needed

    return utility;
}
```

### Option C: Custom AnalysisMainModeIdentifier (For Analysis)

If you need correct mode attribution in output analysis:

```java
public class DrtPtIntermodalMainModeIdentifier implements AnalysisMainModeIdentifier {
    @Override
    public String identifyMainMode(List<? extends PlanElement> tripElements) {
        boolean hasPt = false;
        boolean hasDrt = false;

        for (PlanElement pe : tripElements) {
            if (pe instanceof Leg) {
                String mode = ((Leg) pe).getMode();
                if (mode.equals("pt")) hasPt = true;
                if (mode.equals("drt")) hasDrt = true;
            }
        }

        if (hasPt && hasDrt) {
            return "pt_with_drt";  // Or just "pt" if you prefer
        } else if (hasPt) {
            return "pt";
        } else if (hasDrt) {
            return "drt";
        }
        return "unknown";
    }
}
```

---

## Testing Plan

### Step 1: Config-Only Test (Minimal)
1. Add SwissRailRaptor intermodal config to your config.xml
2. Run simulation with 1-2 iterations
3. Check output plans for trips with DRT access legs to PT

### Step 2: Verify DRT+PT Trips
Look for trip patterns like:
```
walk -> drt -> pt_interaction -> pt -> pt_interaction -> walk
```

### Step 3: Check Mode Stats
- PT mode share should include trips with DRT access
- Standalone DRT mode share should remain separate (door-to-door trips)

### Expected Behavior

| Trip Type | Main Mode | Access | Core | Egress |
|-----------|-----------|--------|------|--------|
| PT with walk | pt | walk | pt | walk |
| PT with DRT access | pt | drt | pt | walk |
| PT with DRT egress | pt | walk | pt | drt |
| PT with DRT both | pt | drt | pt | drt |
| Standalone DRT | drt | - | drt | - |

---

## Potential Issues and Solutions

### 1. DRT Not Being Selected for Access

**Symptom**: All PT trips use walk for access/egress

**Solutions**:
- Verify `maxRadius` for DRT is larger than for walk
- Check if DRT service area covers transit stops
- Reduce walk radius to make DRT more competitive

### 2. Performance Issues

**Symptom**: Slow routing, high memory usage

**Solutions**:
- Limit `maxRadius` for DRT (start with 3000m)
- Use `stopFilterAttribute` to restrict DRT to fewer stops
- Consider using `RandomSelectOneModePerRoutingRequestAndDirection`

### 3. Utility Calculation Mismatch

**Symptom**: DRT access trips don't account for DRT costs properly

**Solution**: Implement Option B (Enhanced PT Utility Estimator)

---

## Comparison: Custom Feeder vs SwissRailRaptor Intermodal

| Criteria | Custom Feeder | SwissRailRaptor |
|----------|---------------|-----------------|
| Code complexity | High | Low |
| OOM risk | Higher (custom routing) | Lower (native routing) |
| Utility control | Full control | PT estimator must handle |
| Mode visibility | Separate `feeder_drt` mode | Within `pt` mode |
| Stop selection | Custom logic | SwissRailRaptor |
| Maintainability | Custom maintenance | MATSim maintained |

---

## References

- [SwissRailRaptor Documentation](https://github.com/SchweizerischeBundesbahnen/matsim-sbb-extensions)
- [Intermodal DRT Trips Issue #363](https://github.com/matsim-org/matsim-code-examples/issues/363)
- [MainModeIdentifier Conflict Issue #1562](https://github.com/matsim-org/matsim-libs/issues/1562)

---

*Last updated: January 2026*
*Branch: claude/drt-pt-feeder-mode-Q02AQ*
