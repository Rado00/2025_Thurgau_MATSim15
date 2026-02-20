# PhD Thesis Methodology Reference Document (PART 2)
## DRT Simulation in MATSim - LOGIC, ALGORITHMS, AND FORMULAS
## For use with normal Claude chat to write LaTeX in Overleaf

**Purpose**: This document explains HOW the simulation works -- the logic, algorithms, control flow, and formulas -- extracted from actual Java source code. Use this together with THESIS_METHODOLOGY_REFERENCE.md (which has the parameter values) when writing LaTeX methodology sections.

**Instructions for Claude in Overleaf chat**: The user is writing a PhD thesis methodology chapter. Write formal academic LaTeX. Use `\begin{equation}`, `\begin{algorithm}`, `\begin{table}`, etc. Cite Horni et al. (2016) for MATSim, Horl et al. (2021) for eqasim, Bischoff et al. (2017) for DRT in MATSim, Bosch et al. (2018) for operator costs. Use consistent notation throughout.

---

# =========================================================================
# SECTION 3.5.1: SIMULATION FRAMEWORK -- HOW THE CO-EVOLUTIONARY LOOP WORKS
# =========================================================================

## 1.1 The MATSim Co-Evolutionary Algorithm

MATSim uses an iterative co-evolutionary algorithm. It does NOT solve an equilibrium analytically. Instead, it simulates each agent's day repeatedly, allowing them to adapt.

### Loop structure (per iteration k):

```
ITERATION k:
  1. MOBSIM (QSim): Execute all agents' plans on the network simultaneously
     - Vehicles move on links following queue-based traffic dynamics (FIFO)
     - PT vehicles follow published schedules
     - DRT vehicles are dispatched in real-time by the DVRP optimizer
     - Events are generated (LinkEnter, LinkLeave, PersonArrival, etc.)

  2. SCORING: Evaluate how each agent's executed plan performed
     - In THIS simulation: scoring is handled ENTIRELY by the eqasim DMC framework
     - MATSim native scoring (Charypar-Nagel) is set to placeholder values
     - The actual utility comes from the discrete mode choice estimators (Section 3.5.3)

  3. REPLANNING: Some agents reconsider their plans
     - 5% of agents invoke DiscreteModeChoice strategy (draw new mode for entire tour)
     - 95% of agents KeepLastSelected (no change)
     - maxAgentPlanMemorySize = 1, so only ONE plan is kept (the current one)
     - This is "mode choice in the loop" -- no best-response, just sampling

  4. CONVERGENCE CHECK:
     - Monitor mode shares over rolling window of 10 iterations
     - Smooth with exponential window of 20 iterations
     - If all monitored modes change by less than 0.001 -> terminate
     - Otherwise continue to iteration k+1
```

### Key architectural insight:
The simulation uses "single-plan mode" (enforceSinglePlan=true, maxAgentPlanMemorySize=1). This means agents do NOT accumulate plans and select the best. Instead, each iteration either:
- Draws a completely new mode choice (5% probability)
- Keeps the current mode (95% probability)

This is a Markov chain over mode choices. Convergence means the mode share distribution is stationary.

### LaTeX equation for the replanning probability:

```latex
P(\text{replan}_k) = \begin{cases}
  0.05 & \text{agent draws new mode via DMC (MultinomialLogit)} \\
  0.95 & \text{agent keeps current mode}
\end{cases}
```

## 1.2 How eqasim Integrates with MATSim

The eqasim framework replaces MATSim's default mode choice with a discrete choice model. The integration works through Guice dependency injection:

```
MATSim Controler
  |
  +-- EqasimModeChoiceModule (replaces default mode choice)
  |     |
  |     +-- DiscreteModeChoice strategy (called during replanning)
  |           |
  |           +-- TourFinder: identifies tours (home -> ... -> home)
  |           +-- ModeAvailability: which modes can this agent use?
  |           +-- TripConstraints: is this mode feasible for this trip?
  |           +-- TripEstimator: compute V_mode for each alternative
  |           +-- Selector: MultinomialLogit choice probability
  |
  +-- SwissModeChoiceModule (Swiss-specific parameters)
  |
  +-- AstraModule_DRT (project-specific: DRT estimator, cost model, predictors)
  |
  +-- DvrpModule + MultiModeDrtModule (DRT vehicle dispatching)
  |
  +-- DrtIntermodalFilterModule (intermodal PT+DRT with service area filtering)
  |
  +-- SmoothingTravelTimeModule (travel time estimation between iterations)
```

### Class Hierarchy for Mode Choice:

```
For each mode, there is a chain:
  Predictor  -->  Variables  -->  Estimator (computes utility)

Car:    CarPredictor         -> CarVariables         -> AstraCarUtilityEstimator_DRT
PT:     AstraPtPredictor     -> AstraPtVariables     -> AstraPtUtilityEstimator_DRT
Bike:   AstraBikePredictor   -> AstraBikeVariables   -> AstraBikeUtilityEstimator_DRT
Walk:   AstraWalkPredictor   -> AstraWalkVariables   -> AstraWalkUtilityEstimator_DRT
DRT:    DRTPredictor         -> DRTVariables         -> DRTUtilityEstimator

Cross-cutting predictors (used by all estimators):
  AstraPersonPredictor -> AstraPersonVariables (age, householdIncome)
  AstraTripPredictor   -> AstraTripVariables   (isWork, isCity)
```

