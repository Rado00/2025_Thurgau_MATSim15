package abmt2025.project.utils.classSamples;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eqasim.core.components.config.EqasimConfigGroup;
import org.eqasim.core.simulation.mode_choice.EqasimModeChoiceModule;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contribs.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import org.matsim.contribs.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import org.matsim.core.config.CommandLine;
import org.matsim.core.config.CommandLine.ConfigurationException;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy.OverwriteFileSetting;
import org.matsim.core.scenario.ScenarioUtils;

import abmt2025.project.utils.OutputPathConfigurator;
import abmt2025.project.utils.classSamples.mode_choice.AbmtModeChoiceModule;

public class RunCustomEqasim {
	
	static public void main(String[] args) throws ConfigurationException, MalformedURLException, IOException {
		// Some paramters added from AdPT

		
		CommandLine cmd = new CommandLine.Builder(args) //
				.requireOptions("config-path") // --config-path "path-to-your-config-file/config.xml" is required
				.allowPrefixes( "mode-parameter", "cost-parameter") //
				.build();
		
		//load the config file with the necessary modules (DMC module and eqasim...)
		//Hint: Search for the EqasimConfigurator class and see what class we should load
		 //First load the Siouxfalls scenario
		
        Config config = ConfigUtils.loadConfig(cmd.getOptionStrict("config-path"), new EqasimConfigGroup(), new DiscreteModeChoiceConfigGroup());
        

        config.controler().setLastIteration(2);
        config.controler().setOverwriteFileSetting(OverwriteFileSetting.deleteDirectoryIfExists);        
        
        		// Set output directory to a unique directory
        String baseDirPath = OutputPathConfigurator.getOutputPath();
        String baseDir = "Sim_SetGit";
        Path path = Paths.get(baseDirPath, baseDir);
        int index = 0;

        while (Files.exists(path)) {
            index++;
            path = Paths.get(baseDirPath, baseDir + index);
        }
        config.controler().setOutputDirectory(path.toString());

		//load scenario
        Scenario scenario = ScenarioUtils.loadScenario(config);
        
      //Now that we have chosen eqasimUtility estimator for setting up the dmc we need to identify for each mode what estimators we want to use
        EqasimConfigGroup eqasimConfig = (EqasimConfigGroup) config.getModules().get(EqasimConfigGroup.GROUP_NAME);

        //these are the default utility estimators already prepared for the different modes in the eqasim framework. this can also be specified in the config file
        eqasimConfig.setEstimator("walk", "WalkUtilityEstimator");
        eqasimConfig.setEstimator("bike", "BikeUtilityEstimator");
        eqasimConfig.setEstimator("pt", "PtUtilityEstimator");
        
      //now we change the estimator for car to the one we defined //we will use the name we will define in the mode choice module
        eqasimConfig.setEstimator("car", "AbmtCarUtilityEstimator");
        
      //we also need to specify our own cost model here or in the config
        eqasimConfig.setCostModel("car", "AbmtCarCostModel");
        eqasimConfig.setCostModel("pt", "AbmtPtCostModel");
        
      //to define the mode and cost parameters path we can specify here or directly in the config file. Ensure this file exist
        //eqasimConfig.setModeParametersPath("scenarios/siouxfalls-2014/mode_params.yml");
       // eqasimConfig.setCostParametersPath("scenarios/siouxfalls-2014/cost_params.yml");
        
      //Here is how to add the AbmtModeAvailability to the dcm module directly, one can make changes to any of the parameterset this way
        //First we get the dmc config group
        //Comment it out because we already defined within the config file
        DiscreteModeChoiceConfigGroup dmcConfig = DiscreteModeChoiceConfigGroup.getOrCreate(config);
        dmcConfig.setModeAvailability("AbmtModeAvailability");
        

		//create controler
		
        Controler controler = new Controler(scenario);

        
		//add modules to controler
        controler.addOverridingModule(new EqasimModeChoiceModule());
        controler.addOverridingModule(new DiscreteModeChoiceModule());
        
        
      //Add an injection for mode parameters for the code to work
		/*
		 * controler.addOverridingModule(new AbstractModule() {
		 * 
		 * @Override public void install() {
		 * bind(ModeParameters.class).asEagerSingleton(); } });
		 */
      //for clean code and organization, we create a module for all our mode choice class injections and add overriding module as below
        controler.addOverridingModule(new AbmtModeChoiceModule());

        controler.run();

		
		
	}

}
