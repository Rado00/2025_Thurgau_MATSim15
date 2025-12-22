# Feeder DRT Module

This module enables DRT (Demand Responsive Transit) to be used as an access/egress mode for PT (Public Transport) trips.

## Trip Structures Supported

The feeder DRT mode supports flexible intermodal trips:
- **DRT → PT → Walk** (DRT for access, walk for egress)
- **Walk → PT → DRT** (walk for access, DRT for egress)
- **DRT → PT → DRT** (DRT for both access and egress)

The router automatically chooses the best combination based on distance to PT stops.

## Configuration

### 1. Add to your config XML file:

```xml
<!-- Feeder DRT Configuration -->
<module name="feederDrt">
    <param name="modeName" value="feeder_drt"/>
    <param name="baseDrtMode" value="drt"/>
    <param name="basePtMode" value="pt"/>
    <param name="maxAccessEgressDistance_m" value="10000.0"/>
    <param name="useDrtForAccess" value="true"/>
    <param name="useDrtForEgress" value="true"/>
</module>

<!-- Add feeder_drt interaction to scoring -->
<module name="scoring">
    <parameterset type="scoringParameters">
        <!-- ... existing activity params ... -->
        <parameterset type="activityParams">
            <param name="activityType" value="feeder_drt interaction"/>
            <param name="scoringThisActivityAtAll" value="false"/>
        </parameterset>
    </parameterset>
</module>

<!-- Add feeder_drt to eqasim estimators (optional - done automatically in code) -->
<module name="eqasim">
    <parameterset type="estimator">
        <param name="estimator" value="FeederDrtUtilityEstimator"/>
        <param name="mode" value="feeder_drt"/>
    </parameterset>
</module>
```

### 2. Mode Availability

The `feeder_drt` mode is automatically available when:
- Walk is available (always)
- PT is available (person can use public transport)
- Person is within DRT service area

### 3. Utility Calculation

The utility of a feeder_drt trip is calculated as:
```
U(feeder_drt) = U(DRT_access) + U(PT) + U(DRT_egress)
```

Where:
- `U(DRT_access)` is the utility of the DRT access leg (if used)
- `U(PT)` is the utility of the PT portion (same as regular PT)
- `U(DRT_egress)` is the utility of the DRT egress leg (if used)

### 4. Constraints

A valid feeder_drt trip must:
- Have at least one DRT leg AND at least one PT leg
- Not originate from or terminate at "outside" activities

## Files

- `FeederDrtConfigGroup.java` - Configuration parameters
- `FeederDrtModule.java` - Guice module for dependency injection
- `FeederDrtUtilityEstimator.java` - Calculates trip utility
- `FeederDrtConstraint.java` - Validates trip structure
- `FeederDrtRoutingModule.java` - Routes feeder_drt trips