## 1.3 Travel Time Learning Between Iterations

The SmoothingTravelTime module tracks link-level travel times across iterations using exponential smoothing:

```latex
\hat{T}_l^{(k)}(t) = \begin{cases}
  T_l^{\text{obs},(k)}(t) & \text{if } k = 0 \\
  (1 - \alpha) \cdot \hat{T}_l^{(k-1)}(t) + \alpha \cdot T_l^{\text{obs},(k)}(t) & \text{if } k > 0
\end{cases}
```

Where:
- l = link index, t = time bin, k = iteration
- alpha_increasing is used when observed TT > current estimate (congestion building)
- alpha_decreasing is used when observed TT < current estimate (congestion dissipating)
- Default travel time = link.length / link.freespeed (free-flow)
- Travel times are binned in 900-second (15-minute) intervals
- The estimate is always >= free-flow time: max(estimate, default)

Source: `SmoothingTravelTime.java:123-146`

### DRT Travel Time Estimation (DVRP)

DRT vehicles use a separate travel time estimator configured in the DVRP module:
- travelTimeEstimationAlpha = 0.05 (offline exponential moving average)
- travelTimeEstimationBeta = 0.0 (no online correction)
- mobsimMode = "car" (DRT vehicles experience the same travel times as car traffic)

Source: config XML lines 988-998

## 1.4 Network Adjustments

Before simulation, minor roads receive a crossing penalty:

```
For each link in network:
  if link is NOT a major road (lower capacity than incoming links)
    AND has multiple incoming links at destination node:
      travelTime = link.length / link.freespeed + crossingPenalty (3.0 s)
      link.freespeed = link.length / travelTime
```

Source: `RunSimulation_DRT.java:117-132`

---

# =========================================================================
# SECTION 3.5.2: DRT ROUTING AND DISPATCHING -- HOW VEHICLES SERVE REQUESTS
# =========================================================================

## 2.1 DRT Operational Scheme

The DRT system uses `operationalScheme = serviceAreaBased`, meaning:
- Vehicles can pick up/drop off anywhere within the service area polygon
- No fixed stops (door-to-door within the area)
- Service area defined by a shapefile polygon
- Both origin AND destination must be inside the polygon (enforced by DMC ShapeFile constraint with requirement=BOTH)

## 2.2 The Insertion Heuristic Algorithm

MATSim's DRT extension uses the **parallel insertion heuristic** for dispatching. This is NOT a simple nearest-vehicle assignment. The algorithm works as follows:

```
ALGORITHM: DRT Request Insertion (SelectiveInsertionSearch)

INPUT: New request r = (origin, destination, earliest_departure, latest_departure)
       Current vehicle schedules S = {s_1, s_2, ..., s_n}

1. COMPUTE unshared ride time:
   T_direct = shortest_path_time(r.origin, r.destination)

2. COMPUTE time windows:
   T_max_wait = MAX_WAIT_TIME                           (= 600 s)
   T_max_travel = alpha * T_direct + beta               (= 2.0 * T_direct + 240 s)
   latest_pickup = r.earliest_departure + T_max_wait
   latest_dropoff = r.earliest_departure + T_max_wait + T_max_travel

3. ZONE-BASED FILTERING (SelectiveInsertionSearch):
   - Network is divided into grid cells (500 m x 500 m)
   - Use restrictiveBeelineSpeedFactor (0.5) to estimate which vehicles
     could possibly reach the pickup within the time window
   - Only evaluate candidate vehicles in reachable zones

4. For each candidate vehicle v:
   For each possible PICKUP insertion point i in v's schedule:
     For each possible DROPOFF insertion point j >= i:
       a. Compute detour time for pickup insertion at position i
       b. Compute detour time for dropoff insertion at position j
       c. Check constraints:
          - Pickup before latest_pickup?
          - Dropoff before latest_dropoff?
          - All EXISTING passengers still meet their constraints?
          - Vehicle capacity not exceeded between i and j?
       d. If feasible, compute insertion cost:
          cost = total_detour_time (impact on all passengers + vehicle)

5. SELECT insertion with minimum cost
   - If rejectRequestIfMaxWaitOrTravelTimeViolated = false (soft constraints):
     Accept even if constraints violated, but add penalty to cost
   - If true: reject request if no feasible insertion exists

6. UPDATE vehicle schedule with new pickup and dropoff stops

OUTPUT: Assigned vehicle and updated schedule, or rejection
```

### Parallelism:
- numberOfThreads = 4 (parallel path computation for insertion candidates)
- Path data computation is the bottleneck, parallelized across threads

### Soft vs Hard Constraints:
In this simulation, `rejectRequestIfMaxWaitOrTravelTimeViolated = false`, meaning:
- Requests are NEVER rejected
- If no insertion meets the time constraints, the least-bad insertion is chosen
- A penalty cost is added to make constraint-violating insertions less attractive
- This ensures every DRT request gets served (important for mode choice equilibrium)

## 2.3 Maximum Travel Time Constraint

```latex
T_{\max}^{\text{travel}} = \alpha \cdot T_{\text{unshared}} + \beta
```

Where:
- T_unshared = shortest-path travel time for a direct (non-shared) ride
- alpha = 2.0 (passengers accept up to 2x the direct ride time)
- beta = 240 s (4 minutes of absolute buffer)

