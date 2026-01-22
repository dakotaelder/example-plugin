package src.main.java.com.model.comparer;

import com.model.rs317.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class RenderPanel317 extends JPanel {

    private final RS317Decompressor modelDecompressor;
    private JLabel canvas;
    private int[] pixels;
    private float[] zBuffer;
    private RS317Model currentModel;
    
    // [FIX] Single Thread Executor to prevent "Thread Explosion" and File Lock crashes
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private volatile int pendingNpcId = -1; // Only load the latest request
    
    private int yaw = 0, pitch = 128, zoom = 600;
    private int lastX, lastY;

    public RenderPanel317(RS317Decompressor decompressor) {
        this.modelDecompressor = decompressor;
        setLayout(new BorderLayout());
        setBackground(Color.BLACK);
        canvas = new JLabel("317 Model View", SwingConstants.CENTER);
        canvas.setOpaque(true);
        canvas.setBackground(new Color(20, 20, 20));
        add(canvas, BorderLayout.CENTER);
        setupMouseControls();
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { initRaster(); render(); }
        });
    }

    public void loadNpc(RS317Npc def) {
        if (def == null || def.models == null || modelDecompressor == null) {
            currentModel = null;
            render();
            return;
        }
        
        // [FIX] Debounce: Update ID, submit task. Task checks if it's still the latest ID.
        pendingNpcId = def.id;
        
        loader.submit(() -> {
            if (pendingNpcId != def.id) return; // Skip if newer request exists
            
            try {
                // Synchronize file access strictly
                synchronized (modelDecompressor) {
                    System.out.println("[DEBUG] Loading 317 Models for NPC: " + def.id);
                    RS317Model[] parts = new RS317Model[def.models.length];
                    boolean error = false;
                    
                    for (int i = 0; i < def.models.length; i++) {
                        byte[] raw = modelDecompressor.decompress(def.models[i]);
                        if (raw == null) { error = true; continue; }
                        
                        if (raw.length > 2 && raw[0] == 0x1F && raw[1] == (byte)0x8B) {
                            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                                raw = gzip.readAllBytes();
                            }
                        }
                        try { parts[i] = new RS317Model(raw); } catch(Exception e) { error = true; }
                    }
                    
                    if (!error && pendingNpcId == def.id) { // Check ID again before rendering
                        RS317Model merged = (parts.length == 1) ? parts[0] : new RS317Model(parts);
                        if (def.originalColors != null && def.modifiedColors != null) {
                            for(int i=0; i<def.originalColors.length; i++) {
                                merged.recolor(def.originalColors[i], def.modifiedColors[i]);
                            }
                        }
                        merged.applyLighting(120, 2000, -50, -30, -50);
                        
                        SwingUtilities.invokeLater(() -> {
                            currentModel = merged;
                            render();
                        });
                    }
                }
            } catch (Exception e) { 
                System.err.println("[ERROR] 317 Load Fail: " + e.getMessage());
            }
        });
    }

    private void initRaster() {
        int w = getWidth(); int h = getHeight();
        if (w > 0 && h > 0) { pixels = new int[w * h]; zBuffer = new float[w * h]; }
    }

    private void render() {
        if (pixels == null) initRaster();
        if (getWidth() <= 0 || getHeight() <= 0) return;
        Arrays.fill(pixels, 0xFF141414);
        Arrays.fill(zBuffer, Float.MAX_VALUE);

        if (currentModel != null) {
            int cx = getWidth() / 2; int cy = getHeight() / 2 + 50;
            double cY = Math.cos((yaw + 1024) * 0.0030679615); 
            double sY = Math.sin((yaw + 1024) * 0.0030679615);
            double cP = Math.cos(pitch * 0.0030679615);
            double sP = Math.sin(pitch * 0.0030679615);
            int midY = (currentModel.minY + currentModel.maxY) / 2;

            int[][] p = new int[currentModel.vertexCount][3];
            boolean[] valid = new boolean[currentModel.vertexCount];

            for (int i = 0; i < currentModel.vertexCount; i++) {
                int x = currentModel.verticesX[i];
                int y = currentModel.verticesY[i] - midY;
                int z = currentModel.verticesZ[i];
                int t = (int) (x * cY - z * sY); z = (int) (x * sY + z * cY); x = t;
                t = (int) (y * cP - z * sP); z = (int) (y * sP + z * cP); y = t;
                z += 420; 
                if (z < 20) continue; 
                p[i][0] = cx + (x * zoom) / z;
                p[i][1] = cy + (y * zoom) / z; 
                p[i][2] = z;
                valid[i] = true;
            }

            for (int i = 0; i < currentModel.faceCount; i++) {
                int i1 = currentModel.faceIndices1[i];
                int i2 = currentModel.faceIndices2[i];
                int i3 = currentModel.faceIndices3[i];
                if (i1 >= 0 && i2 >= 0 && i3 >= 0 && 
                    i1 < currentModel.vertexCount && i2 < currentModel.vertexCount && i3 < currentModel.vertexCount &&
                    valid[i1] && valid[i2] && valid[i3]) {
                    rasterize(p[i1][0], p[i1][1], (float)p[i1][2], 
                              p[i2][0], p[i2][1], (float)p[i2][2], 
                              p[i3][0], p[i3][1], (float)p[i3][2], 
                              rs2Color(currentModel.faceColors[i]));
                }
            }
        }
        BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        System.arraycopy(pixels, 0, ((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0, pixels.length);
        canvas.setIcon(new ImageIcon(img));
    }

    private void rasterize(int x1, int y1, float z1, int x2, int y2, float z2, int x3, int y3, float z3, int c) {
        if (y1 > y2) { int t=x1; x1=x2; x2=t; t=y1; y1=y2; y2=t; float f=z1; z1=z2; z2=f; }
        if (y1 > y3) { int t=x1; x1=x3; x3=t; t=y1; y1=y3; y3=t; float f=z1; z1=z3; z3=f; }
        if (y2 > y3) { int t=x2; x2=x3; x3=t; t=y2; y2=y3; y3=t; float f=z2; z2=z3; z3=f; }
        int th = y3 - y1; if (th == 0) return;
        int cw = getWidth(); int ch = getHeight();
        for (int i = 0; i < th; i++) {
            boolean side = i > y2 - y1 || y2 == y1;
            int sh = side ? y3 - y2 : y2 - y1;
            float a = (float) i / th, b = (float) (i - (side ? y2 - y1 : 0)) / sh;
            int ax = x1 + (int) ((x3 - x1) * a), bx = side ? x2 + (int) ((x3 - x2) * b) : x1 + (int) ((x2 - x1) * b);
            float az = z1 + (z3 - z1) * a, bz = side ? z2 + (z3 - z2) * b : z1 + (z2 - z1) * b;
            if (ax > bx) { int t=ax; ax=bx; bx=t; float f=az; az=bz; bz=f; }
            int y = y1 + i; if (y < 0 || y >= ch) continue;
            float dz = (bz - az) / (bx - ax == 0 ? 1 : bx - ax), cz = az;
            for (int x = ax; x < bx; x++) {
                if (x >= 0 && x < cw) {
                    int idx = x + y * cw;
                    if (cz < zBuffer[idx]) { zBuffer[idx] = cz; pixels[idx] = c; }
                }
                cz += dz;
            }
        }
    }

    private void setupMouseControls() {
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { lastX = e.getX(); lastY = e.getY(); }
            public void mouseDragged(MouseEvent e) {
                yaw += (e.getX() - lastX) * 2; 
                pitch += (e.getY() - lastY) * 2;
                lastX = e.getX(); lastY = e.getY(); render();
            }
            public void mouseWheelMoved(MouseWheelEvent e) { zoom -= e.getWheelRotation() * 50; render(); }
        };
        canvas.addMouseListener(ma); canvas.addMouseMotionListener(ma); canvas.addMouseWheelListener(ma);
    }

    private int rs2Color(int hsl) {
        if (hsl <= 0) return 0xFF555555;
        int h = (hsl >> 10) & 0x3F, s = (hsl >> 7) & 0x07, l = hsl & 0x7F;
        return Color.HSBtoRGB(h / 63.0f, s / 7.0f, l / 127.0f);
    }
}