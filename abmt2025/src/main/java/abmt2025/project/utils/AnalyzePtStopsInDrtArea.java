package abmt2025.project.utils;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.FeatureIterator;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Utility class per analizzare quali fermate PT sono dentro o vicino alla zona DRT.
 *
 * Uso:
 *   java -cp target/abmt2025.jar abmt2025.project.utils.AnalyzePtStopsInDrtArea \
 *       /path/to/config.xml \
 *       /path/to/drt_shapefile.shp \
 *       5000 \
 *       /path/to/output.csv
 */
public class AnalyzePtStopsInDrtArea {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Uso: AnalyzePtStopsInDrtArea <config.xml> <drt_shapefile.shp> [radius_m] [output.csv]");
            System.out.println();
            System.out.println("Argomenti:");
            System.out.println("  config.xml      - MATSim config file con transit schedule");
            System.out.println("  drt_shapefile   - Shapefile della zona DRT");
            System.out.println("  radius_m        - Raggio di ricerca intermodale (default: 5000)");
            System.out.println("  output.csv      - File CSV di output (opzionale)");
            return;
        }

        String configPath = args[0];
        String shapefilePath = args[1];
        double radiusM = args.length > 2 ? Double.parseDouble(args[2]) : 5000.0;
        String outputPath = args.length > 3 ? args[3] : null;

        // Carica config e scenario
        System.out.println("Caricamento config: " + configPath);
        Config config = ConfigUtils.loadConfig(configPath);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        TransitSchedule schedule = scenario.getTransitSchedule();

        System.out.println("Fermate PT nel transit schedule: " + schedule.getFacilities().size());

        // Carica shapefile
        System.out.println("Caricamento shapefile DRT: " + shapefilePath);
        Geometry drtArea = loadShapefile(shapefilePath);
        if (drtArea == null) {
            System.err.println("Errore nel caricamento dello shapefile");
            return;
        }

        Geometry drtAreaBuffered = drtArea.buffer(radiusM);

        // Analizza le fermate
        List<StopAnalysis> results = new ArrayList<>();
        int insideCount = 0;
        int withinRadiusCount = 0;

        for (TransitStopFacility stop : schedule.getFacilities().values()) {
            Coord coord = stop.getCoord();
            Point point = MGC.coord2Point(coord);

            boolean insideDrt = drtArea.contains(point);
            boolean withinRadius = drtAreaBuffered.contains(point);
            double distance = insideDrt ? 0 : point.distance(drtArea);

            String stopCategory = (String) stop.getAttributes().getAttribute("stopCategory");

            StopAnalysis analysis = new StopAnalysis(
                stop.getId().toString(),
                stop.getName(),
                coord.getX(),
                coord.getY(),
                stopCategory,
                insideDrt,
                withinRadius,
                distance
            );
            results.add(analysis);

            if (insideDrt) insideCount++;
            if (withinRadius) withinRadiusCount++;
        }

        // Ordina per distanza
        results.sort(Comparator.comparingDouble(a -> a.distanceToArea));

        // Stampa riepilogo
        System.out.println();
        System.out.println("================================================================");
        System.out.println("RIEPILOGO FERMATE PT vs ZONA DRT");
        System.out.println("================================================================");
        System.out.println("Totale fermate PT: " + results.size());
        System.out.println("Fermate DENTRO la zona DRT: " + insideCount);
        System.out.println("Fermate entro " + (int)radiusM + "m dalla zona DRT: " + withinRadiusCount);
        System.out.println("Fermate oltre " + (int)radiusM + "m dalla zona DRT: " + (results.size() - withinRadiusCount));
        System.out.println("================================================================");

        if (insideCount == 0) {
            System.out.println();
            System.out.println("⚠️  ATTENZIONE: Nessuna fermata PT dentro la zona DRT!");
            System.out.println("   I viaggi multi-modali usano fermate FUORI dalla zona.");
        }

        // Mostra le fermate più vicine
        System.out.println();
        System.out.println("Fermate PT più vicine alla/dentro la zona DRT:");
        int shown = 0;
        for (StopAnalysis stop : results) {
            if (shown >= 15) break;
            String status = stop.insideArea ? "DENTRO" : String.format("%.0fm", stop.distanceToArea);
            String cat = stop.category != null ? " (cat:" + stop.category + ")" : "";
            System.out.println("  - " + stop.name + " [" + stop.id + "]: " + status + cat);
            shown++;
        }

        // Salva CSV se richiesto
        if (outputPath != null) {
            saveToCSV(results, outputPath);
            System.out.println();
            System.out.println("Risultati salvati in: " + outputPath);
        }
    }

    private static Geometry loadShapefile(String shapefilePath) {
        Geometry mergedGeometry = null;

        try {
            SimpleFeatureSource featureSource = ShapeFileReader.readDataFile(shapefilePath);
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
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        return mergedGeometry;
    }

    private static void saveToCSV(List<StopAnalysis> results, String outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("stop_id,name,x,y,category,inside_drt_area,within_intermodal_radius,distance_to_drt_m");
            for (StopAnalysis stop : results) {
                writer.printf("%s,%s,%.1f,%.1f,%s,%b,%b,%.1f%n",
                    stop.id,
                    stop.name.replace(",", ";"),
                    stop.x, stop.y,
                    stop.category != null ? stop.category : "",
                    stop.insideArea,
                    stop.withinRadius,
                    stop.distanceToArea);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class StopAnalysis {
        String id;
        String name;
        double x, y;
        String category;
        boolean insideArea;
        boolean withinRadius;
        double distanceToArea;

        StopAnalysis(String id, String name, double x, double y, String category,
                     boolean insideArea, boolean withinRadius, double distanceToArea) {
            this.id = id;
            this.name = name;
            this.x = x;
            this.y = y;
            this.category = category;
            this.insideArea = insideArea;
            this.withinRadius = withinRadius;
            this.distanceToArea = distanceToArea;
        }
    }
}