Example: A trip that takes 10 minutes direct -> max allowed = 2 * 10 + 4 = 24 minutes

## 2.4 DRT Predictor: How Utility Variables Are Extracted

When the mode choice model needs to evaluate a DRT trip, the DRTPredictor extracts variables from the simulated plan elements:

```
ALGORITHM: DRT Variable Extraction (DRTPredictor.predict)

INPUT: Person, trip, list of plan elements from routing

1. For each plan element:
   If element is a Leg with mode "drt":
     Extract DrtRoute from the leg
     waitingtime_min = DrtRoute.getMaxWaitTime() / 60
     invehicletime_min = DrtRoute.getMaxTravelTime() / 60

2. Compute cost:
   cost_CHF = DRTCostModel.calculateCost(person, trip, elements)
            = DRTFare_CHF + DRTFare_CHF_km * inVehicleDistance_km

3. accesstime_min = 0  (walk to/from DRT pickup is negligible in door-to-door)

OUTPUT: DRTVariables(cost_CHF, invehicletime_min, accesstime_min, waitingtime_min)
```

**Important note on DRT waiting/travel time**: The DRTPredictor reads `getMaxWaitTime()` and `getMaxTravelTime()` from the DrtRoute. These are the CONSTRAINT values (upper bounds), not the actual experienced times. This means:
- The waiting time used in utility = the maximum allowed waiting time from the constraint
- The in-vehicle time used = the maximum allowed travel time from the constraint
- In practice, actual times may be shorter, but the mode choice uses these conservative estimates

Source: `DRTPredictor.java:36-78`

## 2.5 Rebalancing

No explicit rebalancing strategy is configured. The commented-out `DrtModeChoiceModule` (config lines 967-987) suggests spatio-temporal rebalancing was considered but is disabled. Vehicles remain where their last dropoff occurred.

The zonal system (GridFromNetwork, 500m cells) is used by the SelectiveInsertionSearch to efficiently find candidate vehicles, NOT for rebalancing.

---

# =========================================================================
# SECTION 3.5.3: DISCRETE MODE CHOICE MODEL -- UTILITY FUNCTIONS IN DETAIL
# =========================================================================

## 3.1 Tour-Based Model Structure

Mode choice is TOUR-based, not trip-based. This means:

```
ALGORITHM: Tour-Based Discrete Mode Choice

INPUT: Agent's daily plan with activities and trips

1. TOUR IDENTIFICATION (ActivityBased finder):
   Split plan at "home" and "outside" activities into tours
   Example: home -> work -> shop -> home = one tour with 3 trips
   Maximum tour length: 10 trips

2. For each tour:
   a. ENUMERATE feasible mode combinations:
      - Available modes from AstraModeAvailability_DRT
      - Apply tour constraints:
        * VehicleContinuity: car and bike must return to pickup location
        * FromTripBased: trip-level constraints propagated to tour
      - Apply trip constraints for each trip:
        * ShapeFile: DRT requires BOTH origin AND destination in service area
        * TransitWalk: PT requires walk access/egress to stops
        * InfiniteHeadway: PT rejected if headway at origin is infinite
        * PassengerConstraint: car_passenger availability
        * OutsideConstraint: "outside" mode restrictions

   b. For each feasible mode combination:
      COMPUTE tour utility = sum of trip utilities (Cumulative estimator)

   c. SELECT mode combination via MultinomialLogit:
      P(combination_i) = exp(V_i) / sum_j(exp(V_j))
      - minimumUtility = -700, maximumUtility = 700 (clipping)
      - If utility = -Infinity (from DRT failure), alternative is excluded
```

## 3.2 Mode Availability Logic

```
ALGORITHM: Mode Availability (AstraModeAvailability_DRT)

INPUT: Person, list of trips

1. Get base Swiss mode availability from SwissModeAvailability delegate:
   - car: available if person has driving license AND car available
   - bike: available if bikeAvailability != "FOR_NONE"
   - pt: always available (if walk is available)
   - walk: always available
   - car_passenger, truck, outside: based on person attributes

2. If walk is in the available modes:
   Add "drt" to available modes

   (This means DRT is available to everyone who can walk)

3. Additional spatial constraint (applied at trip level, not here):
   DRT trip requires BOTH origin AND destination inside service area shapefile
```

Source: `AstraModeAvailability_DRT.java:39-45`

### Bike Availability Adjustment
Before simulation, bike availability is randomly reduced based on a configuration parameter. For each non-freight person, if their bikeAvailability is not "FOR_NONE", there is a probability (1 - bikeAvailability_fraction) of setting it to "FOR_NONE". This uses the global random seed (42) for reproducibility.

Source: `AstraConfigurator_DRT.java:96-109`

## 3.3 The Elasticity Interaction Function

Multiple utility terms are modified by an "interaction" function that captures how the sensitivity to travel time or cost varies with trip distance or household income:

```latex
f(x, x_{\text{ref}}, \lambda) = \left(\frac{x}{x_{\text{ref}}}\right)^{\lambda}
```

This is implemented in `EstimatorUtils.interaction()` and used as follows:
- Travel time sensitivity DECREASES with distance (lambda_tt_distance = 0.1147 > 0)
  For long trips, an extra minute matters less than for short trips
