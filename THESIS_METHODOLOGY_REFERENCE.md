# PhD Thesis Methodology Reference Document
## DRT Simulation in MATSim - Parameter Extraction

**Purpose**: This document contains all extracted parameters, equations, and code snippets from a MATSim DRT simulation codebase. Use this as reference material when writing LaTeX methodology sections in Overleaf.

---

# SECTION 3.5.1: SIMULATION FRAMEWORK

## Software Stack

| Component | Version/Details |
|-----------|-----------------|
| MATSim | 15 (artifact: `2025.0-PR3223`) |
| Java | 17 |
| Build system | Maven |
| Mode choice framework | eqasim-java (Switzerland module, commit `ad293ff`) |
| DRT extension | `org.matsim.contrib:drt:2025.0-PR3223` |
| Transit router | SwissRailRaptor |
| Coordinate system | Swiss LV95 (EPSG:2056) |

## Iteration Parameters

| Parameter | Value |
|-----------|-------|
| Number of iterations | 100 |
| First iteration | 0 |
| QSim time step | 1 second |
| QSim end time | 30:00:00 |
| Random seed | 42 |

## Convergence Criteria

The simulation uses eqasim's termination module:
- Rolling horizon: 10 iterations
- Smoothing window: 20 iterations
- Threshold: 0.001
- Monitored modes: walk, bike, pt, car, car_passenger, truck, outside, drt

## Scoring Parameters (MATSim native)

| Parameter | Value | Unit |
|-----------|-------|------|
| brainExpBeta (logit scale) | 1.0 | - |
| learningRate | 1.0 | - |
| performing | 6.0 | utils/hr |
| lateArrival | -18.0 | utils/hr |
| waiting | 0.0 | utils/hr |
| waitingPt | -1.0 | utils/hr |
| utilityOfLineSwitch | -1.0 | utils |
| marginalUtilityOfMoney | 0.0 | - |
| marginalUtilityOfTraveling (all modes) | -1.0 | utils/hr |

Note: MATSim native scoring parameters serve as placeholders; actual utility computation is handled by eqasim discrete mode choice estimators.

## Replanning Strategies

| Strategy | Subpopulation | Weight |
|----------|---------------|--------|
| DiscreteModeChoice | default | 0.05 |
| KeepLastSelected | default | 0.95 |
| KeepLastSelected | freight | 0.95 |
| ReRoute | freight | 0.05 |

- maxAgentPlanMemorySize: 1 (single-plan mode)
- enforceSinglePlan: true
- fractionOfIterationsToDisableInnovation: Infinity (never disabled)

## Population and Network

| Parameter | Value |
|-----------|-------|
| Sample size | 1.0 (100% population) |
| flowCapacityFactor | 1.0 |
| storageCapacityFactor | 1.0 |
| Crossing penalty | 3.0 seconds |
| Routing algorithm | SpeedyALT |

---

# SECTION 3.5.2: DRT OPERATIONAL PARAMETERS

## Service Configuration

| Parameter | Value |
|-----------|-------|
| Operational scheme | serviceAreaBased |
| Service area | Shapefile (zone-specific) |
| Max wait time | 600 s (10 min) |
| Max travel time alpha | 2.0 |
| Max travel time beta | 240 s (4 min) |
| Stop duration | 60 s |
| Reject if constraints violated | false (soft constraints) |
| Vehicle capacity | 4 passengers |
| Operational hours | 0-86400 s (24h) |

## Maximum Travel Time Formula

```latex
T_{\max} = \alpha \cdot T_{\text{unshared}} + \beta
```

With configured values:
```latex
T_{\max} = 2.0 \cdot T_{\text{unshared}} + 240\text{s}
```

## Fleet Specification (DVRP XML format)

```xml
<vehicle id="drt0" start_link="420806" t_0="0.0" t_1="86400.0" capacity="4"/>
```

Fleet sizes range from 1 to 1000 vehicles across different service area zones.

## Insertion Algorithm Parameters

