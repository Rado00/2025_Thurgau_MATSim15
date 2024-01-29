package abmt2023.project.mode_choice;

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
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import abmt2023.project.mode_choice.costs.DRTCostModel;
import abmt2023.project.mode_choice.estimators.AstraBikeUtilityEstimator;
import abmt2023.project.mode_choice.estimators.AstraCarUtilityEstimator;
import abmt2023.project.mode_choice.estimators.AstraPtUtilityEstimator;
import abmt2023.project.mode_choice.estimators.AstraWalkUtilityEstimator;
import abmt2023.project.mode_choice.estimators.DRTUtilityEstimator;
import abmt2023.project.mode_choice.predictors.AstraBikePredictor;
import abmt2023.project.mode_choice.predictors.AstraPersonPredictor;
import abmt2023.project.mode_choice.predictors.AstraPtPredictor;
import abmt2023.project.mode_choice.predictors.AstraTripPredictor;
import abmt2023.project.mode_choice.predictors.AstraWalkPredictor;
import abmt2023.project.mode_choice.predictors.DRTPredictor;

public class AstraModule extends AbstractEqasimExtension {
	private final CommandLine commandLine;

	public AstraModule(CommandLine commandLine) {
		this.commandLine = commandLine;
	}

	@Override
	protected void installEqasimExtension() {
		bindUtilityEstimator(AstraCarUtilityEstimator.NAME).to(AstraCarUtilityEstimator.class);
		bindUtilityEstimator(AstraPtUtilityEstimator.NAME).to(AstraPtUtilityEstimator.class);
		bindUtilityEstimator(AstraBikeUtilityEstimator.NAME).to(AstraBikeUtilityEstimator.class);
		bindUtilityEstimator(AstraWalkUtilityEstimator.NAME).to(AstraWalkUtilityEstimator.class);
		bindUtilityEstimator(DRTUtilityEstimator.NAME).to(DRTUtilityEstimator.class);

		bind(AstraPtPredictor.class);
		bind(AstraBikePredictor.class);
		bind(AstraWalkPredictor.class);
		bind(AstraPersonPredictor.class);
		bind(AstraTripPredictor.class);
		bind(DRTPredictor.class);
		bind(DRTCostModel.class);

		bindTripConstraintFactory(InfiniteHeadwayConstraint.NAME).to(InfiniteHeadwayConstraint.Factory.class);

		bind(SwissModeParameters.class).to(AstraModeParameters.class);

		bindCostModel("drt").to(DRTCostModel.class);

		bind(SwissCostParameters.class).to(DrtCostParameters.class);
		bind(SwissModeAvailability.class);
		bindModeAvailability(AstraModeAvailability.NAME).to(AstraModeAvailability.class);

	}

	@Provides
	@Singleton
	public AstraModeParameters provideAstraModeParameters(EqasimConfigGroup config)
			throws IOException, ConfigurationException {
		AstraModeParameters parameters = AstraModeParameters.buildFrom6Feb2020();

		if (config.getModeParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getModeParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("mode-parameter", commandLine, parameters);
		return parameters;
	}

	@Provides
	@Singleton
	public DrtCostParameters provideCostParameters(EqasimConfigGroup config) {
		DrtCostParameters parameters = DrtCostParameters.buildDefault();

		if (config.getCostParametersPath() != null) {
			ParameterDefinition.applyFile(new File(config.getCostParametersPath()), parameters);
		}

		ParameterDefinition.applyCommandLine("cost-parameter", commandLine, parameters);
		return parameters;
	}

	@Provides
	@Singleton
	public OVGKCalculator provideOVGKCalculator(TransitSchedule transitSchedule) {
		return new OVGKCalculator(transitSchedule);
	}

	@Provides
	public AstraModeAvailability provideAstraModeAvailability(SwissModeAvailability delegate) {
		return new AstraModeAvailability(delegate);
	}

	@Provides

	@Named("drt")
	public CostModel provideDrtCostModel(DrtCostParameters costParameters, AstraPersonPredictor predictor) {
		return new DRTCostModel(costParameters, predictor);
	}

}