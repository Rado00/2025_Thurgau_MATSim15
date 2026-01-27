package abmt2025.project.mode_choice.routing;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Utility class to check if coordinates are within the DRT service area.
 * Loads a shape file and provides methods to test if a point is inside.
 */
public class DrtServiceAreaFilter {

    private static final Logger log = LogManager.getLogger(DrtServiceAreaFilter.class);

    private final Geometry serviceArea;
    private final String shapeFilePath;

    // Statistics for logging
    private long checksPerformed = 0;
    private long checksInside = 0;
    private long checksOutside = 0;

    public DrtServiceAreaFilter(String shapeFilePath) {
        this.shapeFilePath = shapeFilePath;
        this.serviceArea = loadServiceArea(shapeFilePath);

        if (serviceArea != null) {
            log.info("DRT service area filter initialized from: {}", shapeFilePath);
            log.info("Service area bounds: {}", serviceArea.getEnvelopeInternal());
        } else {
            log.warn("DRT service area filter could not load shape file: {}", shapeFilePath);
        }
    }

    private Geometry loadServiceArea(String shapeFilePath) {
        Geometry mergedGeometry = null;

        try {
            SimpleFeatureSource featureSource = ShapeFileReader.readDataFile(shapeFilePath);
            SimpleFeatureCollection featureCollection = featureSource.getFeatures();

            try (FeatureIterator<SimpleFeature> features = featureCollection.features()) {
                while (features.hasNext()) {
                    SimpleFeature feature = features.next();
                    Geometry geometry = (Geometry) feature.getDefaultGeometry();
                    if (geometry != null) {
                        if (mergedGeometry == null) {
                            mergedGeometry = geometry;
                        } else {
                            mergedGeometry = mergedGeometry.union(geometry);
                        }
                    }
                }
            }

            log.info("Loaded {} features from DRT service area shape file",
                    featureCollection.size());

        } catch (Exception e) {
            log.error("Failed to load DRT service area shape file: {}", shapeFilePath, e);
            return null;
        }

        return mergedGeometry;
    }

    /**
     * Check if a coordinate is inside the DRT service area.
     *
     * @param coord The coordinate to check
     * @return true if inside the service area, false otherwise
     */
    public boolean isInsideServiceArea(Coord coord) {
        if (serviceArea == null || coord == null) {
            return false;
        }

        checksPerformed++;

        Point point = MGC.coord2Point(coord);
        boolean inside = serviceArea.contains(point);

        if (inside) {
            checksInside++;
        } else {
            checksOutside++;
        }

        return inside;
    }

    /**
     * Check if either of two coordinates is inside the DRT service area.
     * This is useful for intermodal trips where DRT can be used if
     * either origin OR destination is in the service area.
     *
     * @param origin The origin coordinate
     * @param destination The destination coordinate
     * @return true if at least one coordinate is inside the service area
     */
    public boolean isEitherInsideServiceArea(Coord origin, Coord destination) {
        return isInsideServiceArea(origin) || isInsideServiceArea(destination);
    }

    /**
     * Log statistics about service area checks.
     */
    public void logStatistics() {
        log.info("DRT Service Area Filter Statistics:");
        log.info("  Total checks: {}", checksPerformed);
        log.info("  Inside service area: {} ({}%)", checksInside,
                checksPerformed > 0 ? String.format("%.1f", 100.0 * checksInside / checksPerformed) : "0.0");
        log.info("  Outside service area: {} ({}%)", checksOutside,
                checksPerformed > 0 ? String.format("%.1f", 100.0 * checksOutside / checksPerformed) : "0.0");
    }

    /**
     * Reset statistics counters.
     */
    public void resetStatistics() {
        checksPerformed = 0;
        checksInside = 0;
        checksOutside = 0;
    }

    public boolean isInitialized() {
        return serviceArea != null;
    }

    public String getShapeFilePath() {
        return shapeFilePath;
    }
}
