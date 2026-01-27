package abmt2025.project.mode_choice.routing;

import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;

import abmt2025.project.mode_choice.predictors.AstraPtPredictor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Guice module that provides the DRT service area filter for intermodal routing.
 *
 * This module:
 * 1. Loads the DRT service area shape file from the DRT config
 * 2. Creates a DrtServiceAreaFilter singleton
 * 3. Logs statistics at simulation shutdown
 */
public class DrtIntermodalFilterModule extends AbstractModule {

    private static final Logger log = LogManager.getLogger(DrtIntermodalFilterModule.class);

    @Override
    public void install() {
        // Add shutdown listener to log statistics
        addControlerListenerBinding().to(DrtFilterShutdownListener.class);

        log.info("DrtIntermodalFilterModule installed");
    }

    @Provides
    @Singleton
    @Named("drtServiceAreaFilter")
    public DrtServiceAreaFilter provideDrtServiceAreaFilter(Config config) {
        // Get the DRT service area shape file path from config
        MultiModeDrtConfigGroup multiModeDrtConfig = MultiModeDrtConfigGroup.get(config);

        String shapeFilePath = null;

        // Find the first DRT mode's service area shape file
        // Use generic parameter access for MATSim 15 compatibility
        for (DrtConfigGroup drtConfig : multiModeDrtConfig.getModalElements()) {
            // Try to get the parameter directly (works across MATSim versions)
            String paramValue = drtConfig.getParams().get("drtServiceAreaShapeFile");
            if (paramValue != null && !paramValue.isEmpty()) {
                shapeFilePath = paramValue;
                log.info("Found DRT service area shape file for mode '{}': {}",
                        drtConfig.getMode(), shapeFilePath);
                break;
            }
        }

        if (shapeFilePath == null) {
            log.warn("No DRT service area shape file found in config. " +
                    "DRT intermodal filtering will be disabled.");
            return null;
        }

        return new DrtServiceAreaFilter(shapeFilePath);
    }

    /**
     * Listener to log filter statistics at shutdown.
     */
    public static class DrtFilterShutdownListener implements ShutdownListener {

        private DrtServiceAreaFilter filter;

        @com.google.inject.Inject
        public DrtFilterShutdownListener() {
            // Default constructor - filter will be set via setter if available
        }

        @com.google.inject.Inject(optional = true)
        public void setFilter(@Named("drtServiceAreaFilter") DrtServiceAreaFilter filter) {
            this.filter = filter;
        }

        @Override
        public void notifyShutdown(ShutdownEvent event) {
            // Log service area filter statistics
            if (filter != null) {
                filter.logStatistics();
            }

            // Log FilteredDrtIntermodalRoutingModule statistics
            FilteredDrtIntermodalRoutingModule.logStatistics();

            // Log AstraPtPredictor DRT statistics
            AstraPtPredictor.logStatistics();
        }
    }
}
