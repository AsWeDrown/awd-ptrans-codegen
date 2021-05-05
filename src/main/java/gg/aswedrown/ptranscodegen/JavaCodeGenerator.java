package gg.aswedrown.ptranscodegen;

import java.io.BufferedReader;
import java.util.List;

public class JavaCodeGenerator implements CodeGenerator {

    private static final String WRAP_MTD_DECL
            = "private static byte[] internalGeneratedWrap(Message packet, int sequence, int ack, int ackBitfield) {";

    private static final String UNWRAP_MTD_DECL
            = "private static UnwrappedPacketData internalGeneratedUnwrap(byte[] data) throws InvalidProtocolBufferException {";

    @SuppressWarnings ("DuplicatedCode")
    @Override
    public SourceSets insertInExistingSources(BufferedReader srcReader, List<String> allPackets) throws Exception {
        StringBuilder originalSrc = new StringBuilder();
        StringBuilder modifiedSrc = new StringBuilder();

        boolean skipBody = false;
        int brackets = -1;
        String line;

        boolean generatedWrap   = false,
                generatedUnwrap = false;

        while ((line = srcReader.readLine()) != null) {
            originalSrc.append(line);

            if (!skipBody)
                modifiedSrc.append(line).append('\n');

            if (line.trim().startsWith(WRAP_MTD_DECL) || line.trim().startsWith(UNWRAP_MTD_DECL)) {
                skipBody = true;
                brackets = 1;
            } else if (skipBody) {
                if (line.contains("{"))
                    brackets++;

                if (line.contains("}") && --brackets == 0) {
                    skipBody = false;

                    if (!generatedWrap) {
                        generatedWrap = true;
                        appendGeneratedSourcesWrap(modifiedSrc, allPackets);
                        modifiedSrc.append("    }\n");
                    } else if (!generatedUnwrap) {
                        generatedUnwrap = true;
                        appendGeneratedSourcesUnrap(modifiedSrc, allPackets);
                        modifiedSrc.append("    }\n");
                    }
                }
            }
        }

        if (generatedWrap)
            System.out.println("Successfully generated code in method wrap (Java)");
        else {
            System.err.println("WARNING: did not generate any code " +
                    "in method wrap (Java) - missing empty declaration");

            return null;
        }

        if (generatedUnwrap)
            System.out.println("Successfully generated code in method unwrap (Java)");
        else {
            System.err.println("WARNING: did not generate any code " +
                    "in method unwrap (Java) - missing empty declaration");

            return null;
        }

        return new SourceSets(originalSrc.toString(), modifiedSrc.toString());
    }

    @Override
    public void appendGeneratedSourcesWrap(StringBuilder modifiedSrc, List<String> allPackets) {
        modifiedSrc.append("        String packetClassNameUpper = packet.getClass().getSimpleName().toUpperCase();\n" +
                "        PacketWrapper.PacketCase packetType = getPacketCase(packet.getClass());\n" +
                "\n" +
                "        switch (packetType) {\n");

        for (String packetType : allPackets)
            modifiedSrc.append("            case ").append(packetType.toUpperCase()).append(":\n")
                    .append("                return PacketWrapper.newBuilder()\n" +
                            "                        .setSequence(sequence)\n" +
                            "                        .setAck(ack)\n" +
                            "                        .setAckBitfield(ackBitfield)\n" +
                            "                        .set").append(Convert.snakeToCamel(packetType))
                    .append("((").append(Convert.snakeToCamel(packetType))
                    .append(") packet)\n" +
                            "                        .build()\n" +
                            "                        .toByteArray();\n\n");

        modifiedSrc.append("            default:\n" +
                "                // Код \"case ...\" для пакетов этого типа отсутствует выше.\n" +
                "                // Нужно добавить! (исп. awd-ptrans-codegen)\n" +
                "                throw new RuntimeException(\"no implemented transformer for packet type \"\n" +
                "                        + packetClassNameUpper + \" (\" + packet.getClass().getName() + \")\");\n" +
                "        }\n");
    }

    @Override
    public void appendGeneratedSourcesUnrap(StringBuilder modifiedSrc, List<String> allPackets) {
        modifiedSrc.append("        PacketWrapper wrapper = PacketWrapper.parseFrom(data);\n" +
                "\n" +
                "        int sequence    = wrapper.getSequence();\n" +
                "        int ack         = wrapper.getAck();\n" +
                "        int ackBitfield = wrapper.getAckBitfield();\n" +
                "\n" +
                "        PacketWrapper.PacketCase packetType = wrapper.getPacketCase();\n" +
                "\n" +
                "        switch (packetType) {\n");

        for (String packetType : allPackets)
            modifiedSrc.append("            case ").append(packetType.toUpperCase()).append(":\n" +
                    "                return new UnwrappedPacketData(\n" +
                    "                        sequence, ack, ackBitfield, packetType, wrapper.get")
                    .append(Convert.snakeToCamel(packetType)).append("());\n\n");

        modifiedSrc.append("            default:\n" +
                "                // Неизвестный пакет - он будет проигнорирован (не передан никакому PacketListener'у).\n" +
                "                return null;\n" +
                "        }\n");
    }

}