| Parameter | Value |
|-----------|-------|
| Search method | SelectiveInsertionSearch |
| Zone system | GridFromNetwork |
| Cell size | 500 m |
| Restrictive beeline speed factor | 0.5 |

## Passenger Fare Model

```latex
C_{\text{DRT}} = C_{\text{base}} + C_{\text{km}} \cdot d_{\text{in-vehicle}}
```

| Parameter | Symbol | Value |
|-----------|--------|-------|
| Base fare | C_base | 10 CHF |
| Distance fare | C_km | 0 CHF/km |

Java implementation:
```java
public double calculateCost_MU(Person person, DiscreteModeChoiceTrip trip,
        List<? extends PlanElement> elements) {
    return parameters.DRTFare_CHF
         + parameters.DRTFare_CHF_km * getInVehicleDistance_km(elements);
}
```

## Operator Cost Model (Bösch et al. 2018)

```latex
C_{\text{operator}} = c_{\text{vkm}} \cdot VKT + c_{\text{driver}} \cdot VHT + c_{\text{fixed}} \cdot N_{\text{vehicles}}
```

| Parameter | Symbol | Value | Source |
|-----------|--------|-------|--------|
| Vehicle-km cost | c_vkm | 0.20 CHF/km | Bösch et al. 2018 |
| Driver hourly cost | c_driver | 35.0 CHF/h | Swiss wages |
| Daily vehicle cost | c_fixed | 30.0 CHF/vehicle/day | Insurance, parking |

---

# SECTION 3.5.3: DISCRETE MODE CHOICE MODEL

## DMC Configuration

| Parameter | Value |
|-----------|-------|
| Model type | Tour |
| Selector | MultinomialLogit |
| Tour finder | ActivityBased (home, outside) |
| Tour estimator | Cumulative |
| Trip estimator | EqasimUtilityEstimator |
| Mode availability | AstraModeAvailability_DRT |
| Vehicle continuity | car, bike (restricted modes) |
| Cached modes | car, bike, pt, walk, truck, car_passenger, drt |
| Max tour length | 10 trips |

## Tour Constraints
- EqasimVehicleTourConstraint
- FromTripBased

## Trip Constraints
- TransitWalk
- PassengerConstraint
- OutsideConstraint
- ShapeFile (requirement = BOTH for DRT)

## Common Parameters (eqasim Swiss model)

| Parameter | Symbol | Value |
|-----------|--------|-------|
| Cost sensitivity | beta_cost | -0.0888 |
| Cost-income elasticity | lambda_cost_income | -0.8169 |
| Cost-distance elasticity | lambda_cost_distance | -0.2209 |
| Travel time-distance elasticity | lambda_tt_distance | 0.1147 |
| Reference Euclidean distance | d_ref | 39.0 km |
| Reference household income | I_ref | 12,260 CHF/month |

## Elasticity Interaction Function

```latex
f(x, x_{\text{ref}}, \lambda) = \left(\frac{x}{x_{\text{ref}}}\right)^{\lambda}
```

---

## CAR UTILITY FUNCTION

```latex
V_{\text{car}} = \alpha_{\text{car}} + \beta_{\text{tt,car}} \cdot t_{\text{car}} \cdot f(d, d_{\text{ref}}, \lambda_{\text{tt,d}}) + \beta_{\text{acc}} \cdot t_{\text{access}} + \beta_{\text{cost}} \cdot c_{\text{car}} \cdot f(I, I_{\text{ref}}, \lambda_{\text{cost,I}}) + \beta_{\text{work,car}} \cdot \mathbb{1}_{\text{work}} + \beta_{\text{city,car}} \cdot \mathbb{1}_{\text{city}}
```

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| ASC (calibrated) | alpha_car | 0.5 | - |
| In-vehicle travel time | beta_tt_car | -0.0192 | utils/min |
| Work trip indicator | beta_work_car | -1.1606 | - |
| City indicator (calibrated) | beta_city_car | -0.459 | - |
| Car cost per km | c_car | 0.26 | CHF/km |