- Cost sensitivity DECREASES with income (lambda_cost_income = -0.8169 < 0)
  Higher income -> less sensitive to cost (the ratio I/I_ref > 1 raised to negative power < 1)
- Cost sensitivity INCREASES with distance (lambda_cost_distance = -0.2209 < 0)
  Longer trips -> more cost-sensitive (but note: cost itself also increases with distance)

## 3.4 Detailed Utility Functions

### 3.4.1 Car Utility

```latex
V_{\text{car}} = \underbrace{\alpha_{\text{car}}}_{\text{ASC}}
  + \underbrace{\beta_{\text{tt,car}} \cdot t_{\text{car}} \cdot f(d_{\text{eucl}}, d_{\text{ref}}, \lambda_{\text{tt,d}})}_{\text{in-vehicle time (distance-adjusted)}}
  + \underbrace{\beta_{\text{acc}} \cdot t_{\text{access}}}_{\text{access/egress walk time}}
  + \underbrace{\beta_{\text{cost}} \cdot c_{\text{car}} \cdot d_{\text{car}} \cdot f(I, I_{\text{ref}}, \lambda_{\text{cost,I}})}_{\text{monetary cost (income-adjusted)}}
  + \underbrace{\beta_{\text{work}} \cdot \mathbb{1}_{\text{work}}}_{\text{work trip penalty}}
  + \underbrace{\beta_{\text{city}} \cdot \mathbb{1}_{\text{city}}}_{\text{urban area penalty}}
```

Java implementation chain:
```
AstraCarUtilityEstimator_DRT.estimateUtility():
  utility  = estimateConstantUtility()                    // alpha_car (from SwissModeParameters)
  utility += estimateTravelTimeUtility(variables)          // beta_tt * travelTime * f(d)
  utility += estimateAccessEgressTimeUtility(variables)    // from parent CarUtilityEstimator
  utility += estimateMonetaryCostUtility(variables, personVars) // beta_cost * cost * f(I)
  utility += estimateAgeUtility(personVariables)           // beta_age60 * 1{age>=60}  (= 0 currently)
  utility += estimateWorkUtility(tripVariables)            // beta_work * 1{isWork}
  utility += estimateCityUtility(tripVariables)            // beta_city * 1{isCity}
```

The "city" attribute is determined from the activity: `activity.getAttributes().getAttribute("city")`. This is a boolean attribute pre-computed in the population file based on spatial location.

### 3.4.2 Public Transport Utility

PT utility is the most complex because it distinguishes rail vs bus travel time, and handles feeder services differently:

```latex
V_{\text{pt}} = \alpha_{\text{pt}}
  + \underbrace{\beta_{\text{rail}} \cdot t_{\text{rail}} \cdot f(d, d_{\text{ref}}, \lambda_{\text{tt,d}})}_{\text{rail in-vehicle time}}
  + V_{\text{bus/feeder}}
  + \underbrace{\beta_{\text{wait}} \cdot t_{\text{wait}}}_{\text{waiting time}}
  + \underbrace{\beta_{\text{acc}} \cdot t_{\text{access}}}_{\text{walk access/egress}}
  + \underbrace{\beta_{\text{switch}} \cdot n_{\text{transfers}}}_{\text{line switches}}
  + \underbrace{\beta_{\text{cost}} \cdot c_{\text{pt}} \cdot f(I, I_{\text{ref}}, \lambda_{\text{cost,I}})}_{\text{monetary cost}}
  + \underbrace{\beta_{\text{headway}} \cdot h}_{\text{service frequency}}
  + \underbrace{\beta_{\text{OVGK}}}_{\text{PT quality class}}
  + \underbrace{V_{\text{DRT,access}}}_{\text{intermodal DRT component}}
```

#### The feeder logic (critical for methodology):

```latex
V_{\text{bus/feeder}} = \begin{cases}
  \beta_{\text{feeder}} \cdot t_{\text{bus}} & \text{if } t_{\text{rail}} > 0 \text{ AND } t_{\text{bus}} > 0 \text{ (feeder trip)} \\
  \beta_{\text{bus}} \cdot t_{\text{bus}} \cdot f(d, d_{\text{ref}}, \lambda_{\text{tt,d}}) & \text{otherwise (bus-only trip)}
\end{cases}
```

This means:
- When a trip involves BOTH rail AND bus, the bus portion is treated as a "feeder" with a HIGHER disutility coefficient (beta_feeder = -0.0452 vs beta_bus = -0.0124). The feeder coefficient does NOT get the distance interaction.
- When a trip is bus-only, the standard bus coefficient with distance interaction applies.

Source: `AstraPtUtilityEstimator_DRT.java:40-61`

#### OVGK (OV-Guteklasse) -- Swiss PT quality classification:

OVGK is calculated from the transit schedule based on stop proximity and service frequency at the trip origin. It ranges from A (best) to None (no PT).

```latex
\beta_{\text{OVGK}} = \begin{cases}
  0 & \text{if OVGK} = A \\
  -1.7436 & \text{if OVGK} = B \\
  -1.6413 & \text{if OVGK} = C \\
  -0.9649 & \text{if OVGK} = D \\
  -1.0889 & \text{if OVGK} = \text{None}
\end{cases}
```

The OVGK is computed at BOTH origin and destination; the WORST of the two is used.

Source: `AstraPtPredictor.java:209-211`

