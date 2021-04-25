package gg.aswedrown.ptranscodegen;

import java.io.BufferedReader;
import java.util.List;

public class CppCodeGenerator implements CodeGenerator {

    private static final String WRAP_MTD_DECL
            = "std::shared_ptr<WrappedPacketData> internalGeneratedWrap(google::protobuf::Message* packet) {";

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
        modifiedSrc.append("        PacketWrapper wrapper;\n\n");

        for (int i = 0; i < allPackets.size(); i++) {
            modifiedSrc.append("        ");

            if (i > 0)
                modifiedSrc.append("else ");

            String packetType = allPackets.get(i);
            String packetTypeLower = packetType.toLowerCase();
            modifiedSrc.append("if (auto* ").append(packetTypeLower)
                    .append(" = dynamic_cast<").append(packetType).append("*>(packet))\n" +
                    "            wrapper.set_allocated_").append(packetTypeLower).append("(")
                    .append(packetTypeLower).append(");\n");
        }

        modifiedSrc.append("        else\n" +
                "            // Код \"if ...\" для пакетов этого типа отсутствует выше.\n" +
                "            // Нужно добавить! (исп. awd-ptrans-codegen)\n" +
                "            throw std::invalid_argument(\"no implemented transformer for this packet type\");\n" +
                "\n" +
                "        size_t dataLen = wrapper.ByteSizeLong();\n" +
                "        std::shared_ptr<char[]> data(new char[dataLen]);\n" +
                "        wrapper.SerializeToArray(data.get(), static_cast<int>(dataLen));\n" +
                "\n" +
                "        return std::make_shared<WrappedPacketData>(data, dataLen);\n");
    }

    @Override
    public void appendGeneratedSourcesUnrap(StringBuilder modifiedSrc, List<String> allPackets) {
        modifiedSrc.append("        PacketWrapper wrapper;\n" +
                "        wrapper.ParseFromArray(data, static_cast<int>(dataLen));\n" +
                "        PacketWrapper::PacketCase packetType = wrapper.packet_case();\n" +
                "\n" +
                "        switch (packetType) {\n");

        for (String packetType : allPackets)
            modifiedSrc.append("            case PacketWrapper::PacketCase::k").append(packetType).append(":\n" +
                    "                return std::make_shared<UnwrappedPacketData>(packetType,\n" +
                    "                        std::make_shared<").append(packetType).append(">(wrapper.")
                    .append(packetType.toLowerCase()).append("()));\n\n");

        modifiedSrc.append("            default:\n" +
                "                // Неизвестный пакет - он будет проигнорирован (не передан никакому PacketListener'у).\n" +
                "                return nullptr;\n" +
                "        }\n");
    }

}