---

## PUBLIC TRANSPORT UTILITY FUNCTION

```latex
V_{\text{pt}} = \alpha_{\text{pt}} + \beta_{\text{rail}} \cdot t_{\text{rail}} \cdot f(d) + \beta_{\text{bus}} \cdot t_{\text{bus}} + \beta_{\text{wait}} \cdot t_{\text{wait}} + \beta_{\text{acc}} \cdot t_{\text{access}} + \beta_{\text{headway}} \cdot h + \beta_{\text{cost}} \cdot c_{\text{pt}} \cdot f(I) + \beta_{\text{OVGK}} + V_{\text{DRT,access}}
```

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| ASC (calibrated) | alpha_pt | 0 | - |
| Rail in-vehicle time | beta_rail | -0.0072 | utils/min |
| Bus in-vehicle time | beta_bus | -0.0124 | utils/min |
| Feeder travel time | beta_feeder | -0.0452 | utils/min |
| Waiting time | beta_wait_pt | -0.0124 | utils/min |
| Access/egress time | beta_acc_pt | -0.0142 | utils/min |
| Headway | beta_headway | -0.0301 | utils/min |
| OVGK B | beta_OVGK_B | -1.7436 | - |
| OVGK C | beta_OVGK_C | -1.6413 | - |
| OVGK D | beta_OVGK_D | -0.9649 | - |
| OVGK None | beta_OVGK_None | -1.0889 | - |
| PT cost per km | c_pt | 0.60 | CHF/km |
| PT minimum cost | c_pt_min | 2.70 | CHF |

### Feeder vs Non-Feeder Logic

```java
if (railTravelTime_min > 0.0 && busTravelTime_min > 0.0) {
    // Feeder case: rail + bus combination
    utility += betaFeederTravelTime_u_min * busTravelTime_min;
} else {
    // Non-feeder case: bus only
    utility += betaBusTravelTime_u_min * f(d) * busTravelTime_min;
}
```

### DRT Access/Egress Component (for intermodal trips)

```latex
V_{\text{DRT,access}} = \beta_{\text{tt,DRT}} \cdot t_{\text{DRT,access}} + \beta_{\text{wait,DRT}} \cdot t_{\text{DRT,wait}}
```

---

## BICYCLE UTILITY FUNCTION

```latex
V_{\text{bike}} = \alpha_{\text{bike}} + \beta_{\text{tt,bike}} \cdot t_{\text{bike}} \cdot f(d, d_{\text{ref}}, \lambda_{\text{tt,d}}) + \beta_{\text{age60,bike}} \cdot \mathbb{1}_{\text{age} \geq 60}
```

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| ASC (calibrated) | alpha_bike | 0.65 | - |
| Travel time | beta_tt_bike | -0.1258 | utils/min |
| Age >= 60 indicator | beta_age60_bike | -2.6588 | - |

---

## WALK UTILITY FUNCTION

```latex
V_{\text{walk}} = \alpha_{\text{walk}} + \beta_{\text{tt,walk}} \cdot t_{\text{walk}} \cdot f(d, d_{\text{ref}}, \lambda_{\text{tt,d}}) - e^{\gamma \cdot t_{\text{walk}}} + 1
```

Where:
```latex
\gamma = \frac{\ln(100)}{T_{\text{threshold}}}
```

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| ASC (calibrated) | alpha_walk | 0.9 | - |
| Travel time | beta_tt_walk | -0.0457 | utils/min |
| Penalty threshold | T_threshold | 120 | min |

Java implementation of exponential penalty:
```java
protected double estimatePenalty(AstraWalkVariables variables) {
    double beta = Math.log(100) / parameters.astraWalk.travelTimeThreshold_min;
    return -Math.exp(beta * variables.travelTime_min) + 1.0;
}
```

---

## DRT UTILITY FUNCTION (Door-to-Door)