#### Headway processing:
Headway (service frequency) is pre-computed and stored as an attribute on activities. If a trip's origin has infinite headway (no PT service), the InfiniteHeadwayConstraint rejects PT mode for that trip.

Source: `InfiniteHeadwayConstraint.java:19-28`

### 3.4.3 Bicycle Utility

```latex
V_{\text{bike}} = \alpha_{\text{bike}}
  + \beta_{\text{tt,bike}} \cdot t_{\text{bike}} \cdot f(d_{\text{eucl}}, d_{\text{ref}}, \lambda_{\text{tt,d}})
  + \beta_{\text{age60}} \cdot \mathbb{1}_{\text{age} \geq 60}
```

Bike extends SwissBikeUtilityEstimator. The Astra layer adds:
- Distance interaction on travel time (via EstimatorUtils.interaction)
- Age-over-60 indicator (strong negative: -2.6588)

Note: No monetary cost component for bike.

Source: `AstraBikeUtilityEstimator_DRT.java:40-68`

### 3.4.4 Walk Utility

```latex
V_{\text{walk}} = \alpha_{\text{walk}}
  + \beta_{\text{tt,walk}} \cdot t_{\text{walk}} \cdot f(d_{\text{eucl}}, d_{\text{ref}}, \lambda_{\text{tt,d}})
  + P_{\text{walk}}(t_{\text{walk}})
  + \beta_{\text{age60}} \cdot \mathbb{1}_{\text{age} \geq 60}
  + \beta_{\text{work}} \cdot \mathbb{1}_{\text{work}}
```

#### The exponential walk penalty function:

```latex
P_{\text{walk}}(t) = -e^{\gamma \cdot t} + 1
```

```latex
\gamma = \frac{\ln(100)}{T_{\text{threshold}}}
```

With T_threshold = 120 minutes:
- At t=0 min: P = -exp(0)+1 = 0 (no penalty)
- At t=60 min: P = -exp(ln(100)*60/120)+1 = -10+1 = -9
- At t=120 min: P = -exp(ln(100))+1 = -100+1 = -99
- At t>120 min: P explodes exponentially -> walk becomes extremely unattractive

This is a "soft cap" that makes walking unrealistic beyond ~2 hours.

Source: `AstraWalkUtilityEstimator_DRT.java:54-57`

### 3.4.5 DRT Utility (Door-to-Door)

```latex
V_{\text{DRT}} = \alpha_{\text{DRT}}
  + \beta_{\text{tt,DRT}} \cdot t_{\text{invehicle}}
  + \beta_{\text{wait,DRT}} \cdot t_{\text{wait}}
  + \beta_{\text{work}} \cdot \mathbb{1}_{\text{work}}
  + \beta_{\text{cost}} \cdot C_{\text{DRT}}
```

Key differences from other modes:
1. NO distance interaction on travel time (unlike car, PT, bike, walk)
2. NO income interaction on cost (cost enters directly via beta_cost)
3. Separate waiting time coefficient (beta_wait = -0.093, much larger than beta_tt = -0.015)
4. Waiting time is ~6x more disliked than in-vehicle time

The DRT fare enters the utility through the shared beta_cost parameter:
```latex
C_{\text{DRT}} = C_{\text{base}} + C_{\text{km}} \cdot d_{\text{invehicle}}
```

#### Safety mechanism:
If DRT variable prediction fails (e.g., no route found), the estimator returns -Infinity, effectively excluding DRT from the choice set for that trip.

Source: `DRTUtilityEstimator.java:80-125`

### 3.4.6 Multinomial Logit Selection

After computing utilities for all feasible mode combinations for a tour, the MultinomialLogit selector draws a choice:

```latex
P(\text{tour}_i) = \frac{\exp(V_i)}{\sum_{j \in \mathcal{C}} \exp(V_j)}
```

Where:
- V_i = cumulative utility of tour i = sum of trip utilities for all trips in the tour
- C = choice set of feasible tour-mode combinations
- Utilities are clipped to [-700, 700] to prevent numerical overflow
- If V = -Infinity (from DRT failure), that alternative has P = 0

---

# =========================================================================
# SECTION 3.5.4: INTERMODAL PT+DRT ROUTING -- THE FEEDER MODULE
# =========================================================================

## 4.1 Architecture Overview

The intermodal PT+DRT system allows DRT to serve as access/egress for PT stops. It uses SwissRailRaptor's built-in intermodal routing capability, enhanced with a custom service area filter.

```
SwissRailRaptor (PT router)
  |
  +-- intermodalAccessEgress: walk
  |     radius: 500m -> 1000m (step 200m)
  |
  +-- intermodalAccessEgress: drt_access
        radius: 2000m -> 5000m (step 1000m)
        |
        +-- FilteredDrtIntermodalRoutingModule
              |
              +-- DrtServiceAreaFilter (point-in-polygon check)
              |
              +-- DRT Router (delegate, actual DRT routing)
```

## 4.2 The Complete Intermodal Routing Algorithm

