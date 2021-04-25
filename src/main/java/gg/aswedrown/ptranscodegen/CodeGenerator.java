package gg.aswedrown.ptranscodegen;

import java.io.BufferedReader;
import java.util.List;

public interface CodeGenerator {

    SourceSets insertInExistingSources(BufferedReader srcReader, List<String> allPackets) throws Exception;

    void appendGeneratedSourcesWrap(StringBuilder modifiedSrc, List<String> allPackets);

    void appendGeneratedSourcesUnrap(StringBuilder modifiedSrc, List<String> allPackets);

}
