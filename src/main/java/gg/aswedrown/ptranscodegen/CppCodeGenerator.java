package gg.aswedrown.ptranscodegen;

import java.io.BufferedReader;
import java.util.List;

public class CppCodeGenerator implements CodeGenerator {

    private static final String WRAP_MTD_DECL
            = "std::shared_ptr<WrappedPacketData> internalGeneratedWrap(google::protobuf::Message* packet, uint32_t sequence, uint32_t ack, uint32_t ackBitfield) {";

    private static final String UNWRAP_MTD_DECL
            = "std::shared_ptr<UnwrappedPacketData> internalGeneratedUnwrap(char* data, size_t dataLen) {";

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
            System.out.println("Successfully generated code in method wrap (C++)");
        else {
            System.err.println("WARNING: did not generate any code " +
                    "in method wrap (C++) - missing empty declaration");

            return null;
        }

        if (generatedUnwrap)
            System.out.println("Successfully generated code in method unwrap (C++)");
        else {
            System.err.println("WARNING: did not generate any code " +
                    "in method unwrap (C++) - missing empty declaration");

            return null;
        }

        return new SourceSets(originalSrc.toString(), modifiedSrc.toString());
    }

    @Override
    public void appendGeneratedSourcesWrap(StringBuilder modifiedSrc, List<String> allPackets) {
        modifiedSrc.append("        PacketWrapper wrapper;\n" +
                "\n" +
                "        wrapper.set_sequence(sequence);\n" +
                "        wrapper.set_ack(ack);\n" +
                "        wrapper.set_ack_bitfield(ackBitfield);\n\n");

        for (int i = 0; i < allPackets.size(); i++) {
            if (i == 0)
                modifiedSrc.append("        ");
            else
                modifiedSrc.append(" else ");

            String packetType = allPackets.get(i);
            modifiedSrc.append("if (auto* ").append(packetType)
                    .append(" = dynamic_cast<").append(Convert.snakeToCamel(packetType))
                    .append("*>(packet)) {\n" +
                    "            wrapper.set_allocated_").append(packetType)
                    .append("(").append(packetType).append(");\n" +
                    "            size_t dataLen = wrapper.ByteSizeLong();\n" +
                    "            std::shared_ptr<char[]> data(new char[dataLen]);\n" +
                    "            wrapper.SerializeToArray(data.get(), static_cast<int>(dataLen));\n" +
                    "            wrapper.release_").append(packetType).append("();\n" +
                    "\n" +
                    "            return std::make_shared<WrappedPacketData>(data, dataLen);\n" +
                    "        }");
        }

        modifiedSrc.append(" else\n" +
                "            // Код \"if ...\" для пакетов этого типа отсутствует выше.\n" +
                "            // Нужно добавить! (исп. awd-ptrans-codegen)\n" +
                "            throw std::invalid_argument(\"no implemented transformer for this packet type\");\n");
    }

    @Override
    public void appendGeneratedSourcesUnrap(StringBuilder modifiedSrc, List<String> allPackets) {
        modifiedSrc.append("        PacketWrapper wrapper;\n" +
                "        wrapper.ParseFromArray(data, static_cast<int>(dataLen));\n" +
                "\n" +
                "        uint32_t sequence    = wrapper.sequence();\n" +
                "        uint32_t ack         = wrapper.ack();\n" +
                "        uint32_t ackBitfield = wrapper.ack_bitfield();\n" +
                "        \n" +
                "        PacketWrapper::PacketCase packetType = wrapper.packet_case();\n" +
                "\n" +
                "        switch (packetType) {\n");

        for (String packetType : allPackets)
            modifiedSrc.append("            case PacketWrapper::PacketCase::k")
                    .append(Convert.snakeToCamel(packetType)).append(":\n" +
                    "                return std::make_shared<UnwrappedPacketData>(\n" +
                    "                        sequence, ack, ackBitfield, packetType,\n" +
                    "                        std::make_shared<").append(Convert.snakeToCamel(packetType))
                    .append(">(wrapper.")
                    .append(packetType).append("()));\n\n");

        modifiedSrc.append("            default:\n" +
                "                // Неизвестный пакет - он будет проигнорирован (не передан никакому PacketListener'у).\n" +
                "                return nullptr;\n" +
                "        }\n");
    }

}