```
ALGORITHM 1: Intermodal PT+DRT Route Finding

INPUT: Trip from origin O to destination D, departure time t

1. FIND CANDIDATE PT STOPS for access:
   Search for stops near O within increasing radii:
   - Start at initialSearchRadius (500m for walk, 2000m for drt_access)
   - If no stops found, extend by searchExtensionRadius
   - Up to maxRadius (1000m for walk, 5000m for drt_access)

2. FIND CANDIDATE PT STOPS for egress:
   Same search process near D

3. For each access stop s_a and egress stop s_e:
   a. COMPUTE ACCESS LEG OPTIONS:
      Option A: Walk from O to s_a
        - Time = beeline_distance * 1.3 / 1.2 m/s
        - Cost = 0

      Option B: DRT from O to s_a (via drt_access mode)
        - FilteredDrtIntermodalRoutingModule.calcRoute():
          i.   Get coordinates of O and s_a
          ii.  Check O against service area polygon (JTS contains)
          iii. Check s_a against service area polygon
          iv.  If NEITHER inside -> return null (skip, use walk)
          v.   If at least ONE inside -> delegate to DRT router
          vi.  DRT router computes route with waiting time, travel time
        - Cost = DRT fare

   b. SELECT least-cost access mode for s_a (CalcLeastCostModePerStop)

   c. COMPUTE PT LEGS:
      Use RAPTOR algorithm to find optimal PT connections from s_a to s_e
      (considers transfers, waiting, in-vehicle time for rail and bus)

   d. COMPUTE EGRESS LEG OPTIONS:
      Same as access but from s_e to D

   e. SELECT least-cost egress mode for s_e

   f. COMPUTE total generalized cost for route (O -> s_a -> PT -> s_e -> D)

4. SELECT best overall route across all (s_a, s_e) combinations

OUTPUT: Route with legs [access_leg, pt_leg_1, transfer_walk, pt_leg_2, ..., egress_leg]
        where access/egress may be walk or drt_access
```

## 4.3 Service Area Filter Logic

The DrtServiceAreaFilter loads the shapefile and creates a JTS Geometry union of all features:

```
INITIALIZATION:
  1. Read shapefile using GeoTools
  2. For each feature in shapefile:
     Extract geometry
     Union with accumulated geometry
  3. Store merged geometry as serviceArea

QUERY: isInsideServiceArea(coord)
  1. Convert MATSim Coord to JTS Point via MGC.coord2Point()
  2. Return serviceArea.contains(point)  // JTS spatial predicate
```

The filter uses a single merged polygon for all features, so multi-polygon service areas (e.g., multiple zones) are handled as one union.

Source: `DrtServiceAreaFilter.java:44-73`

## 4.4 How DRT Variables Enter PT Utility

When a PT trip includes DRT access/egress legs, the AstraPtPredictor performs a substitution:

```
ALGORITHM: PT Variable Extraction with DRT Legs (AstraPtPredictor.predict)

INPUT: Person, trip, plan elements (may include drt/drt_access legs)

1. SCAN plan elements for DRT legs:
   For each Leg in elements:
     If mode is "drt" or "drt_access":
       hasDrtAccess = true
       If route is DrtRoute:
         drtWaitingTime += route.getMaxWaitTime() / 60  (minutes)
         drtTravelTime += route.getMaxTravelTime() / 60  (minutes)
       Else:
         drtTravelTime += leg.getTravelTime() / 60       (fallback)

       REPLACE this leg with a synthetic walk leg (same travel time)
       (This allows the base PtPredictor to process the trip normally)

2. CALL delegate PtPredictor with filtered elements
   -> Gets base PT variables (inVehicleTime, waitingTime, accessEgressTime, cost, etc.)
   (The DRT legs appear as walk legs to the base predictor, so access/egress time
    includes the DRT travel time as if it were walking)

3. CLASSIFY PT vehicle legs:
   For each PT leg:
     Look up TransitRoute in schedule
     If route.transportMode == "rail":
       railTravelTime += leg time
     Else:
       busTravelTime += leg time

4. GET headway and OVGK:
   headway = origin activity attribute "headway_min"
   ovgk = worst of OVGK at origin and destination

5. RETURN AstraPtVariables(
     delegate_vars,         // base PT variables (with DRT-as-walk)
     railTravelTime,
     busTravelTime,
     headway,
     ovgk,
     drtTravelTime,         // separate DRT in-vehicle time
     drtWaitingTime,         // separate DRT waiting time
     hasDrtAccess             // flag for estimator
   )
```

Then in the estimator, the DRT component is added:

```latex
V_{\text{DRT,access}} = \begin{cases}
  \beta_{\text{tt,DRT}} \cdot t_{\text{DRT,access}} + \beta_{\text{wait,DRT}} \cdot t_{\text{DRT,wait}} & \text{if hasDrtAccess} \\
  0 & \text{otherwise}
\end{cases}
```

**Important**: The DRT travel time enters the PT utility TWICE:
1. As part of the base PT accessEgressTime (because DRT legs are replaced with walk legs)
2. As the separate DRT access/egress utility component (with DRT-specific betas)

This means the DRT access/egress time is valued at BOTH the PT access beta AND the DRT in-vehicle/waiting betas. This is a modeling detail worth noting.

Source: `AstraPtPredictor.java:88-214`, `AstraPtUtilityEstimator_DRT.java:106-120`

## 4.5 Summary of Intermodal Trip Types

The system can produce these PT trip patterns:

