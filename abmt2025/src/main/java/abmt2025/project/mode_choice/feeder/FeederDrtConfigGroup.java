package abmt2025.project.mode_choice.feeder;

import org.matsim.core.config.ReflectiveConfigGroup;

/**
 * Configuration group for Feeder DRT settings.
 * Controls how DRT can be used as access/egress mode for PT.
 */
public class FeederDrtConfigGroup extends ReflectiveConfigGroup {
    public static final String GROUP_NAME = "feederDrt";

    private static final String MODE_NAME = "modeName";
    private static final String BASE_DRT_MODE = "baseDrtMode";
    private static final String BASE_PT_MODE = "basePtMode";
    private static final String MAX_ACCESS_EGRESS_DISTANCE_M = "maxAccessEgressDistance_m";
    private static final String USE_DRT_FOR_ACCESS = "useDrtForAccess";
    private static final String USE_DRT_FOR_EGRESS = "useDrtForEgress";
    private static final String DRT_SERVICE_AREA_SHAPE_FILE = "drtServiceAreaShapeFile";
    private static final String USE_TRAIN_STATION_ROUTING = "useTrainStationRouting";

    private String modeName = "feeder_drt";
    private String baseDrtMode = "drt";
    private String basePtMode = "pt";
    private double maxAccessEgressDistance_m = 10000.0; // 10 km default
    private boolean useDrtForAccess = true;
    private boolean useDrtForEgress = true;
    private String drtServiceAreaShapeFile = null; // Optional: path to DRT service area shape file
    private boolean useTrainStationRouting = true; // If true, also try routing via train stations

    public FeederDrtConfigGroup() {
        super(GROUP_NAME);
    }

    @StringGetter(MODE_NAME)
    public String getModeName() {
        return modeName;
    }

    @StringSetter(MODE_NAME)
    public void setModeName(String modeName) {
        this.modeName = modeName;
    }

    @StringGetter(BASE_DRT_MODE)
    public String getBaseDrtMode() {
        return baseDrtMode;
    }

    @StringSetter(BASE_DRT_MODE)
    public void setBaseDrtMode(String baseDrtMode) {
        this.baseDrtMode = baseDrtMode;
    }

    @StringGetter(BASE_PT_MODE)
    public String getBasePtMode() {
        return basePtMode;
    }

    @StringSetter(BASE_PT_MODE)
    public void setBasePtMode(String basePtMode) {
        this.basePtMode = basePtMode;
    }

    @StringGetter(MAX_ACCESS_EGRESS_DISTANCE_M)
    public double getMaxAccessEgressDistance_m() {
        return maxAccessEgressDistance_m;
    }

    @StringSetter(MAX_ACCESS_EGRESS_DISTANCE_M)
    public void setMaxAccessEgressDistance_m(double maxAccessEgressDistance_m) {
        this.maxAccessEgressDistance_m = maxAccessEgressDistance_m;
    }

    @StringGetter(USE_DRT_FOR_ACCESS)
    public boolean isUseDrtForAccess() {
        return useDrtForAccess;
    }

    @StringSetter(USE_DRT_FOR_ACCESS)
    public void setUseDrtForAccess(boolean useDrtForAccess) {
        this.useDrtForAccess = useDrtForAccess;
    }

    @StringGetter(USE_DRT_FOR_EGRESS)
    public boolean isUseDrtForEgress() {
        return useDrtForEgress;
    }

    @StringSetter(USE_DRT_FOR_EGRESS)
    public void setUseDrtForEgress(boolean useDrtForEgress) {
        this.useDrtForEgress = useDrtForEgress;
    }

    @StringGetter(DRT_SERVICE_AREA_SHAPE_FILE)
    public String getDrtServiceAreaShapeFile() {
        return drtServiceAreaShapeFile;
    }

    @StringSetter(DRT_SERVICE_AREA_SHAPE_FILE)
    public void setDrtServiceAreaShapeFile(String drtServiceAreaShapeFile) {
        this.drtServiceAreaShapeFile = drtServiceAreaShapeFile;
    }

    @StringGetter(USE_TRAIN_STATION_ROUTING)
    public boolean isUseTrainStationRouting() {
        return useTrainStationRouting;
    }

    @StringSetter(USE_TRAIN_STATION_ROUTING)
    public void setUseTrainStationRouting(boolean useTrainStationRouting) {
        this.useTrainStationRouting = useTrainStationRouting;
    }

    public static FeederDrtConfigGroup get(org.matsim.core.config.Config config) {
        return (FeederDrtConfigGroup) config.getModule(GROUP_NAME);
    }
}
