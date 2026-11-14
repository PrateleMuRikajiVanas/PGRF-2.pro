package graphics.clipping;

import model.Line;
import model.Point;

public class CohenSutherlandClipper {

    private static final int INSIDE = 0; // 0000
    private static final int LEFT = 1;   // 0001
    private static final int RIGHT = 2;  // 0010
    private static final int BOTTOM = 4; // 0100
    private static final int TOP = 8;    // 1000

    private final int xmin, ymin, xmax, ymax;

    public CohenSutherlandClipper(int xmin, int ymin, int xmax, int ymax) {
        this.xmin = Math.min(xmin, xmax);
        this.ymin = Math.min(ymin, ymax);
        this.xmax = Math.max(xmin, xmax);
        this.ymax = Math.max(ymin, ymax);
    }

    private int computeOutCode(double x, double y) {
        int code = INSIDE;
        if (x < xmin) {
            code |= LEFT;
        } else if (x > xmax) {
            code |= RIGHT;
        }
        if (y < ymin) {
            code |= BOTTOM;
        } else if (y > ymax) {
            code |= TOP;
        }
        return code;
    }

    public Line clip(Line line) {
        double x0 = line.getPoint1().getX();
        double y0 = line.getPoint1().getY();
        double x1 = line.getPoint2().getX();
        double y1 = line.getPoint2().getY();

        int outcode0 = computeOutCode(x0, y0);
        int outcode1 = computeOutCode(x1, y1);
        boolean accept = false;

        while (true) {
            if ((outcode0 | outcode1) == 0) {
                // trivialni prijeti
                accept = true;
                break;
            } else if ((outcode0 & outcode1) != 0) {
                // trivialni zamitnuti
                break;
            } else {
                double x = 0, y = 0;
                int outcodeOut = outcode0 != 0 ? outcode0 : outcode1;

                if ((outcodeOut & TOP) != 0) {
                    x = x0 + (x1 - x0) * (ymax - y0) / (y1 - y0);
                    y = ymax;
                } else if ((outcodeOut & BOTTOM) != 0) {
                    x = x0 + (x1 - x0) * (ymin - y0) / (y1 - y0);
                    y = ymin;
                } else if ((outcodeOut & RIGHT) != 0) {
                    y = y0 + (y1 - y0) * (xmax - x0) / (x1 - x0);
                    x = xmax;
                } else if ((outcodeOut & LEFT) != 0) {
                    y = y0 + (y1 - y0) * (xmin - x0) / (x1 - x0);
                    x = xmin;
                }

                if (outcodeOut == outcode0) {
                    x0 = x;
                    y0 = y;
                    outcode0 = computeOutCode(x0, y0);
                } else {
                    x1 = x;
                    y1 = y;
                    outcode1 = computeOutCode(x1, y1);
                }
            }
        }

        if (accept) {
            return new Line(
                    new Point((int) Math.round(x0), (int) Math.round(y0)),
                    new Point((int) Math.round(x1), (int) Math.round(y1)),
                    line.getColor()
            );
        }
        return null;
    }
}