| Pattern | Access | PT | Egress | Description |
|---------|--------|-----|--------|-------------|
| walk_pt_walk | walk | rail/bus | walk | Standard PT trip |
| drt_pt_walk | drt_access | rail/bus | walk | DRT feeder to PT |
| walk_pt_drt | walk | rail/bus | drt_access | PT to DRT last-mile |
| drt_pt_drt | drt_access | rail/bus | drt_access | Full DRT first/last mile |

---

# =========================================================================
# SECTION 3.5.5: SCORING -- HOW AGENT PLANS ARE EVALUATED
# =========================================================================

## 5.1 Scoring Architecture

In this simulation, there are TWO scoring systems, but only ONE is active for mode choice:

### MATSim Native Scoring (Charypar-Nagel):
Used ONLY for the MATSim internal plan scoring (required by the framework).
All mode-specific parameters are set to placeholder values (-1.0 utils/hr).
Activity scoring is DISABLED (scoringThisActivityAtAll = false for all activities).
marginalUtilityOfMoney = 0 (monetary costs have no effect in native scoring).

### eqasim Discrete Mode Choice Scoring:
This is where the ACTUAL mode choice decisions happen.
The utility functions from Section 3.5.3 are computed DURING the replanning phase,
not after the mobsim. The DMC framework evaluates alternatives BEFORE execution.

### The key insight:
```
MATSim scoring:  applied AFTER mobsim execution -> affects plan memory (but only 1 plan kept)
eqasim DMC:      applied DURING replanning -> determines mode choice probabilities
```

Since maxAgentPlanMemorySize = 1 and learningRate = 1.0, the MATSim native score is simply overwritten each iteration. The DMC utility functions are what actually drive agent behavior.

## 5.2 The Full Score Formula (for completeness)

The MATSim native score per plan (mostly symbolic in this setup):

```latex
S_{\text{plan}} = \sum_{\text{activities}} S_{\text{act}} + \sum_{\text{legs}} S_{\text{leg}}
```

Where:
```latex
S_{\text{act}} = 0 \quad \text{(all activities have scoringThisActivityAtAll = false)}
```

```latex
S_{\text{leg}} = \beta_{\text{perf}} \cdot t_{\text{travel}} + \text{ASC}_{\text{mode}}
```

But since all ASCs = 0 and marginalUtilityOfTraveling = -1.0 for all modes, this just produces a uniform negative score proportional to total travel time.

The REAL behavioral model is the eqasim DMC described in Section 3.5.3.

---

# =========================================================================
# SUMMARY: KEY CLASS FILE REFERENCES
# =========================================================================

| File | Purpose |
|------|---------|
| `RunSimulation_DRT.java` | Main entry point, wires all modules |
| `AstraConfigurator_DRT.java` | Configures eqasim estimators and constraints |
| `AstraModule_DRT.java` | Guice bindings for all DRT components |
| `AstraModeParameters_DRT.java` | All parameter values (builds from code) |
| `AstraModeAvailability_DRT.java` | DRT added to choice set if walk available |
| `DRTUtilityEstimator.java` | V_DRT computation |
| `AstraCarUtilityEstimator_DRT.java` | V_car computation |
| `AstraPtUtilityEstimator_DRT.java` | V_pt computation (incl. DRT access) |
| `AstraBikeUtilityEstimator_DRT.java` | V_bike computation |
| `AstraWalkUtilityEstimator_DRT.java` | V_walk computation (incl. penalty) |
| `DRTPredictor.java` | Extracts DRT variables from simulated routes |
| `AstraPtPredictor.java` | Extracts PT variables, handles DRT legs |
| `DRTCostModel.java` | DRT fare calculation |
| `DrtCostParameters.java` | Fare and operator cost parameters |
| `OperatorCostCalculator.java` | Post-sim operator financial analysis |
| `FilteredDrtIntermodalRoutingModule.java` | Service area filter for intermodal |
| `DrtServiceAreaFilter.java` | Shapefile loading and point-in-polygon |
| `DrtIntermodalFilterModule.java` | Guice wiring for intermodal filter |
| `SmoothingTravelTime.java` | Travel time learning across iterations |
| `InfiniteHeadwayConstraint.java` | Rejects PT if headway is infinite |
| `AstraPredictorUtils.java` | Helper: income, age, work, city lookups |
| `Thurgau_config_DRT_M15_10.xml` | Full XML configuration |
| `autoRun_DRT_parallel_and_analyse.sh` | Runtime parameters and job submission |

---

# =========================================================================
# LATEX ALGORITHM ENVIRONMENTS (ready to copy into Overleaf)
# =========================================================================

## Algorithm 1: MATSim Co-Evolutionary Loop

```latex
\begin{algorithm}[H]
\caption{MATSim Co-Evolutionary Simulation Loop}
\label{alg:matsim-loop}
\begin{algorithmic}[1]
\REQUIRE Initial population with activity plans, transport network, transit schedule, DRT fleet
\ENSURE Converged mode shares and travel patterns
\STATE $k \gets 0$
\REPEAT
  \STATE \textbf{Mobsim:} Execute all agent plans simultaneously on network (QSim)
  \STATE \quad DRT vehicles dispatched via insertion heuristic
  \STATE \quad PT vehicles follow published schedules
  \STATE \textbf{Scoring:} Evaluate executed plans (placeholder scoring)
  \STATE \textbf{Replanning:} For each agent $n$:
  \STATE \quad Draw $u \sim \text{Uniform}(0,1)$
  \IF{$u < 0.05$}
    \STATE Invoke \textsc{DiscreteModeChoice}: draw new tour modes via MNL
  \ELSE
    \STATE Keep current mode assignment
  \ENDIF
  \STATE $k \gets k + 1$
  \STATE Compute smoothed mode shares $\bar{s}_m^{(k)}$ over last 20 iterations
\UNTIL{$\max_m |\bar{s}_m^{(k)} - \bar{s}_m^{(k-10)}| < 0.001$ \OR $k = k_{\max}$}
\end{algorithmic}
\end{algorithm}
```

