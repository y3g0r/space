package com.space.ui;

import com.space.model.FileNode;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.function.Consumer;

/**
 * Interactive multi-layer pie chart for visualizing disk usage.
 * - Center: Current directory info
 * - Inner ring: Direct children
 * - Outer ring(s): Grandchildren
 */
public class MultiLayerPieChart extends Canvas {
    private static final double INNER_RADIUS_RATIO = 0.25;
    private static final double LAYER_WIDTH_RATIO = 0.30;
    private static final int MIN_SEGMENT_ANGLE = 5; // Minimum degrees for a segment to be visible

    private FileNode currentNode;
    private final List<PieSegment> segments = new ArrayList<>();
    private PieSegment hoveredSegment = null;
    private Consumer<FileNode> onNavigate;
    private final Random colorRandom = new Random(42); // Deterministic colors

    public MultiLayerPieChart(double width, double height) {
        super(width, height);
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        setOnMouseMoved(this::handleMouseMove);
        setOnMouseClicked(this::handleMouseClick);
    }

    public void setCurrentNode(FileNode node) {
        this.currentNode = node;
        this.colorRandom.setSeed(42); // Reset for consistent colors
        redraw();
    }

    public void setOnNavigate(Consumer<FileNode> onNavigate) {
        this.onNavigate = onNavigate;
    }

    private void handleMouseMove(MouseEvent event) {
        double x = event.getX();
        double y = event.getY();

        PieSegment oldHovered = hoveredSegment;
        hoveredSegment = findSegmentAt(x, y);

        if (oldHovered != hoveredSegment) {
            redraw();
        }
    }

