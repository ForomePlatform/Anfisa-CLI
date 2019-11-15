package org.forome;

import org.forome.util.Util;

/**
 * Starting arguments:
 * configuration.json
 */
public class Main {
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage <configuration_json_file_full_path> <xlDataset_name> <tree_name> <workspace_name>");
            return;
        }
        Util util = new Util(args);
        if (util.createWorkspace()) {
            System.out.println("Workspace was created!");
        } else {
            System.out.println("Workspace has not created!");
        }
    }
}
