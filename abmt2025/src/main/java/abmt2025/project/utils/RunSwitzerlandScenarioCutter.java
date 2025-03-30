package abmt2023.project.utils;

import org.eqasim.core.scenario.cutter.RunScenarioCutter;
import org.matsim.core.config.CommandLine;

import java.io.IOException;

/**
 * author kaghog created on 04.07.2022
 * project matsim-tools
 *
 * The script expects a number of arguments:
 * --config-path /path/to/zurich_config.xml
 * --output-path /path/to/output/zurich_100pct
 * --extent-path /path/to/zurich20km_shapefile.shp
 * --config:plans.inputPlansFile /path/to/plans.xml.gz
 * --prefix zurich_
 * --threads 24
 * https://github.com/eqasim-org/eqasim-java/blob/develop/docs/cutting.md
 *
 */
public class RunSwitzerlandScenarioCutter {

    public static void main(String[] args) throws InterruptedException, CommandLine.ConfigurationException, IOException {
        RunScenarioCutter.main(args);


    }
}
