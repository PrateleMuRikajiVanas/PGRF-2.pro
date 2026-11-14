package graphics.filler;

import graphics.Raster;

public class RecursiveSeedFiller implements SeedFiller {

    private final Raster raster;

    public RecursiveSeedFiller(Raster raster) {
        this.raster = raster;
    }

    @Override
    public boolean fill(int x1, int y1, int targetColor, int fillColor) {
        return fill(x1, y1, targetColor, fillColor, false);
    }

    @Override
    public boolean fill(int x1, int y1, int boundaryOrTargetColor, int fillColor, boolean useBoundary) {
        Integer startColor = raster.getPixel(x1, y1);
        if (startColor == null) {
            return false;
        }

        if (useBoundary) {
            if (startColor == fillColor || startColor == boundaryOrTargetColor) {
                return false;
            }
        } else {
            if (startColor == fillColor || startColor != boundaryOrTargetColor) {
                return false;
            }
        }

        fillRegion(x1, y1, boundaryOrTargetColor, fillColor, useBoundary);
        return true;
    }

    private void fillRegion(int x, int y, int boundaryOrTargetColor, int fillColor, boolean useBoundary) {
        Integer color = raster.getPixel(x, y);
        if (color == null || color == fillColor) {
            return;
        }

        if (useBoundary) {
            if (color == boundaryOrTargetColor) {
                return; // narazili jsme na barvu hranice
            }
        } else {
            if (color != boundaryOrTargetColor) {
                return; // narazili jsme mimo barvu pozadí
            }
        }

        raster.setPixel(x, y, fillColor);

        fillRegion(x - 1, y, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x + 1, y, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x, y - 1, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x, y + 1, boundaryOrTargetColor, fillColor, useBoundary);

        fillRegion(x - 1, y - 1, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x - 1, y + 1, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x + 1, y - 1, boundaryOrTargetColor, fillColor, useBoundary);
        fillRegion(x + 1, y + 1, boundaryOrTargetColor, fillColor, useBoundary);
    }
}
