package com.model.rs317;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class Viewer317 extends JFrame {

    private static final String CACHE_PATH = "\data\\cache_372\\";

    private RS317Decompressor configDecompressor;
    private RS317Decompressor modelDecompressor;
    private Map<Integer, RS317Npc> npcDefinitions = new HashMap<>();
    
    private DefaultListModel<String> listModel = new DefaultListModel<>();
    private JList<String> npcList;
    private JTextArea detailsArea;
    private JLabel canvas;
    
    private JTextField searchField;
    private List<String> allListItems = new ArrayList<>();
    
    // Animation
    private int currentAnimId = -1;
    private int currentFrame = 0;
    private int frameDelay = 0;
    private Timer animTimer;
    
    private int[] pixels;
    private float[] zBuffer;
    private RS317Model currentModel;
    private RS317Model baseModel;
    private RS317Npc currentDef;
    
    private int yaw = 0, pitch = 128, zoom = 600;
    private int lastX, lastY;

    public Viewer317() {
        setTitle("317 NPC Viewer (Animated Fixed)");
        setSize(1100, 750);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(280, 0));
        
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(); }
            public void removeUpdate(DocumentEvent e) { filterList(); }
            public void changedUpdate(DocumentEvent e) { filterList(); }
        });
        searchPanel.add(new JLabel(" Search: "), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        sidebar.add(searchPanel, BorderLayout.NORTH);
        
        npcList = new JList<>(listModel);
        npcList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadNpc(npcList.getSelectedValue());
        });
        sidebar.add(new JScrollPane(npcList), BorderLayout.CENTER);
        
        JPanel bottomPanel = new JPanel(new BorderLayout());
        detailsArea = new JTextArea(10, 20);
        detailsArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        detailsArea.setEditable(false);
        bottomPanel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);
        
        JPanel animControls = new JPanel(new FlowLayout());
        JButton btnStand = new JButton("Stand");
        JButton btnWalk = new JButton("Walk");
        btnStand.addActionListener(e -> setAnimation(currentDef != null ? currentDef.standAnim : -1));
        btnWalk.addActionListener(e -> setAnimation(currentDef != null ? currentDef.walkAnim : -1));
        animControls.add(btnStand);
        animControls.add(btnWalk);
        bottomPanel.add(animControls, BorderLayout.SOUTH);
        
        sidebar.add(bottomPanel, BorderLayout.SOUTH);
        add(sidebar, BorderLayout.WEST);

        canvas = new JLabel("Loading...", SwingConstants.CENTER);
        canvas.setBackground(new Color(20, 20, 20));
        canvas.setOpaque(true);
        setupMouseControls();
        canvas.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { initRaster(); render(); }
        });
        add(canvas, BorderLayout.CENTER);

        animTimer = new Timer(30, e -> updateAnimation());
        animTimer.start();

        new Thread(this::initCache).start();
    }

    private void filterList() {
        String query = searchField.getText().toLowerCase();
        listModel.clear();
        for (String s : allListItems) {
            if (query.isEmpty() || s.toLowerCase().contains(query)) listModel.addElement(s);
        }
    }
    
    private void setAnimation(int animId) {
        if (animId == -1) {
            currentAnimId = -1;
            if (currentModel != null) currentModel.reset(); // Snap to T-Pose
            render();
            return;
        }
        currentAnimId = animId;
        currentFrame = 0;
        frameDelay = 0;
    }
    
    private void updateAnimation() {
        if (currentAnimId == -1 || currentModel == null) return;
        
        RS317Sequence seq = RS317Sequence.sequences.get(currentAnimId);
        if (seq == null || seq.frameIDs == null) return;
        
        frameDelay++;
        if (frameDelay > seq.frameDurations[currentFrame]) {
            frameDelay = 0;
            currentFrame++;
            if (currentFrame >= seq.frameCount) {
                currentFrame = 0;
            }
            int frameId = seq.frameIDs[currentFrame];
            currentModel.animate(frameId); // Uses new reset() logic inside
            render();
        }
    }

    private void initCache() {
        try {
            File path = new File(CACHE_PATH);
            if (!path.exists()) { updateStatus("Cache path missing."); return; }
            RandomAccessFile data = new RandomAccessFile(new File(path, "main_file_cache.dat"), "r");
            RandomAccessFile idx0 = new RandomAccessFile(new File(path, "main_file_cache.idx0"), "r");
            RandomAccessFile idx1 = new RandomAccessFile(new File(path, "main_file_cache.idx1"), "r");

            configDecompressor = new RS317Decompressor(data, idx0, 0);
            modelDecompressor = new RS317Decompressor(data, idx1, 1);

            byte[] frameData = configDecompressor.decompress(1);
            if (frameData != null) {
                RS317Frame.load(frameData);
                updateStatus("Loaded Animation Frames.");
            }

            byte[] configRaw = configDecompressor.decompress(2);
            RS317Archive archive = new RS317Archive(configRaw);
            RS317Sequence.load(archive);
            
            byte[] npcDat = archive.getFile("npc.dat");
            byte[] npcIdx = archive.getFile("npc.idx");
            
            RS317Stream idxStream = new RS317Stream(npcIdx);
            RS317Stream datStream = new RS317Stream(npcDat);
            int count = idxStream.readUnsignedWord();
            
            int offset = 2;
            for (int i = 0; i < count; i++) {
                datStream.offset = offset;
                try {
                    npcDefinitions.put(i, RS317Npc.decode(i, datStream));
                } catch(Exception e) {}
                offset += idxStream.readUnsignedWord();
            }

            SwingUtilities.invokeLater(() -> {
                allListItems.clear();
                npcDefinitions.forEach((id, def) -> {
                    if (def.name != null) allListItems.add(id + ": " + def.name);
                });
                filterList();
                canvas.setText("");
                updateStatus("Loaded " + npcDefinitions.size() + " NPCs.");
            });
        } catch (Exception e) {
            updateStatus("Init Error: " + e.getMessage());
        }
    }

    private void loadNpc(String selection) {
        if (selection == null) return;
        new Thread(() -> {
            try {
                int id = Integer.parseInt(selection.split(":")[0]);
                currentDef = npcDefinitions.get(id);
                
                StringBuilder sb = new StringBuilder();
                sb.append("ID: ").append(id).append("\n");
                sb.append("Name: ").append(currentDef.name).append("\n");
                sb.append("Combat: ").append(currentDef.combatLevel).append("\n");
                sb.append("Stand: ").append(currentDef.standAnim).append("\n");
                sb.append("Walk: ").append(currentDef.walkAnim).append("\n");
                sb.append("Options: ");
                if (currentDef.actions != null) {
                    for (String act : currentDef.actions) {
                        if (act != null) sb.append("[").append(act).append("] ");
                    }
                }
                SwingUtilities.invokeLater(() -> detailsArea.setText(sb.toString()));

                if (currentDef.models != null) {
                    RS317Model[] parts = new RS317Model[currentDef.models.length];
                    boolean hasError = false;
                    for (int i = 0; i < currentDef.models.length; i++) {
                        byte[] raw = modelDecompressor.decompress(currentDef.models[i]);
                        if (raw == null) { hasError = true; continue; }
                        byte[] data = ungzip(raw);
                        try { parts[i] = new RS317Model(data); } catch (Exception e) { hasError = true; }
                    }
                    
                    if (!hasError) {
                        RS317Model merged = (parts.length == 1) ? parts[0] : new RS317Model(parts);
                        if (currentDef.originalColors != null && currentDef.modifiedColors != null) {
                            for(int i=0; i<currentDef.originalColors.length; i++) {
                                merged.recolor(currentDef.originalColors[i], currentDef.modifiedColors[i]);
                            }
                        }
                        merged.applyLighting(120, 2000, -50, -30, -50);
                        
                        SwingUtilities.invokeLater(() -> {
                            currentModel = merged;
                            if (currentDef.standAnim != -1) setAnimation(currentDef.standAnim);
                            render();
                        });
                    }
                }
            } catch (Exception e) {
                updateStatus("Load Error: " + e.getMessage());
            }
        }).start();
    }
    private byte[] ungzip(byte[] data) {
        if (data == null || data.length < 2) return data;
        if (data[0] == (byte)0x1F && data[1] == (byte)0x8B) {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return gzip.readAllBytes();
            } catch (Exception e) { return data; }
        }
        return data;
    }

    private void initRaster() {
        int w = canvas.getWidth(); int h = canvas.getHeight();
        if (w > 0 && h > 0) { pixels = new int[w * h]; zBuffer = new float[w * h]; }
    }

    private void render() {
        if (pixels == null) initRaster();
        Arrays.fill(pixels, 0xFF141414);
        Arrays.fill(zBuffer, Float.MAX_VALUE);

        if (currentModel != null) {
            int cx = canvas.getWidth() / 2, cy = canvas.getHeight() / 2 + 50;
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
                if (z < 10) continue; 
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
        BufferedImage img = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_ARGB);
        System.arraycopy(pixels, 0, ((DataBufferInt) img.getRaster().getDataBuffer()).getData(), 0, pixels.length);
        canvas.setIcon(new ImageIcon(img));
    }

    private void rasterize(int x1, int y1, float z1, int x2, int y2, float z2, int x3, int y3, float z3, int c) {
        if (y1 > y2) { int t=x1; x1=x2; x2=t; t=y1; y1=y2; y2=t; float f=z1; z1=z2; z2=f; }
        if (y1 > y3) { int t=x1; x1=x3; x3=t; t=y1; y1=y3; y3=t; float f=z1; z1=z3; z3=f; }
        if (y2 > y3) { int t=x2; x2=x3; x3=t; t=y2; y2=y3; y3=t; float f=z2; z2=z3; z3=f; }
        int th = y3 - y1; if (th == 0) return;
        int cw = canvas.getWidth(), ch = canvas.getHeight();
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

    private void updateStatus(String msg) { SwingUtilities.invokeLater(() -> detailsArea.append(msg + "\n")); }
    
}
