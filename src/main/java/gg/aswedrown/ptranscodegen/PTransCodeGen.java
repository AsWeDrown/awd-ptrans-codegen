package gg.aswedrown.ptranscodegen;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public class PTransCodeGen {

    private static final String GREETING
            = "----------------------------------------- awd-ptrans-codegen -----------------------------------------";

    private static final String HORIZONTAL_RULE
            = "------------------------------------------------------------------------------------------------------";

    private static final String PROTO_ARG = "--proto=";
    private static final String JAVA_SRC_ARG = "--java_src=";
    private static final String CPP_SRC_ARG = "--cpp_src=";

    private static final String ALL_PACKETS_LIST_SPEC_START_IDENTIFIER = "oneof packet {";
    private static final String PACKET_WRAPPER_CLASS_NAME = "PacketWrapper";

    public static void main(String[] args) {
        System.out.println(GREETING);

        if (args.length != 3) {
            wrongUsage();
            return;
        }

        String protoPath = null, javaSrcPath = null, cppSrcPath = null;

        for (String arg : args) {
            if (arg.toLowerCase().startsWith(PROTO_ARG))
                protoPath = arg.substring(PROTO_ARG.length());
            else if (arg.toLowerCase().startsWith(JAVA_SRC_ARG))
                javaSrcPath = arg.substring(JAVA_SRC_ARG.length());
            else if (arg.toLowerCase().startsWith(CPP_SRC_ARG))
                cppSrcPath = arg.substring(CPP_SRC_ARG.length());
        }

        if (protoPath == null || javaSrcPath == null || cppSrcPath == null) {
            wrongUsage();
            return;
        }

        File packetsProto = new File(protoPath);

        if (!packetsProto.isFile()) {
            System.err.println("The specified proto file does not exist or is a directory:");
            System.err.println(packetsProto.getAbsolutePath());
            System.exit(1);

            return;
        }

        System.out.println("Analysing packets.proto");

        List<String> detectedPacketClasses = new ArrayList<>();
        List<String> allPackets = new ArrayList<>();

        try (BufferedReader protoReader = new BufferedReader(new InputStreamReader(
                                          new FileInputStream(packetsProto), StandardCharsets.UTF_8))) {
            boolean allPacketsListStarted = false;
            String line;

            while ((line = protoReader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("message ")) {
                    String packetClass = line.split(" ")[1].replace("{", "");

                    if (!packetClass.equals(PACKET_WRAPPER_CLASS_NAME))
                        detectedPacketClasses.add(packetClass);
                } else if (line.startsWith(ALL_PACKETS_LIST_SPEC_START_IDENTIFIER))
                    allPacketsListStarted = true;
                else if (allPacketsListStarted && !line.isEmpty()) {
                    if (line.contains("/*") || line.contains("*/")) {
                        System.err.println("Comments of /* this type */ are " +
                                "forbidden inside 'oneof packet {...}' packet types specification");
                        System.exit(1);

                        return;
                    } else if (!line.startsWith("//") && line.contains("=") && line.contains(";")) {
                        String packetType = line.split(" ")[1];
                        System.out.println("Detected packet specification: " + packetType
                                + " (message/class " + Convert.snakeToCamel(packetType) + ")");

                        if (!detectedPacketClasses.contains(Convert.snakeToCamel(packetType))) {
                            System.err.println("Packet " + packetType + " is listed inside 'oneof packet {...}' " +
                                    "packet types specification, but its class ('message " + packetType + " {...}' " +
                                    "declaration) is missing above in the proto file");
                            System.exit(1);

                            return;
                        }

                        allPackets.add(packetType);
                    }
                }
            }
        } catch (IOException ex) {
            System.err.println("Failed to read the specified packets.proto specification file:");
            ex.printStackTrace();
            System.exit(1);

            return;
        }

        for (String detectedPacketClass : detectedPacketClasses) {
            boolean listedInOneOf = allPackets.stream().anyMatch(packetType ->
                    detectedPacketClass.equals(Convert.snakeToCamel(packetType)));

            if (!listedInOneOf) {
                System.err.println("Packet " + detectedPacketClass + " class specificatin " +
                        "('message " + detectedPacketClass + " {...}' declaration) was detected " +
                        "above in the proto file, but the packet itself is not listed inside 'oneof " +
                        "packet {...}' packet types specification");
                System.exit(1);

                return;
            }
        }

        generate(new JavaCodeGenerator(), javaSrcPath, allPackets);
        generate(new CppCodeGenerator(), cppSrcPath, allPackets);

        System.out.println(HORIZONTAL_RULE);
        System.out.println("Complete");
        System.out.println(HORIZONTAL_RULE);
    }

    private static void wrongUsage() {
        System.err.println("Required arguments:");
        System.err.println("    --proto=PATH           Path to the 'packets.proto' packet specification file.");
        System.err.println("    --java_src=PATH        Path to the original Java source code file.");
        System.err.println("    --cpp_src=PATH         Path to the original C++ source code file.");

        System.exit(1);
    }

    private static void generate(CodeGenerator codeGen, String srcFilePath, List<String> allPackets) {
        System.out.println(HORIZONTAL_RULE);
        File sourceFile = new File(srcFilePath);

        if (!sourceFile.isFile()) {
            System.err.println("The specified source code file does not exist or is a directory:");
            System.err.println(sourceFile.getAbsolutePath());
            System.exit(1);

            return;
        }

        System.out.println("Generating code for " + allPackets.size() + " packets in:");
        System.out.println(sourceFile.getAbsolutePath());
        SourceSets sourceSets;

        try (BufferedReader srcReader = new BufferedReader(new InputStreamReader(
                new FileInputStream(sourceFile), StandardCharsets.UTF_8))) {
            sourceSets = codeGen.insertInExistingSources(srcReader, allPackets);
        } catch (Exception ex) {
            System.err.println("Failed to read the specified source file or to generate the code:");
            ex.printStackTrace();
            System.exit(1);

            return;
        }

        if (sourceSets == null) {
            System.err.println("Aborting due to a critical error in file (no SourceSets produced):");
            System.err.println(sourceFile.getAbsolutePath());
            System.exit(1);

            return;
        }

        //noinspection IfStatementWithIdenticalBranches
        if (sourceSets.areProgrammaticallyEqual()) {
            System.out.println("No code generation needed for file (already packets.proto-compatible):");
            System.out.println(sourceFile.getAbsolutePath());
        } else {
            File backupSrc = new File(sourceFile.getAbsolutePath()
                    .replace(".java", "_BACKUP.java")
                    .replace(".cpp", "_BACKUP.cpp")
            );

            System.out.println("Saving source file backup as '" + backupSrc.getName() + "'");

            try {
                Files.move(sourceFile.toPath(), backupSrc.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                System.err.println("Failed to backup (move/rename) source file:");
                ex.printStackTrace();
                System.exit(1);

                return;
            }

            System.out.println("Inserting generated code in the original source file");

            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(sourceFile), StandardCharsets.UTF_8))) {
                writer.write(sourceSets.getModifiedSrc());
            } catch (IOException ex) {
                System.err.println("Failed to create replacement for the original source file:");
                ex.printStackTrace();
                System.exit(1);
            }

            System.out.println("Finished code generation in file:");
            System.out.println(sourceFile.getAbsolutePath());
        }
    }

}