    private void handleMouseClick(MouseEvent event) {
        if (event.getClickCount() == 2) {
            double x = event.getX();
            double y = event.getY();

            // Check if clicking center to go up
            double centerX = getWidth() / 2;
            double centerY = getHeight() / 2;
            double dx = x - centerX;
            double dy = y - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);
            double radius = Math.min(getWidth(), getHeight()) / 2;
            double innerRadius = radius * INNER_RADIUS_RATIO;

            if (distance < innerRadius && currentNode != null && currentNode.getParent() != null) {
                navigateTo(currentNode.getParent());
                return;
            }

            // Check if clicking a segment
            PieSegment clicked = findSegmentAt(x, y);
            if (clicked != null && clicked.node.isDirectory() && !clicked.node.getChildren().isEmpty()) {
                navigateTo(clicked.node);
            }
        }
    }

    private PieSegment findSegmentAt(double x, double y) {
        double centerX = getWidth() / 2;
        double centerY = getHeight() / 2;
        double dx = x - centerX;
        double dy = y - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;

        for (PieSegment segment : segments) {
            if (distance >= segment.innerRadius && distance <= segment.outerRadius) {
                double startAngle = segment.startAngle;
                double endAngle = segment.startAngle + segment.extent;

                // Normalize angles
                if (startAngle < 0) startAngle += 360;
                if (endAngle < 0) endAngle += 360;

                if (startAngle <= endAngle) {
                    if (angle >= startAngle && angle <= endAngle) {
                        return segment;
                    }
                } else {
                    if (angle >= startAngle || angle <= endAngle) {
                        return segment;
                    }
                }
            }
        }
        return null;
    }

    private void navigateTo(FileNode node) {
        if (onNavigate != null) {
            onNavigate.accept(node);
        }
    }

    private void redraw() {
        segments.clear();
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        if (currentNode == null) {
            return;
        }

        double centerX = getWidth() / 2;
        double centerY = getHeight() / 2;
        double radius = Math.min(getWidth(), getHeight()) / 2 - 10;

        // Draw center circle (current directory)
        drawCenterCircle(gc, centerX, centerY, radius);

        // Draw layers
        if (currentNode.isDirectory() && !currentNode.getChildren().isEmpty()) {
            drawFirstLayerWithChildren(gc, currentNode.getChildren(), centerX, centerY, radius);
        }
    }

    private void drawCenterCircle(GraphicsContext gc, double centerX, double centerY, double radius) {
        double innerRadius = radius * INNER_RADIUS_RATIO;

        // Draw circle
        gc.setFill(Color.rgb(245, 245, 250));
        gc.fillOval(centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2);

        // Draw border
        gc.setStroke(Color.rgb(200, 200, 210));
        gc.setLineWidth(2);
        gc.strokeOval(centerX - innerRadius, centerY - innerRadius, innerRadius * 2, innerRadius * 2);

        // Draw text
        gc.setFill(Color.rgb(60, 60, 80));
        gc.setFont(Font.font("System", 12));
        gc.setTextAlign(TextAlignment.CENTER);

        String name = currentNode.getName();
        String size = FileNode.formatSize(currentNode.getSize());

        gc.fillText(truncateText(name, 15), centerX, centerY - 10);
        gc.fillText(size, centerX, centerY + 10);

        if (currentNode.getParent() != null) {
            gc.setFont(Font.font("System", 9));
            gc.setFill(Color.rgb(100, 100, 120));
            gc.fillText("(double-click to go up)", centerX, centerY + 25);
        }
    }

    private void drawFirstLayerWithChildren(GraphicsContext gc, List<FileNode> children,
                                            double centerX, double centerY, double radius) {
        double innerRadius = radius * INNER_RADIUS_RATIO;
        double outerRadius = radius * (INNER_RADIUS_RATIO + LAYER_WIDTH_RATIO);
        double secondInnerRadius = outerRadius;
        double secondOuterRadius = radius * (INNER_RADIUS_RATIO + LAYER_WIDTH_RATIO * 2);

        // Calculate total size
        long totalSize = children.stream().mapToLong(FileNode::getSize).sum();
        if (totalSize == 0) return;

        // Sort by size (largest first)
        List<FileNode> sortedNodes = children.stream()
                .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                .toList();

        double currentAngle = -90; // Start from top

        for (FileNode node : sortedNodes) {
            double percentage = (double) node.getSize() / totalSize;
            double extent = 360 * percentage;

            // Skip very small segments
            if (extent < MIN_SEGMENT_ANGLE) continue;

            Color color = generateColor(node);
            boolean isHovered = hoveredSegment != null && hoveredSegment.node == node;

            if (isHovered) {
                color = color.brighter();
            }

            // Draw first layer segment
            drawSegment(gc, centerX, centerY, innerRadius, outerRadius,
                    currentAngle, extent, color, node, isHovered);

            // Store segment for hit detection
            segments.add(new PieSegment(node, innerRadius, outerRadius, currentAngle, extent));

            // Draw grandchildren in second layer within this parent's angular bounds
            if (node.isDirectory() && !node.getChildren().isEmpty()) {
                drawChildrenInSector(gc, node.getChildren(), centerX, centerY,
                        secondInnerRadius, secondOuterRadius,
                        currentAngle, extent);
            }

            currentAngle += extent;
        }
    }

    private void drawChildrenInSector(GraphicsContext gc, List<FileNode> children,
                                      double centerX, double centerY,
                                      double innerRadius, double outerRadius,
                                      double sectorStartAngle, double sectorExtent) {
        // Calculate total size of children
        long totalSize = children.stream().mapToLong(FileNode::getSize).sum();
        if (totalSize == 0) return;

        // Sort by size (largest first)
        List<FileNode> sortedNodes = children.stream()
                .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                .toList();

        double currentAngle = sectorStartAngle;

        for (FileNode node : sortedNodes) {
            double percentage = (double) node.getSize() / totalSize;
            double extent = sectorExtent * percentage;

            // Skip very small segments
            if (extent < MIN_SEGMENT_ANGLE) continue;

            Color color = generateColor(node);
            boolean isHovered = hoveredSegment != null && hoveredSegment.node == node;

            if (isHovered) {
                color = color.brighter();
            }

            // Draw segment within the parent's sector
            drawSegment(gc, centerX, centerY, innerRadius, outerRadius,
                    currentAngle, extent, color, node, isHovered);

            // Store segment for hit detection
            segments.add(new PieSegment(node, innerRadius, outerRadius, currentAngle, extent));

            currentAngle += extent;
        }
    }

    private void drawLayer(GraphicsContext gc, List<FileNode> nodes, double centerX, double centerY,
                           double radius, int layerIndex) {
        double innerRadius = radius * (INNER_RADIUS_RATIO + LAYER_WIDTH_RATIO * layerIndex);
        double outerRadius = radius * (INNER_RADIUS_RATIO + LAYER_WIDTH_RATIO * (layerIndex + 1));

        // Calculate total size
        long totalSize = nodes.stream().mapToLong(FileNode::getSize).sum();
        if (totalSize == 0) return;

        // Sort by size (largest first)
        List<FileNode> sortedNodes = nodes.stream()
                .sorted((a, b) -> Long.compare(b.getSize(), a.getSize()))
                .toList();

        double currentAngle = -90; // Start from top

        for (FileNode node : sortedNodes) {
            double percentage = (double) node.getSize() / totalSize;
            double extent = 360 * percentage;

            // Skip very small segments
            if (extent < MIN_SEGMENT_ANGLE) continue;

            Color color = generateColor(node);
            boolean isHovered = hoveredSegment != null && hoveredSegment.node == node;

            if (isHovered) {
                color = color.brighter();
            }

            // Draw segment
            drawSegment(gc, centerX, centerY, innerRadius, outerRadius,
                    currentAngle, extent, color, node, isHovered);

            // Store segment for hit detection
            segments.add(new PieSegment(node, innerRadius, outerRadius, currentAngle, extent));

            currentAngle += extent;
        }
    }

    private void drawSegment(GraphicsContext gc, double centerX, double centerY,
                             double innerRadius, double outerRadius,
                             double startAngle, double extent, Color color,
                             FileNode node, boolean isHovered) {
        gc.setFill(color);

        // Draw arc using polygon approximation
        int steps = Math.max(3, (int) (extent / 2));
        double[] xPoints = new double[steps * 2 + 2];
        double[] yPoints = new double[steps * 2 + 2];

        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startAngle + extent * i / steps);
            xPoints[i] = centerX + outerRadius * Math.cos(angle);
            yPoints[i] = centerY + outerRadius * Math.sin(angle);
        }

        for (int i = 0; i <= steps; i++) {
            double angle = Math.toRadians(startAngle + extent * (steps - i) / steps);
            xPoints[steps + 1 + i] = centerX + innerRadius * Math.cos(angle);
            yPoints[steps + 1 + i] = centerY + innerRadius * Math.sin(angle);
        }

        gc.fillPolygon(xPoints, yPoints, xPoints.length);

        // Draw border
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(isHovered ? 3 : 1);
        gc.strokePolygon(xPoints, yPoints, xPoints.length);

        // Draw label if segment is large enough
        if (extent > 20) {
            double midAngle = Math.toRadians(startAngle + extent / 2);
            double labelRadius = (innerRadius + outerRadius) / 2;
            double labelX = centerX + labelRadius * Math.cos(midAngle);
            double labelY = centerY + labelRadius * Math.sin(midAngle);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("System", 10));
            gc.setTextAlign(TextAlignment.CENTER);

            String label = truncateText(node.getName(), 10);
            if (extent > 40) {
                gc.fillText(label, labelX, labelY - 5);
                gc.fillText(FileNode.formatSize(node.getSize()), labelX, labelY + 8);
            } else {
                gc.fillText(label, labelX, labelY);
            }
        }

        // Draw tooltip on hover
        if (isHovered && extent <= 20) {
            gc.setFill(Color.rgb(50, 50, 60, 0.9));
            gc.setStroke(Color.rgb(200, 200, 210));
            gc.setLineWidth(1);

            String text = node.getName() + " (" + FileNode.formatSize(node.getSize()) + ")";
            double textWidth = text.length() * 6 + 20;
            double tooltipX = centerX - textWidth / 2;
            double tooltipY = centerY - outerRadius - 30;

            gc.fillRoundRect(tooltipX, tooltipY, textWidth, 25, 5, 5);
            gc.strokeRoundRect(tooltipX, tooltipY, textWidth, 25, 5, 5);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("System", 11));
            gc.fillText(text, centerX, tooltipY + 16);
        }
    }

    private Color generateColor(FileNode node) {
        // Generate deterministic color based on node name
        int hash = node.getName().hashCode();
        colorRandom.setSeed(hash);

        // Generate pleasant colors in the blue-purple-teal range
        double hue = 180 + colorRandom.nextDouble() * 140; // 180-320 degrees
        double saturation = 0.4 + colorRandom.nextDouble() * 0.3; // 40-70%
        double brightness = 0.6 + colorRandom.nextDouble() * 0.2; // 60-80%

        return Color.hsb(hue, saturation, brightness);
    }

    private String truncateText(String text, int maxLength) {
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 2) + "..";
    }

    private static class PieSegment {
        final FileNode node;
        final double innerRadius;
        final double outerRadius;
        final double startAngle;
        final double extent;

        PieSegment(FileNode node, double innerRadius, double outerRadius,
                   double startAngle, double extent) {
            this.node = node;
            this.innerRadius = innerRadius;
            this.outerRadius = outerRadius;
            this.startAngle = startAngle;
            this.extent = extent;
        }
    }
}
