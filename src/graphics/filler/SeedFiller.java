package graphics.filler;

public interface SeedFiller {

    boolean fill(int x1, int y1, int targetColor, int fillColor);
    boolean fill(int x1, int y1, int boundaryOrTargetColor, int fillColor, boolean useBoundary);
}
