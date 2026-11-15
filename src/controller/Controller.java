package controller;

import graphics.clipping.CohenSutherlandClipper;
import graphics.filler.ScanlineFiller;
import graphics.filler.RecursiveSeedFiller;
import graphics.filler.SeedFiller;
import graphics.clipping.PolygonClipper;
import graphics.clipping.SutherlandHodgmanClipper;
import graphics.rasterizer.LineRasterizer;
import graphics.rasterizer.PolygonOutlineRasterizer;
import graphics.rasterizer.PolygonRasterizer;
import graphics.rasterizer.XiaolinWuLineRasterizer;
import model.Line;
import model.Point;
import model.Polygon;
import view.Canvas;
import view.gui.Toolbar;

import java.awt.Color;
import java.awt.event.*;
import java.util.List;

import javax.swing.AbstractAction;

import static java.awt.event.KeyEvent.VK_C;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;

public class Controller {

    private final Canvas canvas;
    private final Toolbar toolbar;

    private Line previewLine;

    private final Polygon userPolygon = new Polygon();
    private final Polygon clippingPolygon;

    private final PolygonClipper clipper;
    private final PolygonRasterizer polygonRasterizer;
    private final LineRasterizer lineRasterizer;

    private Point seedFillPoint;
    private boolean seedFillMode;
    private final SeedFiller seedFiller;
    private final ScanlineFiller scanlineFiller;

    private boolean clipMode;
    private Point clipStartPoint;
    private Line previewClipRect;

    private static final int LINE_COLOR = Color.WHITE.getRGB();
    private static final int PREVIEW_COLOR = Color.RED.getRGB();
    private static final int ORIGINAL_COLOR = new Color(0x9099A3).getRGB();
    private static final int CLIPPED_POLYGON_COLOR = new Color(0xA4F2DF).getRGB();
    private static final int CLIP_RECT_COLOR = Color.YELLOW.getRGB();
    private static final int CLIPPING_POLYGON_COLOR = new Color(0xE6B85C).getRGB();
    private static final int FILL_COLOR = new Color(0x58BFA9).getRGB();

    // 5vrcholová ořezová šablona
    private static final List<Point> CLIPPING_POLYGON_VERTICES = List.of(
            new Point(200, 120),
            new Point(450, 150),
            new Point(520, 350),
            new Point(350, 480),
            new Point(180, 400)
    );

    public Controller(Canvas canvas, Toolbar toolbar) {
        this.canvas = canvas;
        this.toolbar = toolbar;

        this.clipper = new SutherlandHodgmanClipper();
        this.lineRasterizer = new XiaolinWuLineRasterizer(canvas.getRaster());
        this.scanlineFiller = new ScanlineFiller(canvas.getRaster());
        this.seedFiller = new RecursiveSeedFiller(canvas.getRaster());
        this.polygonRasterizer = new PolygonOutlineRasterizer(this.lineRasterizer);

        this.clippingPolygon = createPolygon(CLIPPING_POLYGON_VERTICES, CLIPPING_POLYGON_COLOR);
    }

