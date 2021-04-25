package gg.aswedrown.ptranscodegen;

public class SourceSets {

    private final String originalSrc, modifiedSrc;

    public SourceSets(String originalSrc, String modifiedSrc) {
        this.originalSrc = originalSrc;
        this.modifiedSrc = modifiedSrc;
    }

    public String getOriginalSrc() {
        return originalSrc;
    }

    public String getModifiedSrc() {
        return modifiedSrc;
    }

    public boolean areProgrammaticallyEqual() {
        return rawCode(originalSrc).equals(rawCode(modifiedSrc));
    }

    private String rawCode(String sourceCode) {
        return sourceCode.replaceAll("[\\s]", "");
    }

}