```latex
V_{\text{DRT}} = \alpha_{\text{DRT}} + \beta_{\text{tt,DRT}} \cdot t_{\text{invehicle}} + \beta_{\text{wait,DRT}} \cdot t_{\text{wait}} + \beta_{\text{work,DRT}} \cdot \mathbb{1}_{\text{work}} + \beta_{\text{cost}} \cdot C_{\text{DRT}}
```

| Parameter | Symbol | Value | Unit |
|-----------|--------|-------|------|
| ASC | alpha_DRT | -0.061 | - |
| In-vehicle time | beta_tt_DRT | -0.015 | utils/min |
| Waiting time | beta_wait_DRT | -0.093 | utils/min |
| Access/egress time | beta_acc_DRT | -0.014 | utils/min (disabled) |
| Work trip indicator | beta_work_DRT | -1.938 | - |
| Age >= 60 indicator | beta_age60_DRT | 0.0 | (disabled) |

Note: Access/egress time and age components are defined but commented out in the code:
```java
utility += estimateConstantUtility();
utility += estimateTravelTimeUtility(variables);
// utility += estimateAccessEgressTimeUtility(variables);  // DISABLED
utility += estimateWaitingTimeUtility(variables);
utility += estimateWorkUtility(tripVariables);
// utility += estimateAgeUtility(personVariables);  // DISABLED
utility += estimateCostUtility(variables);
```

---

# SECTION 3.5.4: INTERMODAL PT+DRT ROUTING

## Architecture Components

1. **DrtIntermodalFilterModule** - Guice module for dependency injection
2. **DrtServiceAreaFilter** - Loads shapefile, performs point-in-polygon checks
3. **FilteredDrtIntermodalRoutingModule** - Wraps DRT router with spatial filtering

## SwissRailRaptor Configuration

| Parameter | Value |
|-----------|-------|
| Intermodal access/egress | enabled |
| Mode selection | CalcLeastCostModePerStop |
| Transfer walk margin | 5.0 seconds |

## Walk Access/Egress Parameters

| Parameter | Value |
|-----------|-------|
| Mode | walk |
| Initial search radius | 500 m |
| Max radius | 1000 m |
| Search extension | 200 m |

## DRT Access/Egress Parameters

| Parameter | Value |
|-----------|-------|
| Mode | drt_access |
| Initial search radius | 2000 m |
| Max radius | 5000 m |
| Search extension | 1000 m |

## Algorithm Flow (Pseudocode)

```
1. SwissRailRaptor receives PT routing request
2. For each nearby PT stop (within search radius):
   a. Compute walk access/egress cost
   b. Compute drt_access cost:
      i.   Check origin against DRT service area shapefile
      ii.  Check destination against DRT service area shapefile
      iii. If NEITHER inside -> return null (skip DRT)
      iv.  If at least ONE inside -> delegate to DRT router
   c. Select least-cost access/egress mode per stop
3. Select best overall PT route (may include DRT legs)
```

## Service Area Filtering Logic

```java
boolean fromInside = serviceAreaFilter.isInsideServiceArea(fromCoord);
boolean toInside = serviceAreaFilter.isInsideServiceArea(toCoord);

if (!fromInside && !toInside) {
    // Neither endpoint in service area - skip DRT routing
    return null;
}
// At least one endpoint inside - proceed with DRT routing
return delegate.calcRoute(request);
```

## Point-in-Polygon Check

```java
public boolean isInsideServiceArea(Coord coord) {
    Point point = MGC.coord2Point(coord);
    return serviceArea.contains(point);  // JTS geometry operation
}
```

## DRT Leg Processing in PT Trips

When PT trip includes DRT access/egress:
1. Identify DRT legs (mode = "drt" or "drt_access")
2. Extract travel time and waiting time from DrtRoute
3. Replace DRT legs with synthetic walk legs for base PT predictor
4. Pass DRT-specific times to AstraPtVariables
5. Add DRT disutility component to PT utility

