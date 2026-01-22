package src.main.java.com.model.comparer;

import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;

import javax.swing.*;

import com.dakota.ExamplePlugin;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

public class RenderPanelOSRS extends JPanel {

    private final ExamplePlugin plugin;
    private JLabel canvas;
    private int[] pixels;
    private float[] zBuffer;
    private net.runelite.api.Model currentModel;
    
    private int yaw = -200, pitch = -110, zoom = 1750;
    private int lastX, lastY;

    // [FIX] Constructor matching the one called in NpcComparerGUI
    public RenderPanelOSRS(ExamplePlugin plugin) {
        this.plugin = plugin;
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        
        canvas = new JLabel("Initializing OSRS...", SwingConstants.CENTER);
        canvas.setOpaque(true);
        canvas.setBackground(new Color(20, 20, 20));
        add(canvas, BorderLayout.CENTER);
        
        setupMouse();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                initRaster();
                render();
            }
        });
    }

    public void loadNpc(NPCComposition def) {
        if (def == null) return;
        // [FIX] Calls getClientThread() which is now defined in ExamplePlugin
        plugin.getClientThread().invoke(() -> {
            int[] modelIds = def.getModels();
            if (modelIds == null) return;

            ModelData[] parts = new ModelData[modelIds.length];
            for (int i = 0; i < modelIds.length; i++) {
                parts[i] = plugin.getClient().loadModelData(modelIds[i]);
            }
            ModelData modelData = plugin.getClient().mergeModels(parts);

            if (modelData != null) {
                short[] find = def.getColorToReplace();
                short[] replace = def.getColorToReplaceWith();
                if (find != null && replace != null) {
                    modelData.cloneColors();
                    for (int i = 0; i < find.length; i++) {
                        modelData.recolor(find[i], replace[i]);
                    }
                }
                net.runelite.api.Model model = modelData.light(64, 850, -30, -50, -30);
                SwingUtilities.invokeLater(() -> {
                    this.currentModel = model;
                    render();
                });
            }
        });
    }

    private void initRaster() {
        int w = getWidth(); int h = getHeight();
        if (w > 0 && h > 0) { pixels = new int[w * h]; zBuffer = new float[w * h]; }
    }

    private void render() {
        if (pixels == null) initRaster();
        if (currentModel == null || getWidth() <= 0) return;
        
        Arrays.fill(pixels, 0xFF282828);
        Arrays.fill(zBuffer, Float.MAX_VALUE);

        int cx = getWidth() / 2;
        int cy = getHeight() / 2 + 150; 

        double angY = (yaw / 2048.0) * Math.PI * 2;
        double sinY = Math.sin(angY); double cosY = Math.cos(angY);
        double angP = (pitch / 2048.0) * Math.PI * 2;
        double sinP = Math.sin(angP); double cosP = Math.cos(angP);

        float[] xVerts = currentModel.getVerticesX();
        float[] yVerts = currentModel.getVerticesY();
        float[] zVerts = currentModel.getVerticesZ();
        int[] indices1 = currentModel.getFaceIndices1();
        int[] indices2 = currentModel.getFaceIndices2();
        int[] indices3 = currentModel.getFaceIndices3();
        int[] faceColors = currentModel.getFaceColors1();

        for (int i = 0; i < currentModel.getFaceCount(); i++) {
            int i1 = indices1[i]; int i2 = indices2[i]; int i3 = indices3[i];

            float[] v1 = rotate(xVerts[i1], yVerts[i1], zVerts[i1], sinY, cosY, sinP, cosP);
            float[] v2 = rotate(xVerts[i2], yVerts[i2], zVerts[i2], sinY, cosY, sinP, cosP);
            float[] v3 = rotate(xVerts[i3], yVerts[i3], zVerts[i3], sinY, cosY, sinP, cosP);

            Point3D p1 = project(v1, cx, cy);
            Point3D p2 = project(v2, cx, cy);
            Point3D p3 = project(v3, cx, cy);

            if (p1 != null && p2 != null && p3 != null) {
                int hsl = (faceColors != null && i < faceColors.length) ? faceColors[i] : 0;
                drawTriangle(pixels, zBuffer, getWidth(), getHeight(), p1, p2, p3, rs2ColorToInt(hsl));
            }
        }

        BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        System.arraycopy(pixels, 0, ((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0, pixels.length);
        canvas.setIcon(new ImageIcon(img));
    }

    private void drawTriangle(int[] pixels, float[] zBuf, int w, int h, Point3D v1, Point3D v2, Point3D v3, int color) {
        if (v1.y > v2.y) { Point3D t = v1; v1 = v2; v2 = t; }
        if (v1.y > v3.y) { Point3D t = v1; v1 = v3; v3 = t; }
        if (v2.y > v3.y) { Point3D t = v2; v2 = v3; v3 = t; }

        int totalHeight = (int) (v3.y - v1.y);
        if (totalHeight == 0) return;

        for (int i = 0; i < totalHeight; i++) {
            boolean secondHalf = i > v2.y - v1.y || v2.y == v1.y;
            int segmentHeight = secondHalf ? (int) (v3.y - v2.y) : (int) (v2.y - v1.y);
            float alpha = (float) i / totalHeight;
            float beta  = (float) (i - (secondHalf ? v2.y - v1.y : 0)) / segmentHeight; 
            
            Point3D A = v1.add(v3.subtract(v1).scale(alpha));
            Point3D B = secondHalf ? v2.add(v3.subtract(v2).scale(beta)) : v1.add(v2.subtract(v1).scale(beta));

            if (A.x > B.x) { Point3D t = A; A = B; B = t; }

            int y = (int) (v1.y + i);
            if (y < 0 || y >= h) continue;

            int startX = (int) A.x; int endX = (int) B.x;
            float zStart = A.z; float zEnd = B.z;
            float dx = (endX - startX);

            if (startX < 0) startX = 0;
            if (endX >= w) endX = w - 1;

            for (int x = startX; x <= endX; x++) {
                float phi = (dx == 0) ? 1.0f : (x - A.x) / dx;
                float z = zStart + (zEnd - zStart) * phi;
                int idx = x + y * w;
                if (z < zBuf[idx]) { zBuf[idx] = z; pixels[idx] = color; }
            }
        }
    }

    private float[] rotate(float x, float y, float z, double sinY, double cosY, double sinP, double cosP) {
        float rx = (float) ((x * cosY) - (z * sinY));
        float rz = (float) ((x * sinY) + (z * cosY));
        float ry = y;
        float ry2 = (float) ((ry * cosP) + (rz * sinP)); 
        float rz2 = (float) ((rz * cosP) - (ry * sinP)); 
        return new float[]{rx, -ry2, rz2};
    }

    private Point3D project(float[] v, int offX, int offY) {
        float z = v[2] + 1500; if (z <= 50) return null; 
        float screenX = offX + (v[0] * zoom) / z;
        float screenY = offY - (v[1] * zoom) / z; 
        return new Point3D(screenX, screenY, z);
    }
    
    private static int rs2ColorToInt(int hsl) {
        if (hsl <= 0) return 0xFF000000;
        int hue = (hsl >> 10) & 0x3f; int sat = (hsl >> 7) & 0x07; int lum = (hsl & 0x7f);
        return HSLtoRGB(hue / 63.0f, sat / 7.0f, lum / 127.0f);
    }

    private static int HSLtoRGB(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) { r = g = b = l; } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f/3f); g = hueToRgb(p, q, h); b = hueToRgb(p, q, h - 1f/3f);
        }
        return (0xFF << 24) | ((int)(clamp(r)*255) << 16) | ((int)(clamp(g)*255) << 8) | (int)(clamp(b)*255);
    }
    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1; if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }
    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }
    private static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) { this.x=x; this.y=y; this.z=z; }
        Point3D add(Point3D o) { return new Point3D(x+o.x, y+o.y, z+o.z); }
        Point3D subtract(Point3D o) { return new Point3D(x-o.x, y-o.y, z-o.z); }
        Point3D scale(float s) { return new Point3D(x*s, y*s, z*s); }
    }
    private void setupMouse() {
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { lastX = e.getX(); lastY = e.getY(); }
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || e.isShiftDown()) {
                    yaw += (e.getX() - lastX) * 4; pitch -= (e.getY() - lastY) * 4;
                    lastX = e.getX(); lastY = e.getY(); render();
                }
            }
            public void mouseWheelMoved(MouseWheelEvent e) { zoom -= e.getWheelRotation() * 50; render(); }
        };
        canvas.addMouseListener(ma); canvas.addMouseMotionListener(ma); canvas.addMouseWheelListener(ma);
    }
    
    public void loadCustomNpc(int[] modelIds, int[] srcColors, int[] dstColors) {
        if (modelIds == null || modelIds.length == 0) {
            updateModel(null);
            return;
        }

        plugin.getClientThread().invoke(() -> {
            try {
                net.runelite.api.ModelData combined = null;
                for (int id : modelIds) {
                    net.runelite.api.ModelData md = plugin.getClient().loadModelData(id);
                    if (md != null) {
                        combined = (combined == null) ? md : plugin.getClient().mergeModels(combined, md);
                    }
                }

                if (combined == null) {
                    updateModel(null);
                    return;
                }

                // Apply Colors
                if (srcColors != null && dstColors != null) {
                    // Clone before coloring to avoid affecting the cache
                    combined.cloneColors(); 
                    for (int i = 0; i < srcColors.length; i++) {
                        if (i < dstColors.length) {
                            combined.recolor((short) srcColors[i], (short) dstColors[i]);
                        }
                    }
                }

                // [FIX] Use light() instead of createModel()
                net.runelite.api.Model model = combined.light(64, 850, -30, -50, -30);
                
                SwingUtilities.invokeLater(() -> updateModel(model));
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void updateModel(net.runelite.api.Model model) {
        this.currentModel = model;
        render();
        repaint();
    }
    
}