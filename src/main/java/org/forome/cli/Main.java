/*
 * Copyright (c) 2019. Partners HealthCare and other members of
 * Forome Association
 *
 *  Developed by Andrei Pestov and Michael Bouzinier, based on contributions by
 *  members of Division of Genetics, Brigham and Women's Hospital
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.forome.cli;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.forome.cli.util.Util;

import java.util.HashMap;
import java.util.Map;

/**
 * Starting arguments:
 * configuration.json
 */
public class Main {
    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("Anfisa CLI").build()
                        .defaultHelp(true)
                        .description("Manipulate Anfisa datasets via command line.");
        parser.addArgument("-c", "--config")
            .setDefault("anfisa-cli.json").required (true)
            .help("Path to config file for Anfisa Instance");
        parser.addArgument("-a", "--action")
            .required (true).choices ("activate", "apply")
            .help("Path to config file for Anfisa Instance");
        parser.addArgument("--ds")
            .required (true)
            .help("Dataset to create/modify/change status");
        parser.addArgument("--rule")
            .required (false)
            .help("Rule to apply");
        parser.addArgument("--parent")
            .required (false)
            .help("Parent dataset");

        Namespace ns = null;
        try {
            ns = parser.parseArgs (args);
        } catch (ArgumentParserException e) {
            parser.handleError (e);
            System.exit (1);
        }

        Map<String, String> attrs = new HashMap<> ();
        for (Map.Entry<String, Object> e: ns.getAttrs ().entrySet ()) {
            attrs.put (e.getKey (), String.valueOf (e.getValue ()));
        }
        Util util = new Util(attrs);

        boolean ret = false;
        String action = ns.getString ("action");
        if (action.equalsIgnoreCase ("activate"))
            ret = util.activateWorkspace ();
        else if (action.equalsIgnoreCase ("apply"))
             ret = util.createWorkspace();

        if (ret) {
            System.out.println ("Action performed: " + action);
        } else {
            System.out.println ("Action " + action + " has failed");
            System.out.println (ns);
        }
    }
}
