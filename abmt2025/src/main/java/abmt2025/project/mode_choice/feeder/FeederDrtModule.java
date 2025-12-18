package abmt2025.project.mode_choice.feeder;

import org.eqasim.core.simulation.mode_choice.AbstractEqasimExtension;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

/**
 * Guice module for Feeder DRT functionality.
 *
 * Binds the feeder DRT routing module, utility estimator, and constraint.
 * This module enables DRT to be used as access/egress mode for PT trips.
 */
public class FeederDrtModule extends AbstractEqasimExtension {

    public static final String FEEDER_DRT_MODE = "feeder_drt";

    @Override
    protected void installEqasimExtension() {
        // Bind the utility estimator for feeder_drt mode
        bindUtilityEstimator(FeederDrtUtilityEstimator.NAME).to(FeederDrtUtilityEstimator.class);

        // Bind the trip constraint for feeder_drt mode
        bindTripConstraintFactory(FeederDrtConstraint.NAME).to(FeederDrtConstraint.Factory.class);

        // Bind the routing module for feeder_drt mode
        addRoutingModuleBinding(FEEDER_DRT_MODE).to(FeederDrtRoutingModule.class);
    }

    @Provides
    @Singleton
    public FeederDrtRoutingModule provideFeederDrtRoutingModule(
            @Named(TransportMode.drt) RoutingModule drtRoutingModule,
            @Named(TransportMode.pt) RoutingModule ptRoutingModule,
            @Named(TransportMode.walk) RoutingModule walkRoutingModule,
            PopulationFactory populationFactory,
            TransitSchedule transitSchedule,
            Network network,
            FeederDrtConfigGroup config) {

        return new FeederDrtRoutingModule(
                drtRoutingModule,
                ptRoutingModule,
                walkRoutingModule,
                populationFactory,
                transitSchedule,
                network,
                config);
    }

    @Provides
    @Singleton
    public FeederDrtUtilityEstimator provideFeederDrtUtilityEstimator(
            abmt2025.project.mode_choice.estimators.DRTUtilityEstimator drtEstimator,
            abmt2025.project.mode_choice.estimators.AstraPtUtilityEstimator_DRT ptEstimator) {

        return new FeederDrtUtilityEstimator(drtEstimator, ptEstimator);
    }

    @Provides
    public FeederDrtConstraint.Factory provideFeederDrtConstraintFactory() {
        return new FeederDrtConstraint.Factory();
    }
}
