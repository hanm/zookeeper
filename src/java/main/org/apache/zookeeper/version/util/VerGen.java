/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.version.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VerGen {
    private static final String PACKAGE_NAME = "org.apache.zookeeper.version";
    private static final String TYPE_NAME = "Info";

    static void printUsage() {
        System.out.print("Usage:\tjava  -cp <classpath> org.apache.zookeeper."
                + "version.util.VerGen maj.min.micro[-qualifier] rev buildDate");
        System.exit(1);
    }

    public static void generateFile(File outputDir, Version version, int rev, String buildDate)
    {
        String path = PACKAGE_NAME.replaceAll("\\.", "/");
        File pkgdir = new File(outputDir, path);
        if (!pkgdir.exists()) {
            // create the pkg directory
            boolean ret = pkgdir.mkdirs();
            if (!ret) {
                System.out.println("Cannnot create directory: " + path);
                System.exit(1);
            }
        } else if (!pkgdir.isDirectory()) {
            // not a directory
            System.out.println(path + " is not a directory.");
            System.exit(1);
        }

        try (FileWriter w = new FileWriter(new File(pkgdir, TYPE_NAME + ".java"))) {
            w.write("// Do not edit!\n// File generated by org.apache.zookeeper"
                    + ".version.util.VerGen.\n");
            w.write("/**\n");
            w.write("* Licensed to the Apache Software Foundation (ASF) under one\n");
            w.write("* or more contributor license agreements.  See the NOTICE file\n");
            w.write("* distributed with this work for additional information\n");
            w.write("* regarding copyright ownership.  The ASF licenses this file\n");
            w.write("* to you under the Apache License, Version 2.0 (the\n");
            w.write("* \"License\"); you may not use this file except in compliance\n");
            w.write("* with the License.  You may obtain a copy of the License at\n");
            w.write("*\n");
            w.write("*     http://www.apache.org/licenses/LICENSE-2.0\n");
            w.write("*\n");
            w.write("* Unless required by applicable law or agreed to in writing, software\n");
            w.write("* distributed under the License is distributed on an \"AS IS\" BASIS,\n");
            w.write("* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n");
            w.write("* See the License for the specific language governing permissions and\n");
            w.write("* limitations under the License.\n");
            w.write("*/\n");
            w.write("\n");
            w.write("package " + PACKAGE_NAME + ";\n\n");
            w.write("public interface " + TYPE_NAME + " {\n");
            w.write("    public static final int MAJOR=" + version.maj + ";\n");
            w.write("    public static final int MINOR=" + version.min + ";\n");
            w.write("    public static final int MICRO=" + version.micro + ";\n");
            w.write("    public static final String QUALIFIER="
                    + (version.qualifier == null ? null :
                        "\"" + version.qualifier + "\"")
                    + ";\n");
            if (rev < 0) {
                System.out.println("Unknown REVISION number, using " + rev);
            }
            w.write("    public static final int REVISION=" + rev + ";\n");
            w.write("    public static final String BUILD_DATE=\"" + buildDate
                    + "\";\n");
            w.write("}\n");
        } catch (IOException e) {
            System.out.println("Unable to generate version.Info file: "
                    + e.getMessage());
            System.exit(1);
        }
    }

    public static class Version {
        public int maj;
        public int min;
        public int micro;
        public String qualifier;
    }

    public static Version parseVersionString(String input) {
        Version result = new Version();

        Pattern p = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)((\\.\\d+)*)(-(.+))?$");
        Matcher m = p.matcher(input);

        if (!m.matches()) {
            return null;
        }
        result.maj = Integer.parseInt(m.group(1));
        result.min = Integer.parseInt(m.group(2));
        result.micro = Integer.parseInt(m.group(3));
        if (m.groupCount() == 7) {
            result.qualifier = m.group(7);
        } else {
            result.qualifier = null;
        }
        return result;
    }

    /**
     * Emits a org.apache.zookeeper.version.Info interface file with version and
     * revision information constants set to the values passed in as command
     * line parameters. The file is created in the current directory. <br>
     * Usage: java org.apache.zookeeper.version.util.VerGen maj.min.micro[-qualifier]
     * rev buildDate
     *
     * @param args
     *            <ul>
     *            <li>maj - major version number
     *            <li>min - minor version number
     *            <li>micro - minor minor version number
     *            <li>qualifier - optional qualifier (dash followed by qualifier text)
     *            <li>rev - current SVN revision number
     *            <li>buildDate - date the build
     *            </ul>
     */
    public static void main(String[] args) {
        if (args.length != 3)
            printUsage();
        try {
            Version version = parseVersionString(args[0]);
            if (version == null) {
                System.err.println(
                        "Invalid version number format, must be \"x.y.z(-.*)?\"");
                System.exit(1);
            }
            int rev;
            try {
                rev = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                rev = -1;
            }
            generateFile(new File("."), version, rev, args[2]);
        } catch (NumberFormatException e) {
            System.err.println(
                    "All version-related parameters must be valid integers!");
            throw e;
        }
    }

}