```java
protected double estimateDrtAccessEgressUtility(AstraPtVariables variables) {
    if (!variables.hasDrtAccess) return 0.0;
    double utility = 0.0;
    utility += parameters.astraDRT.betaInVehicleTime * variables.drtAccessEgressTime_min;
    utility += parameters.astraDRT.betaWaitingTime * variables.drtWaitingTime_min;
    return utility;
}
```

---

# CALIBRATION PARAMETERS

These parameters are passed at runtime via Java system properties:

| System Property | Parameter | Default Value |
|-----------------|-----------|---------------|
| ALPHA_WALK | Walk ASC | 0.9 |
| ALPHA_BIKE | Bike ASC | 0.65 |
| ALPHA_PT | PT ASC | 0 |
| ALPHA_CAR | Car ASC | 0.5 |
| BETA_CAR_CITY | Car city indicator | -0.459 |
| DRT_FARE_CHF | DRT base fare | 10 CHF |
| DRT_FARE_CHF_KM | DRT distance fare | 0 CHF/km |

Runtime injection example:
```bash
java -Xmx128G \
    -DDRT_FARE_CHF=10 \
    -DDRT_FARE_CHF_KM=0 \
    -DALPHA_WALK=0.9 \
    -DALPHA_BIKE=0.65 \
    -DALPHA_PT=0 \
    -DALPHA_CAR=0.5 \
    -DBETA_CAR_CITY=-0.459 \
    -cp abmt2025.jar abmt2025.project.mode_choice.RunSimulation_DRT \
    --config-path config.xml
```

---

# KEY REFERENCES

- Bösch, P. M., Becker, F., Becker, H., & Axhausen, K. W. (2018). Cost-based analysis of autonomous mobility services. Transport Policy, 64, 76-91.
- Hörl, S., Balac, M., & Axhausen, K. W. (2019). Dynamic demand estimation for an AMoD system in Paris. IEEE ITSC.
- eqasim-java framework: https://github.com/eqasim-org/eqasim-java

---

# LATEX SNIPPETS FOR EQUATIONS

## Multinomial Logit Choice Probability
```latex
P_i = \frac{e^{V_i}}{\sum_{j \in C} e^{V_j}}
```

## General Utility Function Structure
```latex
V_m = \alpha_m + \sum_k \beta_{k,m} \cdot x_{k,m} + \beta_{\text{cost}} \cdot c_m \cdot f(I, I_{\text{ref}}, \lambda_{\text{cost,I}})
```

## Elasticity Interaction
```latex
f(x, x_{\text{ref}}, \lambda) = \left(\frac{x}{x_{\text{ref}}}\right)^{\lambda}
```

## DRT Maximum Travel Time Constraint
```latex
T_{\max} = \alpha \cdot T_{\text{unshared}} + \beta
```

## Operator Cost Function
```latex
C_{\text{operator}} = c_{\text{vkm}} \cdot \sum_v d_v + c_{\text{driver}} \cdot \sum_v h_v + c_{\text{fixed}} \cdot N
```

## Walk Penalty Function
```latex
P_{\text{walk}}(t) = -e^{\gamma t} + 1, \quad \gamma = \frac{\ln(100)}{T_{\text{threshold}}}
```

---

# NOTES FOR THESIS WRITING

1. **Disabled parameters**: The DRT utility estimator has access/egress time and age-over-60 terms defined but commented out in the code. Document this as a modeling decision.

2. **Flat fare assumption**: Default scenario uses flat 10 CHF fare with no distance component.

3. **Intermodal distinction**: The `drt_access` mode is distinct from standalone `drt`. It only appears as access/egress legs within PT trips, not as a standalone mode choice alternative.

4. **Swiss-specific elements**: OVGK (ÖV-Güteklasse) is a Swiss public transport quality classification that affects PT utility.

5. **Soft constraints**: DRT operational constraints are "soft" (rejectRequestIfMaxWaitOrTravelTimeViolated = false), meaning requests are accepted even if constraints would be violated.

6. **Tour-based model**: Mode choice is tour-based, not trip-based. Vehicle continuity (car, bike) is enforced across tours.