    public void init() {
        toolbar.addClearListener(_ -> clearDrawing());
        toolbar.addSeedFillListener(_ -> toggleSeedFillMode());
        toolbar.addClipListener(_ -> toggleClipMode());

        canvas.getInputMap(WHEN_IN_FOCUSED_WINDOW).put(getKeyStroke(VK_C, 0), "clearCanvas");
        canvas.getActionMap().put("clearCanvas", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                clearDrawing();
            }
        });

        canvas.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1) {
                    if (clipMode) {
                        clipStartPoint = getPoint(event);
                    } else if (seedFillMode) {
                        fillAt(event);
                    } else {
                        startLine(event);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }

                // COHEN-SUTHERLAND
                if (clipMode && clipStartPoint != null) {
                    Point endPoint = getPoint(event);
                    int xmin = Math.min(clipStartPoint.getX(), endPoint.getX());
                    int ymin = Math.min(clipStartPoint.getY(), endPoint.getY());
                    int xmax = Math.max(clipStartPoint.getX(), endPoint.getX());
                    int ymax = Math.max(clipStartPoint.getY(), endPoint.getY());

                    CohenSutherlandClipper tempClipper = new CohenSutherlandClipper(xmin, ymin, xmax, ymax);

                    List<Line> polygonLines = userPolygon.getLines();
                    // Pokud má polygon prave 1 linku orez pres Cohen-Sutherland
                    if (polygonLines.size() == 1) {
                        Line line = polygonLines.get(0);
                        Line clipped = tempClipper.clip(line);

                        userPolygon.clear();
                        if (clipped != null) {
                            userPolygon.addLine(new Line(clipped.getPoint1(), clipped.getPoint2(), LINE_COLOR));
                        }
                    } else {
                        //prazdny else jinak se rozsviti render na 148
                    }

                    clipStartPoint = null;
                    previewClipRect = null;
                    clipMode = false;
                    try {
                        toolbar.getClass().getMethod("setClipMode", boolean.class).invoke(toolbar, false);
                    } catch (Exception ignored) {}
                    render();
                    return;
                }

                if (clipMode) {
                    return;
                }

                if (previewLine == null) {
                    return;
                }

                Point endPoint = getPoint(event);
                if (event.isShiftDown()) {
                    endPoint = snapToAxisOrDiagonal(previewLine.getPoint1(), endPoint);
                }

                // Pridani usecky do polygonu a zruseni nahledu
                userPolygon.addLine(new Line(previewLine.getPoint1(), endPoint, LINE_COLOR));
                previewLine = null;
                render();
            }
        });

        canvas.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent event) {
                if (clipMode && clipStartPoint != null) {
                    Point current = getPoint(event);
                    previewClipRect = new Line(clipStartPoint, current, CLIP_RECT_COLOR);
                    render();
                    return;
                }

                if (clipMode) {
                    return;
                }

                if (previewLine != null) {
                    Point endPoint = getPoint(event);
                    if (event.isShiftDown()) {
                        endPoint = snapToAxisOrDiagonal(previewLine.getPoint1(), endPoint);
                    }
                    previewLine = new Line(previewLine.getPoint1(), endPoint, PREVIEW_COLOR);
                    render();
                }
            }
        });

        render();
    }

    private Point snapToAxisOrDiagonal(Point start, Point current) {
        int dx = current.getX() - start.getX();
        int dy = current.getY() - start.getY();
        double length = Math.hypot(dx, dy);
        double step = Math.PI / 4;
        double angle = Math.round(Math.atan2(dy, dx) / step) * step;

        int snappedDx = (int) Math.round(length * Math.cos(angle));
        int snappedDy = (int) Math.round(length * Math.sin(angle));
        return new Point(start.getX() + snappedDx, start.getY() + snappedDy);
    }

    private void render() {
        canvas.clear();

        // Sutherland-Hodgman ořez
        Polygon subject = withPreviewLine(userPolygon, previewLine);
        Polygon clippedPolygon;
        try {
            clippedPolygon = clipper.clip(subject, clippingPolygon);
        } catch (UnsupportedOperationException unfinishedClipper) {
            clippedPolygon = null;
        }
        if (clippedPolygon != null) {
            scanlineFiller.fill(clippedPolygon, FILL_COLOR);
            polygonRasterizer.rasterize(withColor(clippedPolygon, CLIPPED_POLYGON_COLOR), null);
        }

        // vykresleny obrysu polygonu a orezu polygonu
        polygonRasterizer.rasterize(withColor(userPolygon, ORIGINAL_COLOR), previewLine);
        polygonRasterizer.rasterize(clippingPolygon, null);

        // nahled orezu
        if (previewClipRect != null) {
            drawPreviewRectangle();
        }

        // Seed fill
        if (seedFillPoint != null) {
            seedFiller.fill(seedFillPoint.getX(), seedFillPoint.getY(), canvas.getBackgroundColor(), FILL_COLOR);
        }
        canvas.repaint();
    }

    private void drawRectangle(int x1, int y1, int x2, int y2, int color) {
        lineRasterizer.rasterize(new Line(new Point(x1, y1), new Point(x2, y1), color));
        lineRasterizer.rasterize(new Line(new Point(x2, y1), new Point(x2, y2), color));
        lineRasterizer.rasterize(new Line(new Point(x2, y2), new Point(x1, y2), color));
        lineRasterizer.rasterize(new Line(new Point(x1, y2), new Point(x1, y1), color));
    }

    private void drawPreviewRectangle() {
        Point p1 = previewClipRect.getPoint1();
        Point p2 = previewClipRect.getPoint2();
        int rxMin = Math.min(p1.getX(), p2.getX());
        int ryMin = Math.min(p1.getY(), p2.getY());
        int rxMax = Math.max(p1.getX(), p2.getX());
        int ryMax = Math.max(p1.getY(), p2.getY());
        drawRectangle(rxMin, ryMin, rxMax, ryMax, CLIP_RECT_COLOR);
    }

    private void clearDrawing() {
        userPolygon.clear();
        previewLine = null;
        previewClipRect = null;
        clipStartPoint = null;
        seedFillPoint = null;
        seedFillMode = false;
        clipMode = false;
        toolbar.setSeedFillMode(false);
        try {
            toolbar.getClass().getMethod("setClipMode", boolean.class).invoke(toolbar, false);
        } catch (Exception ignored) {}
        render();
    }

    private void toggleClipMode() {
        clipMode = !clipMode;
        seedFillMode = false;
        try {
            toolbar.getClass().getMethod("setClipMode", boolean.class).invoke(toolbar, clipMode);
            toolbar.setSeedFillMode(false);
        } catch (Exception ignored) {}
        render();
    }

    private void toggleSeedFillMode() {
        seedFillMode = !seedFillMode;
        try {
            toolbar.getClass().getMethod("setClipMode", boolean.class).invoke(toolbar, false);
        } catch (Exception ignored) {}
        render();
    }

    private void fillAt(MouseEvent event) {
        Point seed = getPoint(event);
        Integer seedColor = canvas.getRaster().getPixel(seed.getX(), seed.getY());
        if (seedFillPoint != null && seedColor != null && seedColor == FILL_COLOR) {
            seedFillPoint = null;
            render();
            return;
        }

        boolean filled = seedFiller.fill(seed.getX(), seed.getY(), canvas.getBackgroundColor(), FILL_COLOR);
        if (filled) {
            seedFillPoint = seed;
            render();
        }
    }

    private Point getPoint(MouseEvent event) {
        return new Point(event.getX(), event.getY());
    }

    private void startLine(MouseEvent event) {
        seedFillPoint = null;
        Point startPoint = userPolygon.getLastPoint();
        if (startPoint == null) {
            startPoint = getPoint(event);
        }
        Point endPoint = getPoint(event);
        if (event.isShiftDown()) {
            endPoint = snapToAxisOrDiagonal(startPoint, endPoint);
        }
        previewLine = new Line(startPoint, endPoint, PREVIEW_COLOR);
        render();
    }

    private Polygon withColor(Polygon source, int color) {
        Polygon result = new Polygon();
        for (Line line : source.getLines()) {
            result.addLine(new Line(line.getPoint1(), line.getPoint2(), color));
        }
        return result;
    }

    private Polygon withPreviewLine(Polygon source, Line preview) {
        Polygon result = new Polygon();
        for (Line line : source.getLines()) {
            result.addLine(line);
        }
        if (preview != null) {
            result.addLine(preview);
        }
        return result;
    }

    private Polygon createPolygon(List<? extends Point> vertices, int color) {
        Polygon result = new Polygon();
        for (int i = 0; i + 1 < vertices.size(); i++) {
            result.addLine(new Line(vertices.get(i), vertices.get(i + 1), color));
        }
        return result;
    }
}
