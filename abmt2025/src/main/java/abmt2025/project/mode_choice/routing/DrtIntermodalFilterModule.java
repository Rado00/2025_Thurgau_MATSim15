package abmt2025.project.mode_choice.routing;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.drt.run.DrtConfigGroup;
import org.matsim.contrib.drt.run.MultiModeDrtConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.router.RoutingModule;

import abmt2025.project.mode_choice.predictors.AstraPtPredictor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Guice module that provides the DRT service area filter for intermodal routing.
 *
 * This module:
 * 1. Loads the DRT service area shape file from the DRT config
 * 2. Creates a DrtServiceAreaFilter singleton
 * 3. Binds a "drt_access" routing module for intermodal PT access/egress
 * 4. Logs statistics at simulation shutdown
 *
 * IMPORTANT: To activate filtering, configure SwissRailRaptor to use mode "drt_access"
 * instead of "drt" for intermodal access/egress.
 */
public class DrtIntermodalFilterModule extends AbstractModule {

    private static final Logger log = LogManager.getLogger(DrtIntermodalFilterModule.class);

    /**
     * The mode name used for filtered DRT intermodal access/egress.
     * Configure SwissRailRaptor intermodalAccessEgress to use this mode.
     */
    public static final String DRT_ACCESS_MODE = "drt_access";

    @Override
    public void install() {
        // Add shutdown listener to log statistics
        addControlerListenerBinding().to(DrtFilterShutdownListener.class);

        // Bind the filtered DRT routing module to a new mode "drt_access"
        // SwissRailRaptor should be configured to use this mode for intermodal access/egress
        addRoutingModuleBinding(DRT_ACCESS_MODE).toProvider(FilteredDrtRoutingModuleProvider.class);

        log.info("DrtIntermodalFilterModule installed");
        log.info("Bound '{}' routing mode with service area filtering", DRT_ACCESS_MODE);
        log.info("NOTE: Configure SwissRailRaptor to use mode='{}' for intermodal access/egress", DRT_ACCESS_MODE);
    }

    /**
     * Provider for the filtered DRT routing module.
     * Uses runtime lookup via Injector to get the original DRT router and wrap it with filtering.
     */
    public static class FilteredDrtRoutingModuleProvider implements Provider<RoutingModule> {

        private final Injector injector;
        private DrtServiceAreaFilter filter;

        @Inject
        public FilteredDrtRoutingModuleProvider(Injector injector) {
            this.injector = injector;
        }

        @Inject(optional = true)
        public void setFilter(@Named("drtServiceAreaFilter") DrtServiceAreaFilter filter) {
            this.filter = filter;
        }

        @Override
        public RoutingModule get() {
            // Get the original DRT routing module at runtime using the Injector
            RoutingModule originalDrtRouter = null;
            try {
                // Look up the DRT routing module by its named binding
                originalDrtRouter = injector.getInstance(
                        Key.get(RoutingModule.class, Names.named(TransportMode.drt)));
                log.info("FilteredDrtRoutingModuleProvider: Found DRT routing module: {}",
                        originalDrtRouter.getClass().getSimpleName());
            } catch (Exception e) {
                log.warn("Could not find DRT routing module for mode '{}': {}",
                        TransportMode.drt, e.getMessage());
            }

            if (originalDrtRouter == null) {
                log.warn("No DRT routing module found - drt_access will return null routes");
                return new FilteredDrtIntermodalRoutingModule(null, filter);
            }

            if (filter != null && filter.isInitialized()) {
                log.info("FilteredDrtRoutingModuleProvider: wrapping {} with active service area filter",
                        originalDrtRouter.getClass().getSimpleName());
            } else {
                log.info("FilteredDrtRoutingModuleProvider: wrapping {} (filter not available, will pass-through)",
                        originalDrtRouter.getClass().getSimpleName());
            }

            return new FilteredDrtIntermodalRoutingModule(originalDrtRouter, filter);
        }
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