## Algorithm 2: DRT Insertion Heuristic

```latex
\begin{algorithm}[H]
\caption{DRT Request Insertion with Selective Search}
\label{alg:drt-insertion}
\begin{algorithmic}[1]
\REQUIRE Request $r = (o, d, t_{\text{dep}})$, vehicle schedules $\mathcal{S}$
\ENSURE Updated schedule with $r$ inserted
\STATE $T_{\text{direct}} \gets \textsc{ShortestPath}(o, d)$
\STATE $T_{\max}^{\text{wait}} \gets 600\text{s}$
\STATE $T_{\max}^{\text{travel}} \gets \alpha \cdot T_{\text{direct}} + \beta$
\STATE $\mathcal{V}_{\text{cand}} \gets \textsc{ZoneFilter}(\mathcal{S}, o, T_{\max}^{\text{wait}})$ \COMMENT{Grid-based filtering}
\STATE $\text{best\_cost} \gets \infty$
\FOR{each vehicle $v \in \mathcal{V}_{\text{cand}}$}
  \FOR{each pickup position $i$ in schedule of $v$}
    \FOR{each dropoff position $j \geq i$}
      \STATE Compute detour and check constraints
      \IF{feasible \AND cost $<$ best\_cost}
        \STATE best\_cost $\gets$ cost; best\_insertion $\gets (v, i, j)$
      \ENDIF
    \ENDFOR
  \ENDFOR
\ENDFOR
\IF{best\_insertion found}
  \STATE Insert $r$ into schedule at best\_insertion
\ELSE
  \STATE Insert at least-bad position (soft constraints)
\ENDIF
\end{algorithmic}
\end{algorithm}
```

## Algorithm 3: Intermodal PT+DRT Route Finding

```latex
\begin{algorithm}[H]
\caption{Intermodal PT+DRT Route Finding}
\label{alg:intermodal}
\begin{algorithmic}[1]
\REQUIRE Origin $O$, destination $D$, departure time $t$, service area polygon $\mathcal{A}$
\ENSURE Best intermodal route
\STATE Find candidate access stops $\mathcal{S}_a$ near $O$ (radius 500--5000m)
\STATE Find candidate egress stops $\mathcal{S}_e$ near $D$ (radius 500--5000m)
\FOR{each $s_a \in \mathcal{S}_a$, $s_e \in \mathcal{S}_e$}
  \STATE \textbf{Access:} $c_{\text{walk}} \gets \textsc{WalkCost}(O, s_a)$
  \IF{$O \in \mathcal{A}$ \OR $s_a \in \mathcal{A}$}
    \STATE $c_{\text{drt}} \gets \textsc{DrtRouteCost}(O, s_a)$
  \ELSE
    \STATE $c_{\text{drt}} \gets \infty$ \COMMENT{Neither endpoint in service area}
  \ENDIF
  \STATE $c_{\text{access}} \gets \min(c_{\text{walk}}, c_{\text{drt}})$
  \STATE \textbf{PT:} $c_{\text{pt}} \gets \textsc{Raptor}(s_a, s_e, t)$
  \STATE \textbf{Egress:} Analogous to access for $(s_e, D)$
  \STATE $c_{\text{total}} \gets c_{\text{access}} + c_{\text{pt}} + c_{\text{egress}}$
\ENDFOR
\RETURN Route with minimum $c_{\text{total}}$
\end{algorithmic}
\end{algorithm}
```

---

# =========================================================================
# PARAMETER VALUES (see THESIS_METHODOLOGY_REFERENCE.md for full tables)
# =========================================================================

Quick reference of ALL parameter values for the utility functions:

COMMON: beta_cost = -0.0888, lambda_cost_income = -0.8169, lambda_cost_distance = -0.2209
        lambda_tt_distance = 0.1147, d_ref = 39.0 km, I_ref = 12260 CHF/month

CAR:    ASC = 0.5 (calibrated), beta_tt = -0.0192, beta_work = -1.1606, beta_city = -0.459, c_car = 0.26 CHF/km
PT:     ASC = 0 (calibrated), beta_rail = -0.0072, beta_bus = -0.0124, beta_feeder = -0.0452
        beta_wait = -0.0124, beta_acc = -0.0142, beta_headway = -0.0301, c_pt = 0.60 CHF/km
BIKE:   ASC = 0.65 (calibrated), beta_tt = -0.1258, beta_age60 = -2.6588
WALK:   ASC = 0.9 (calibrated), beta_tt = -0.0457, T_threshold = 120 min
DRT:    ASC = -0.061, beta_tt = -0.015, beta_wait = -0.093, beta_work = -1.938
        C_DRT = 10 CHF (flat fare)

OVGK:   A=0, B=-1.7436, C=-1.6413, D=-0.9649, None=-1.0889
