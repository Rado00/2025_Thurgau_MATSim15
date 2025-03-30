package abmt2025.project.utils;

import java.io.File;

public class OutputPathConfigurator {
    public static void main(String[] args) {
        String dataFolder = getOutputPath();
        System.out.println("Data folder is set to: " + dataFolder);
    }

    public static String getOutputPath() {
        String osType = System.getProperty("os.name").toLowerCase();
        String userName = System.getProperty("user.name");

        String outputFolder = "/home/muaa/SAVED_OUTPUTS";

        if (osType.contains("win") && userName.equals("muaa")) {
            outputFolder = "C:/Users/" + userName + "/Documents/3_MIEI/2025_ABMT_Data/Zurich/0_Outputs/";
        } else if (osType.contains("nix") || osType.contains("nux") || osType.contains("aix") && userName.equals("cmuratori")) {
            outputFolder = "/cluster/scratch/cmuratori/data/output_euler";
        } else if (osType.contains("nix") || osType.contains("nux") || osType.contains("aix") && userName.equals("muaa")) {
            outputFolder = "/home/muaa/SAVED_OUTPUTS";
        } else if (osType.contains("mac")) {
            outputFolder = "/Users/Marco/Library/CloudStorage/OneDrive-Persönlich/ETHZ/Agent Based Modeling/data/";
        } else {
            throw new RuntimeException("Unsupported system configuration");
        }

        // if (osType.contains("win") && userName.equals("muaa")) {
        //     outputFolder = "C:/Users/" + userName + "/Documents/3_MIEI/2025_ABMT_Data/Zurich/0_Outputs/";
        // } else if (osType.contains("nix") || osType.contains("nux") || osType.contains("aix")) {
        //     if (userName.equals("cmuratori")) {
        //         outputFolder = "/cluster/scratch/cmuratori/data/output_euler";
        //     } else if (userName.equals("muaa")) {
        //         outputFolder = "/home/muaa/SAVED_OUTPUTS";
        //     }
        // } else if (osType.contains("mac")) {
        //     outputFolder = "/Users/Marco/Library/CloudStorage/OneDrive-Persönlich/ETHZ/Agent Based Modeling/data/";
        // } else {
        //     throw new RuntimeException("Unsupported system configuration");
        // }

        return outputFolder;
    }
}
