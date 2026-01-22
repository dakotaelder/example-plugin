package com.model;

import net.runelite.api.*;
import net.runelite.client.callback.ClientThread;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModelViewerGUI extends JFrame {

    private final ModelViewerPlugin plugin;
    private JList<String> npcList;
    private DefaultListModel<String> listModel;
    private JLabel canvasLabel;
    private JTextField animIdField;
    
    // Renderer State
    private int currentNpcId = -1;
    private net.runelite.api.Model currentModel = null;
    private JTextArea detailsArea;
    // Animation State
    private ModelData baseModelData = null; 
    private Animation currentAnimation = null;
    private int animFrame = 0;
    private int animCycle = 0;
    private Timer animTimer;
    
    // Camera Controls
    private int yaw = -200;
    private int pitch = -110;  
    private int zoom = 1750;  
    // Mouse State
    private int lastMouseX;
    private int lastMouseY;

    // Z-Buffer Rasterizer State
    private int[] canvasPixels;
    private float[] zBuffer;
    private int canvasWidth = 0;
    private int canvasHeight = 0;

    public ModelViewerGUI(ModelViewerPlugin plugin) {
        this.plugin = plugin;

        setTitle("Dakota Model Viewer (Z-Buffered)");
        setSize(900, 700);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // --- LEFT PANEL ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(250, 0));
        
        JTextField searchField = new JTextField();
        searchField.setToolTipText("Search NPC Name/ID");
        JButton searchBtn = new JButton("Search NPC");
        searchBtn.addActionListener(e -> performSearch(searchField.getText()));
        searchField.addActionListener(e -> performSearch(searchField.getText()));

        listModel = new DefaultListModel<>();
        npcList = new JList<>(listModel);
        npcList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        npcList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && npcList.getSelectedValue() != null) {
                parseSelection(npcList.getSelectedValue());
            }
        });

        leftPanel.add(searchField, BorderLayout.NORTH);
        leftPanel.add(new JScrollPane(npcList), BorderLayout.CENTER);
        leftPanel.add(searchBtn, BorderLayout.SOUTH);

        // --- CENTER CANVAS ---
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBackground(Color.DARK_GRAY);
        
        canvasLabel = new JLabel("Select an NPC", SwingConstants.CENTER);
        canvasLabel.setForeground(Color.WHITE);
        
        // Listeners for Resize (Re-alloc buffers) and Mouse
        canvasLabel.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                initBuffers(canvasLabel.getWidth(), canvasLabel.getHeight());
                renderFrame();
            }
        });
        setupMouseListeners();
        
        centerPanel.add(canvasLabel, BorderLayout.CENTER);

        // --- RIGHT PANEL ---
        JPanel rightPanel = new JPanel();
        rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
        rightPanel.setPreferredSize(new Dimension(200, 0));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        rightPanel.add(new JLabel("Animation ID:"));
        animIdField = new JTextField("-1");
        animIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        animIdField.addActionListener(e -> reloadAnimation(true)); 
        rightPanel.add(animIdField);
        
        JButton applyAnimBtn = new JButton("Apply / Search Anim");
        applyAnimBtn.addActionListener(e -> reloadAnimation(true));
        rightPanel.add(applyAnimBtn);

        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(new JLabel("NPC Details:"));
        detailsArea = new JTextArea(8, 15);
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);
        detailsArea.setFont(new Font("SansSerif", Font.PLAIN, 11));
        detailsArea.setBackground(new Color(50, 50, 50));
        detailsArea.setForeground(Color.LIGHT_GRAY);
        rightPanel.add(new JScrollPane(detailsArea));
        
        rightPanel.add(Box.createVerticalStrut(20));
        rightPanel.add(new JLabel("Controls:"));
        rightPanel.add(new JLabel("• Middle Click: Rotate"));
        rightPanel.add(new JLabel("• Scroll: Zoom"));
        JButton btn459 = new JButton("Launch 459 Viewer");
        btn459.addActionListener(e -> {
            SwingUtilities.invokeLater(() -> new com.model.rs459.Viewer459());
        });
        rightPanel.add(Box.createVerticalStrut(10));
        rightPanel.add(btn459);
        add(leftPanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
        
        animTimer = new Timer(30, e -> updateAnimation());
        animTimer.start();
        
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                animTimer.stop();
            }
        });
        
        performSearch("");
    }

    private void initBuffers(int w, int h) {
        if (w <= 0) w = 1;
        if (h <= 0) h = 1;
        canvasWidth = w;
        canvasHeight = h;
        // Z-Buffer: stores depth for every pixel
        zBuffer = new float[w * h];
        // Pixel Buffer: managed by BufferedImage
    }

    private void setupMouseListeners() {
        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMouseX = e.getX();
                lastMouseY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isMiddleMouseButton(e) || (SwingUtilities.isLeftMouseButton(e) && e.isShiftDown())) {
                    int dx = e.getX() - lastMouseX;
                    int dy = e.getY() - lastMouseY;

                    // [UPDATED] Invert Horizontal Dragging (Changed from -= to +=)
                    yaw += dx * 4; 
                    
                    // [KEEP] Vertical Dragging (User confirmed this is good)
                    pitch -= dy * 4; 
                    
                    if (pitch > 380) pitch = 380;
                    if (pitch < -380) pitch = -380;

                    lastMouseX = e.getX();
                    lastMouseY = e.getY();
                    renderFrame();
                }
            }
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom -= e.getWheelRotation() * 50;
                if (zoom < 200) zoom = 200;
                if (zoom > 4000) zoom = 4000;
                renderFrame();
            }
        };

        canvasLabel.addMouseListener(ma);
        canvasLabel.addMouseMotionListener(ma);
        canvasLabel.addMouseWheelListener(ma);
    }
    private void performSearch(String query) {
        listModel.clear();
        listModel.addElement("Loading...");
        plugin.getClientThread().invoke(() -> {
            List<String> results = new ArrayList<>();
            String lower = query.toLowerCase();
            for (int i = 0; i < 15000; i++) { 
                NPCComposition def = plugin.getClient().getNpcDefinition(i);
                if (def != null && def.getName() != null && !def.getName().equalsIgnoreCase("null")) {
                     if (lower.isEmpty() || def.getName().toLowerCase().contains(lower)) {
                         results.add("ID: " + i + " | " + def.getName());
                     }
                }
            }
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                for (String s : results) listModel.addElement(s);
            });
        });
    }

    private void parseSelection(String selection) {
        try {
            if (selection.startsWith("ID: ")) {
                int id = Integer.parseInt(selection.split(" ")[1]);
                currentNpcId = id;
                // Auto-load with fresh animation search
                reloadAnimation(false);
            }
        } catch (Exception e) {}
    }

    private void reloadAnimation(boolean manualOverride) {
        if (currentNpcId == -1) return;
        
        plugin.getClientThread().invoke(() -> {
            Client client = plugin.getClient();
            NPCComposition def = client.getNpcDefinition(currentNpcId);
            if (def == null) return;

            final int combatLevel = def.getCombatLevel();
            final String[] actions = def.getActions();
            
            SwingUtilities.invokeLater(() -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Combat Lvl: ").append(combatLevel).append("\n\n");
                sb.append("Options:\n");
                if (actions != null) {
                    for (String action : actions) {
                        if (action != null) {
                            sb.append("• ").append(action).append("\n");
                        }
                    }
                }
                detailsArea.setText(sb.toString());
            });
            int animId = -1;
            
            // 1. Manual Input (Only used if Manual Override is TRUE)
            if (manualOverride) {
                try {
                    String txt = animIdField.getText().trim();
                    if (!txt.isEmpty()) animId = Integer.parseInt(txt);
                } catch (NumberFormatException e) { animId = -1; }
            }

            // 2. Auto-Detect (If NOT manual override, or if manual input failed/was -1)
            if (!manualOverride || animId == -1) {
                animId = findIdleAnimation(def);
                final int foundId = animId;
                SwingUtilities.invokeLater(() -> animIdField.setText(String.valueOf(foundId)));
            }

            // 3. Load Animation
            if (animId >= 0) { // Changed > 0 to >= 0 to allow ID 0 if valid
                currentAnimation = client.loadAnimation(animId);
            } else {
                currentAnimation = null;
            }

            // 4. Load Model
            int[] modelIds = def.getModels();
            if (modelIds == null) return;

            ModelData[] parts = new ModelData[modelIds.length];
            for (int i = 0; i < modelIds.length; i++) {
                parts[i] = client.loadModelData(modelIds[i]);
            }
            ModelData modelData = client.mergeModels(parts);

            if (modelData != null) {
                short[] find = def.getColorToReplace();
                short[] replace = def.getColorToReplaceWith();
                if (find != null && replace != null) {
                    modelData.cloneColors();
                    for (int i = 0; i < find.length; i++) {
                        modelData.recolor(find[i], replace[i]);
                    }
                }
                
                this.baseModelData = modelData;
                this.animFrame = 0;
                this.animCycle = 0;
                
                net.runelite.api.Model model = modelData.light(64, 850, -30, -50, -30);
                
                SwingUtilities.invokeLater(() -> {
                    this.currentModel = model;
                    renderFrame();
                });
            }
        });
    }
    
    private int findIdleAnimation(NPCComposition def) {
        // 1. Try public API method
        try {
            Method m = def.getClass().getMethod("getIdlePoseAnimation");
            return (int) m.invoke(def);
        } catch (Exception e) {}

        // 2. Reflection: Check common internal field names
        String[] fieldNames = {
            "idleSequence",      // Standard RuneLite mixin field
            "standingAnimation", // Common alternate name
            "standAnimationId",  // Older naming convention
            "idleAnimation"      // Generic fallback
        };

        for (String fieldName : fieldNames) {
            try {
                Field f = def.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                int id = f.getInt(def);
                if (id != -1) return id; // Return first valid ID found
            } catch (Exception e) {
                // Field not found, continue to next candidate
            }
        }

        return -1; // Could not find animation in cache
    }    
    
    private void updateAnimation() {
        if (baseModelData == null || currentAnimation == null) return;
        
        plugin.getClientThread().invoke(() -> {
             animCycle++;
             int[] durations = currentAnimation.getFrameLengths();
             int numFrames = currentAnimation.getNumFrames(); 
             
             if (durations != null && numFrames > 0) {
                 int duration = (animFrame < durations.length) ? durations[animFrame] : 1;
                 if (animCycle > duration) {
                     animCycle = 0;
                     animFrame++;
                     if (animFrame >= numFrames) animFrame = 0;
                     
                     ModelData frameData = baseModelData.cloneVertices(); // Clone to prevent permanent twisting
                     net.runelite.api.Model rawModel = frameData.light(64, 850, -30, -50, -30);
                     net.runelite.api.Model animatedModel = plugin.getClient().applyTransformations(rawModel, currentAnimation, animFrame, null, 0);
                     
                     SwingUtilities.invokeLater(() -> {
                         this.currentModel = animatedModel;
                         renderFrame();
                     });
                 }
             }
        });
    }

    // ==========================================================
    //  PIXEL-PERFECT SOFTWARE RASTERIZER (Z-BUFFERED)
    // ==========================================================
    private void renderFrame() {
        if (currentModel == null || canvasWidth == 0 || canvasHeight == 0) return;

        BufferedImage image = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        
        // Clear Buffers
        Arrays.fill(zBuffer, Float.MAX_VALUE);
        Arrays.fill(pixels, 0xFF282828); // Dark Grey Background

        // Center Point
        int cx = canvasWidth / 2;
        int cy = canvasHeight / 2 + 150; 

        // Get Model Data
        float[] xVerts = currentModel.getVerticesX();
        float[] yVerts = currentModel.getVerticesY();
        float[] zVerts = currentModel.getVerticesZ();
        int[] indices1 = currentModel.getFaceIndices1();
        int[] indices2 = currentModel.getFaceIndices2();
        int[] indices3 = currentModel.getFaceIndices3();
        int[] faceColors = currentModel.getFaceColors1();
        byte[] facePriorities = currentModel.getFaceRenderPriorities(); 

        // Rotation Math
        double angY = (yaw / 2048.0) * Math.PI * 2;
        double sinY = Math.sin(angY);
        double cosY = Math.cos(angY);
        double angP = (pitch / 2048.0) * Math.PI * 2;
        double sinP = Math.sin(angP);
        double cosP = Math.cos(angP);

        // Rasterize Every Triangle
        for (int i = 0; i < currentModel.getFaceCount(); i++) {
            int i1 = indices1[i];
            int i2 = indices2[i];
            int i3 = indices3[i];

            // Rotate
            float[] v1 = rotate(xVerts[i1], yVerts[i1], zVerts[i1], sinY, cosY, sinP, cosP);
            float[] v2 = rotate(xVerts[i2], yVerts[i2], zVerts[i2], sinY, cosY, sinP, cosP);
            float[] v3 = rotate(xVerts[i3], yVerts[i3], zVerts[i3], sinY, cosY, sinP, cosP);

            // Project
            Point3D p1 = project(v1, cx, cy);
            Point3D p2 = project(v2, cx, cy);
            Point3D p3 = project(v3, cx, cy);

            if (p1 != null && p2 != null && p3 != null) {
                // Determine Color
                int hsl = (faceColors != null && i < faceColors.length) ? faceColors[i] : 0;
                int color = rs2ColorToInt(hsl);
                
                // Draw Triangle to Buffer
                drawTriangle(pixels, zBuffer, canvasWidth, canvasHeight, p1, p2, p3, color);
            }
        }

        canvasLabel.setIcon(new ImageIcon(image));
        canvasLabel.setText(""); 
    }

    private void drawTriangle(int[] pixels, float[] zBuf, int w, int h, Point3D v1, Point3D v2, Point3D v3, int color) {
        // Standard Scanline Rasterization
        // Sort vertices by Y
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

            int startX = (int) A.x;
            int endX   = (int) B.x;
            
            float zStart = A.z;
            float zEnd   = B.z;
            float dx = (endX - startX);

            if (startX < 0) startX = 0;
            if (endX >= w) endX = w - 1;

            for (int x = startX; x <= endX; x++) {
                // Interpolate Z
                float phi = (dx == 0) ? 1.0f : (x - A.x) / dx;
                float z = zStart + (zEnd - zStart) * phi;
                
                int idx = x + y * w;
                if (z < zBuf[idx]) {
                    zBuf[idx] = z;
                    pixels[idx] = color;
                }
            }
        }
    }

    private float[] rotate(float x, float y, float z, double sinY, double cosY, double sinP, double cosP) {
        float rx = (float) ((x * cosY) - (z * sinY));
        float rz = (float) ((x * sinY) + (z * cosY));
        float ry = y;
        // Pitch
        float ry2 = (float) ((ry * cosP) + (rz * sinP)); 
        float rz2 = (float) ((rz * cosP) - (ry * sinP)); 
        return new float[]{rx, -ry2, rz2}; // -ry2 to flip for screen coords
    }

    private Point3D project(float[] v, int offX, int offY) {
        float x = v[0];
        float y = v[1];
        float z = v[2];
        z += 1500; 
        if (z <= 50) return null; 
        float screenX = offX + (x * zoom) / z;
        float screenY = offY - (y * zoom) / z; 
        return new Point3D(screenX, screenY, z);
    }
    
    private static int rs2ColorToInt(int hsl) {
        if (hsl <= 0) return 0xFF000000;
        int hue = (hsl >> 10) & 0x3f;
        int sat = (hsl >> 7) & 0x07;
        int lum = (hsl & 0x7f);
        return HSLtoRGB(hue / 63.0f, sat / 7.0f, lum / 127.0f);
    }

    private static int HSLtoRGB(float h, float s, float l) {
        float r, g, b;
        if (s == 0f) {
            r = g = b = l;
        } else {
            float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
            float p = 2 * l - q;
            r = hueToRgb(p, q, h + 1f/3f);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1f/3f);
        }
        return (0xFF << 24) | ((int)(clamp(r)*255) << 16) | ((int)(clamp(g)*255) << 8) | (int)(clamp(b)*255);
    }

    private static float hueToRgb(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f/6f) return p + (q - p) * 6f * t;
        if (t < 1f/2f) return q;
        if (t < 2f/3f) return p + (q - p) * (2f/3f - t) * 6f;
        return p;
    }

    private static float clamp(float v) { return Math.max(0, Math.min(1, v)); }

    // Helper Class
    private static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        Point3D add(Point3D o) { return new Point3D(x+o.x, y+o.y, z+o.z); }
        Point3D subtract(Point3D o) { return new Point3D(x-o.x, y-o.y, z-o.z); }
        Point3D scale(float s) { return new Point3D(x*s, y*s, z*s); }
    }
}