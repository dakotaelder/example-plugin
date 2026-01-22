package com.model.rs459;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.util.Arrays;

public class Viewer459 extends JFrame {

    private RS459Cache cache;
    private ConfigArchive npcArchive; 
    
    private JLabel canvas;
    private JTextArea details;
    private JComboBox<String> modeBox;
    private DefaultListModel<String> listModel;
    
    private int[] pixels;
    private float[] zBuffer;
    private RS459Model currentModel;
    
    private int yaw = 0, pitch = 0, zoom = 600;
    private int lastX, lastY;
    private boolean isNpcMode = true;

    // PATH (Verify this matches your folder)
    private static final String CACHE_PATH = "C:\\Users\\dakot\\Server_Workspace\\RuneScape_Server\\data\\cache_459\\.jagex_cache_32\\runescape\\";

    public Viewer459() {
        setTitle("RS 459 Viewer - Integrated");
        setSize(1100, 700);
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        try {
            cache = new RS459Cache(CACHE_PATH);
        } catch (Exception e) { 
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Cache Error: " + e.getMessage());
        }

        // Sidebar
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(260, 0));
        
        JPanel controls = new JPanel(new GridLayout(0, 1));
        modeBox = new JComboBox<>(new String[]{"Raw Models (Idx 1)", "NPCs (Idx 2.9)"});
        modeBox.setSelectedIndex(1);
        modeBox.addActionListener(e -> {
            isNpcMode = modeBox.getSelectedIndex() == 1;
            refreshList();
        });
        controls.add(modeBox);
        sidebar.add(controls, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        JList<String> list = new JList<>(listModel);
        
        // [CRITICAL] This listener calls load(id), which calls loadNpc(id)
        list.addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting() && list.getSelectedIndex() != -1) {
                String val = list.getSelectedValue();
                try {
                    int id = Integer.parseInt(val.split(" ")[1]);
                    load(id); 
                } catch (Exception ex) {}
            }
        });
        sidebar.add(new JScrollPane(list), BorderLayout.CENTER);
        
        details = new JTextArea(15, 15);
        sidebar.add(new JScrollPane(details), BorderLayout.SOUTH);
        add(sidebar, BorderLayout.WEST);

        canvas = new JLabel("Loading...", SwingConstants.CENTER);
        canvas.setBackground(Color.DARK_GRAY);
        canvas.setOpaque(true);
        add(canvas, BorderLayout.CENTER);

        setupMouse();
        canvas.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) {
                initRaster();
                render();
            }
        });
        
        setVisible(true);
        SwingUtilities.invokeLater(this::loadNpcArchive);
    }
    
    private void loadNpcArchive() {
        if (cache == null) return;
        new Thread(() -> {
            byte[] data = cache.readArchive(2, 9);
            SwingUtilities.invokeLater(() -> {
                if(data != null) {
                    npcArchive = new ConfigArchive(data);
                    refreshList();
                    details.setText("Loaded NPC Configs.\nBytes: " + data.length + "\nFile Count: " + npcArchive.getEntryCount());
                    canvas.setText("Select an NPC");
                } else {
                    details.setText("Failed to load NPC Archive 2.9\n(Check Cache Path)");
                }
            });
        }).start();
    }

    private void refreshList() {
        listModel.clear();
        new Thread(() -> {
            int max = isNpcMode ? (npcArchive != null ? npcArchive.getEntryCount() : 0) : 30000;
            final int count = Math.min(max, 10000);
            
            SwingUtilities.invokeLater(() -> {
                listModel.clear();
                String prefix = isNpcMode ? "NPC " : "Model ";
                for(int i=0; i<count; i++) listModel.addElement(prefix + i);
            });
        }).start();
    }

    // [CRITICAL] This connects the UI to the loading logic
    private void load(int id) {
        if(isNpcMode) {
            loadNpc(id);
        } else {
            loadModel(id);
        }
    }
    
    private void loadNpc(int id) {
        if (npcArchive == null) return;
        byte[] data = npcArchive.getFile(id);
        if (data == null) return;
        try {
            NpcDefinition def = new NpcDefinition(id, data);
            details.setText("NPC: " + def.name + "\nCombat: " + def.combatLevel + "\nModels: " + Arrays.toString(def.modelIds));
            
            if (def.modelIds != null && def.modelIds.length > 0) {
                loadModel(def.modelIds[0]);
            } else { 
                currentModel = null; 
                render(); 
            }
        } catch (Exception e) { details.setText("Error: " + e.getMessage()); }
    }

    private void loadModel(int id) {
        if(cache == null) return;
        byte[] data = cache.readArchive(1, id); 
        
        // Check for null to avoid crashing on corrupt files
        if(data != null && data.length > 0) {
            try {
                currentModel = new RS459Model(data);
                render();
            } catch (Exception e) {
                details.append("\nModel Load Error");
            }
        } else {
            currentModel = null;
            render();
        }
    }
    
    private void initRaster() {
        int w = canvas.getWidth(); int h = canvas.getHeight();
        if(w>0 && h>0) { pixels = new int[w*h]; zBuffer = new float[w*h]; }
    }

    private void render() {
        if(pixels == null || currentModel == null) {
            if(pixels != null) Arrays.fill(pixels, 0xFF333333);
            if(canvas.getWidth()>0 && canvas.getHeight()>0) {
                 BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
                 if(pixels != null) {
                    int[] r = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
                    System.arraycopy(pixels, 0, r, 0, pixels.length);
                 }
                 canvas.setIcon(new ImageIcon(img));
            }
            return;
        }
        Arrays.fill(pixels, 0xFF333333);
        Arrays.fill(zBuffer, Float.MAX_VALUE);
        
        int w = canvas.getWidth(); int h = canvas.getHeight();
        int cx = w/2; int cy = h/2 + 50;
        double angY = (yaw / 2048.0) * Math.PI * 2;
        double angP = (pitch / 2048.0) * Math.PI * 2;
        double sinY = Math.sin(angY), cosY = Math.cos(angY);
        double sinP = Math.sin(angP), cosP = Math.cos(angP);
        
        int[] vx = currentModel.verticesX; int[] vy = currentModel.verticesY; int[] vz = currentModel.verticesZ;
        for(int i=0; i<currentModel.faceCount; i++) {
             int i1 = currentModel.indices1[i]; int i2 = currentModel.indices2[i]; int i3 = currentModel.indices3[i];
             if (i1<0||i2<0||i3<0||i1>=vx.length||i2>=vx.length||i3>=vx.length) continue;
             float[] v1 = transform(vx[i1], vy[i1], vz[i1], sinY, cosY, sinP, cosP);
             float[] v2 = transform(vx[i2], vy[i2], vz[i2], sinY, cosY, sinP, cosP);
             float[] v3 = transform(vx[i3], vy[i3], vz[i3], sinY, cosY, sinP, cosP);
             Point p1 = project(v1, cx, cy); Point p2 = project(v2, cx, cy); Point p3 = project(v3, cx, cy);
             if(p1!=null&&p2!=null&&p3!=null) drawTri(pixels, zBuffer, w, h, p1, p2, p3, v1[2], v2[2], v3[2], rs2Color(currentModel.faceColors[i]));
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        int[] r = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();
        System.arraycopy(pixels, 0, r, 0, pixels.length);
        canvas.setIcon(new ImageIcon(img));
    }
    
    private float[] transform(int x, int y, int z, double sinY, double cosY, double sinP, double cosP) {
        float rx = (float)(x * cosY - z * sinY); float rz = (float)(x * sinY + z * cosY);
        float ry = y; float ry2 = (float)(ry * cosP - rz * sinP); float rz2 = (float)(ry * sinP + rz * cosP);
        return new float[]{rx, -ry2, rz2};
    }
    private Point project(float[] v, int cx, int cy) {
        float z = v[2] + 2500; if(z <= 50) return null;
        return new Point(cx + (int)((v[0] * zoom) / z), cy - (int)((v[1] * zoom) / z));
    }
    private void drawTri(int[] p, float[] z, int w, int h, Point p1, Point p2, Point p3, float z1, float z2, float z3, int c) {
        if (p1.y > p2.y) { Point t=p1; p1=p2; p2=t; float tz=z1; z1=z2; z2=tz; }
        if (p1.y > p3.y) { Point t=p1; p1=p3; p3=t; float tz=z1; z1=z3; z3=tz; }
        if (p2.y > p3.y) { Point t=p2; p2=p3; p3=t; float tz=z2; z2=z3; z3=tz; }
        int total = p3.y - p1.y;
        for (int i = 0; i < total; i++) {
            boolean sec = i > p2.y - p1.y || p2.y == p1.y;
            int seg = sec ? p3.y - p2.y : p2.y - p1.y;
            float alpha = (float) i / total;
            float beta  = (float) (i - (sec ? p2.y - p1.y : 0)) / seg; 
            Point A = add(p1, scale(sub(p3, p1), alpha));
            Point B = sec ? add(p2, scale(sub(p3, p2), beta)) : add(p1, scale(sub(p2, p1), beta));
            float zA = z1 + (z3 - z1) * alpha; float zB = sec ? z2 + (z3 - z2) * beta : z1 + (z2 - z1) * beta;
            if (A.x > B.x) { Point t=A; A=B; B=t; float tz=zA; zA=zB; zB=tz; }
            int y = p1.y + i; if (y < 0 || y >= h) continue;
            int sx = Math.max(0, A.x); int ex = Math.min(w-1, B.x);
            float dz = (zB - zA) / (B.x - A.x == 0 ? 1 : B.x - A.x); float cz = zA + (sx - A.x) * dz;
            for (int x = sx; x <= ex; x++) {
                int idx = x + y * w; if (cz < z[idx]) { z[idx] = cz; p[idx] = c; } cz += dz;
            }
        }
    }
    private Point add(Point a, Point b) { return new Point(a.x+b.x, a.y+b.y); }
    private Point sub(Point a, Point b) { return new Point(a.x-b.x, a.y-b.y); }
    private Point scale(Point a, float s) { return new Point((int)(a.x*s), (int)(a.y*s)); }
    private void setupMouse() {
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { lastX = e.getX(); lastY = e.getY(); }
            public void mouseDragged(MouseEvent e) {
                 if(SwingUtilities.isMiddleMouseButton(e) || SwingUtilities.isLeftMouseButton(e)) { yaw += (e.getX()-lastX)*4; pitch += (e.getY()-lastY)*4; lastX=e.getX(); lastY=e.getY(); render(); }
            }
            public void mouseWheelMoved(MouseWheelEvent e) { zoom -= e.getWheelRotation()*50; render(); }
        };
        canvas.addMouseListener(ma); canvas.addMouseMotionListener(ma); canvas.addMouseWheelListener(ma);
    }
    private int rs2Color(int hsl) {
        if(hsl <= 0) return 0xFF555555;
        int h = (hsl >> 10) & 0x3F; int s = (hsl >> 7) & 0x07; int l = hsl & 0x7F;
        return Color.HSBtoRGB(h/63.0f, s/7.0f, l/127.0f);
    }
}