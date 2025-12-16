package abmt2025.project.mode_choice;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.AbstractEqasimExtension;
import org.eqasim.core.simulation.mode_choice.ParameterDefinition;
import org.eqasim.core.simulation.mode_choice.cost.CostModel;
import org.eqasim.switzerland.mode_choice.SwissModeAvailability;
import org.eqasim.switzerland.mode_choice.parameters.SwissCostParameters;
import org.eqasim.switzerland.mode_choice.parameters.SwissModeParameters;
import org.eqasim.switzerland.ovgk.OVGKCalculator;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.router.costcalculators.RandomizingTimeDistanceTravelDisutilityFactory;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import abmt2025.project.mode_choice.estimators.AstraBikeUtilityEstimator_Baseline;
import abmt2025.project.mode_choice.estimators.AstraCarUtilityEstimator_Baseline;
import abmt2025.project.mode_choice.estimators.AstraPtUtilityEstimator_Baseline;
import abmt2025.project.mode_choice.estimators.AstraWalkUtilityEstimator_Baseline;
import abmt2025.project.mode_choice.predictors.AstraBikePredictor;
import abmt2025.project.mode_choice.predictors.AstraPersonPredictor;
import abmt2025.project.mode_choice.predictors.AstraPtPredictor;
import abmt2025.project.mode_choice.predictors.AstraTripPredictor;
import abmt2025.project.mode_choice.predictors.AstraWalkPredictor;

public class AstraModule_Baseline extends AbstractEqasimExtension {
	private final CommandLine commandLine;

	public AstraModule_Baseline(CommandLine commandLine) {
		this.commandLine = commandLine;
	}

	@Override
	protected void installEqasimExtension() {
		bindUtilityEstimator(AstraCarUtilityEstimator_Baseline.NAME).to(AstraCarUtilityEstimator_Baseline.class);
		bindUtilityEstimator(AstraPtUtilityEstimator_Baseline.NAME).to(AstraPtUtilityEstimator_Baseline.class);
		bindUtilityEstimator(AstraBikeUtilityEstimator_Baseline.NAME).to(AstraBikeUtilityEstimator_Baseline.class);
		bindUtilityEstimator(AstraWalkUtilityEstimator_Baseline.NAME).to(AstraWalkUtilityEstimator_Baseline.class);

		bind(AstraPtPredictor.class);
		bind(AstraBikePredictor.class);
		bind(AstraWalkPredictor.class);
		bind(AstraPersonPredictor.class);
		bind(AstraTripPredictor.class);

		bindTripConstraintFactory(InfiniteHeadwayConstraint.NAME).to(InfiniteHeadwayConstraint.Factory.class);

		bind(SwissModeParameters.class).to(AstraModeParameters_Baseline.class);

		bind(SwissModeAvailability.class);
		bindModeAvailability(AstraModeAvailability_Baseline.NAME).to(AstraModeAvailability_Baseline.class);

	}


	@Provides
	@Singleton
	public AstraModeParameters_Baseline provideAstraModeParameters(EqasimConfigGroup config)
			throws IOException, ConfigurationException {
		AstraModeParameters_Baseline parameters = AstraModeParameters_Baseline.buildFrom6Feb2020();

		if (config.getModeParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getModeParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("mode-parameter", commandLine, parameters);
		return parameters;
	}

	@Provides
	@Singleton
	public OVGKCalculator provideOVGKCalculator(TransitSchedule transitSchedule) {
		return new OVGKCalculator(transitSchedule);
	}

	@Provides
	public AstraModeAvailability_Baseline provideAstraModeAvailability(SwissModeAvailability delegate) {
		return new AstraModeAvailability_Baseline(delegate);
	}

}