package com.dakota;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GraphEditor extends JPanel
{
    private final List<Node> nodes;
    private Node selectedNode;
    private final ExamplePlugin plugin;

    private double scale = 1.0;
    private int offsetX = 0, offsetY = 0;
    private Point dragStart;
    private Node draggingNode;
    
    private Node connSource;
    private int connIndex;
    private Point mousePos;
    public int npcIdForSave = -1;
    public String npcNameForSave = "";
    public Node currentNode = null; // Track the active node per-tab
    public boolean isLocationSpecific = true; // Checked by default
    public int dialogueLocX = -1;
    public int dialogueLocY = -1;
    public int dialogueHeight = -1;
    public JCheckBox locCheckBox; // Public so we can set it during load
    public List<Node> getNodes() { return nodes; }
    private JPanel inspectorPanel;    private JPanel simulatorPanel;
    public boolean suppressAutoGrab = false;
    
    private JPanel canvas;
    
    
    private static final Color[] WIRE_COLORS = { 
        Color.CYAN, Color.ORANGE, Color.MAGENTA, Color.GREEN, Color.YELLOW, Color.PINK 
    };
    private static final String[] SKILL_NAMES = {
            "0 - Attack", "1 - Defence", "2 - Strength", "3 - Hitpoints", "4 - Ranged",
            "5 - Prayer", "6 - Magic", "7 - Cooking", "8 - Woodcutting", "9 - Fletching",
            "10 - Fishing", "11 - Firemaking", "12 - Crafting", "13 - Smithing", "14 - Mining",
            "15 - Herblore", "16 - Agility", "17 - Thieving", "18 - Slayer", "19 - Farming",
            "20 - Runecrafting", "21 - Hunter", "22 - Construction", "23 - Summoning"
        };

        private static class QuestItem {
            int id; String name;
            public QuestItem(int id, String name) { this.id = id; this.name = name; }
            public String toString() { return name + " (" + id + ")"; }
        }
        
        public GraphEditor(List<Node> nodes, ExamplePlugin plugin)
    {
        this.nodes = nodes;
        this.plugin = plugin;
        setLayout(new BorderLayout());

     // [REPLACE TOOLBAR SECTION]
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        toolbar.setBackground(new Color(25, 25, 25));

        // 1. Create Checkbox
        locCheckBox = new JCheckBox("Location Specific");
        locCheckBox.setBackground(new Color(25, 25, 25));
        locCheckBox.setForeground(Color.CYAN);
        locCheckBox.setFocusPainted(false);
        
        // 2. Add Listener: Checking the box AUTO-GRABS location
        locCheckBox.addActionListener(e -> {
            // If suppressed (e.g. during load), do nothing
            if (suppressAutoGrab) return; 

            if (locCheckBox.isSelected()) {
                grabLocation(); // Call the public helper
            } else {
                dialogueLocX = -1;
                dialogueLocY = -1;
                dialogueHeight = -1;
            }
        });
        toolbar.add(locCheckBox);
        add(toolbar, BorderLayout.NORTH);        
        
        this.canvas = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                renderGraph((Graphics2D)g);
            }
        };
        canvas.setBackground(new Color(30, 30, 30));
        setupInteractions(canvas);
        add(canvas, BorderLayout.CENTER);
        

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setPreferredSize(new Dimension(300, 0));
        rightPanel.setBackground(new Color(40, 40, 43));
        rightPanel.setBorder(new MatteBorder(0, 1, 0, 0, Color.GRAY));

        // 1. INSPECTOR (Top - Scrollable Editor)
        inspectorPanel = new JPanel();
        inspectorPanel.setLayout(new BoxLayout(inspectorPanel, BoxLayout.Y_AXIS));
        inspectorPanel.setBackground(new Color(40, 40, 43));
        
        JScrollPane inspectorScroll = new JScrollPane(inspectorPanel);
        inspectorScroll.setBorder(null);
        inspectorScroll.getVerticalScrollBar().setUnitIncrement(16);
        
        // 2. SIMULATOR (Bottom - Fixed Height Preview)
        simulatorPanel = new JPanel();
        simulatorPanel.setLayout(new BoxLayout(simulatorPanel, BoxLayout.Y_AXIS));
        simulatorPanel.setBackground(new Color(30, 30, 30));
        simulatorPanel.setBorder(new MatteBorder(1, 0, 0, 0, Color.GRAY));
        simulatorPanel.setPreferredSize(new Dimension(300, 200)); // Fixed height for chat preview

        // Add to Right Panel (Split Center/South)
        rightPanel.add(inspectorScroll, BorderLayout.CENTER);
        rightPanel.add(simulatorPanel, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.EAST);
        }

    public void selectNode(Node n) {
        this.selectedNode = n;
        updateInternalPanel();
        repaint();
    }
    public void grabLocation() {
        if (plugin.client.getLocalPlayer() != null) {
            net.runelite.api.coords.WorldPoint wp = plugin.client.getLocalPlayer().getWorldLocation();
            dialogueLocX = wp.getX();
            dialogueLocY = wp.getY();
            dialogueHeight = wp.getPlane();
        }
    }
    public Point getCanvasCenter() {
        if (canvas == null || canvas.getWidth() == 0) return new Point(50, 50);
        
        // Calculate center of visible screen
        int cx = canvas.getWidth() / 2;
        int cy = canvas.getHeight() / 2;
        
        // Convert to Graph Model coordinates (Reverse the offset/scale transform)
        int mx = (int) ((cx - offsetX) / scale);
        int my = (int) ((cy - offsetY) / scale);
        
        return new Point(mx, my);
    }
    public void updateInternalPanel() {
        inspectorPanel.removeAll();
        simulatorPanel.removeAll();

        if (selectedNode == null) {
            JLabel lbl = new JLabel("No Node Selected");
            lbl.setForeground(Color.GRAY);
            inspectorPanel.add(lbl);
            inspectorPanel.revalidate(); inspectorPanel.repaint();
            simulatorPanel.revalidate(); simulatorPanel.repaint();
            return;
        }

        Node n = selectedNode;
        
        // ==========================================
        // 0. MANUAL OVERRIDES (NEW SECTION)
        // ==========================================
        if (n.type == Node.NodeType.DIALOGUE) {
            JLabel head = new JLabel("NODE SETTINGS");
            head.setForeground(Color.CYAN);
            head.setAlignmentX(Component.LEFT_ALIGNMENT);
            inspectorPanel.add(head);

            JPanel p = new JPanel(new GridLayout(0, 2, 5, 5)); // Grid with gaps
            p.setBackground(new Color(50, 50, 50));
            p.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            p.setMaximumSize(new Dimension(300, 60)); // Limit height

            // 1. NPC / ITEM ID Input
            JLabel lblId = new JLabel("Item/NPC ID:");
            lblId.setForeground(Color.LIGHT_GRAY);
            p.add(lblId);

            JTextField txtId = new JTextField(String.valueOf(n.npcId));
            txtId.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    try {
                        n.npcId = Integer.parseInt(txtId.getText());
                        repaint(); // Update graph visual immediately
                    } catch (Exception x) {}
                }
            });
            p.add(txtId);

            // 2. ZOOM / ITEM 2 Input
            JLabel lblAnim = new JLabel("Zoom / Item 2:");
            lblAnim.setForeground(Color.LIGHT_GRAY);
            p.add(lblAnim);

            JTextField txtAnim = new JTextField(String.valueOf(n.animationId));
            txtAnim.addKeyListener(new KeyAdapter() {
                public void keyReleased(KeyEvent e) {
                    try {
                        n.animationId = Integer.parseInt(txtAnim.getText());
                        repaint();
                    } catch (Exception x) {}
                }
            });
            p.add(txtAnim);

            inspectorPanel.add(p);
            inspectorPanel.add(Box.createVerticalStrut(15)); // Spacing before Conditions/Actions
        }
        // ==========================================
        // 1. CONDITIONS EDITOR (Logic & Dropdowns)
        // ==========================================
        if (n.type == Node.NodeType.LOGIC_BRANCH) {
            JLabel head = new JLabel("LOGIC CONDITIONS");
            head.setForeground(Color.CYAN);
            inspectorPanel.add(head);

            JButton addCond = new JButton("+ Add Condition");
            addCond.setBackground(new Color(100, 70, 140));
            addCond.setForeground(Color.WHITE);
            addCond.addActionListener(e -> {
                n.conditions.add(new Node.Condition());
                updateInternalPanel();
                repaint();
            });
            inspectorPanel.add(addCond);
            inspectorPanel.add(Box.createVerticalStrut(10));

            for(int i=0; i<n.conditions.size(); i++) {
                Node.Condition c = n.conditions.get(i);
                
                // [CHANGE] Use GridLayout(0, 1) to allow variable rows (we are adding a button)
                JPanel p = new JPanel(new GridLayout(0, 1)); 
                p.setBackground(new Color(60,60,60));
                p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Condition " + (i+1)));

                // Row 1: Type Selector
                JComboBox<Node.ConditionType> typeBox = new JComboBox<>(Node.ConditionType.values());
                typeBox.setSelectedItem(c.type);
                typeBox.addActionListener(e -> { c.type = (Node.ConditionType)typeBox.getSelectedItem(); updateInternalPanel(); });
                p.add(typeBox);

                // Row 2: Dynamic Inputs
                JPanel row = new JPanel(new GridLayout(1, 2));
                JComponent input1; 
                
                // (Logic for Skill/Quest dropdowns remains the same...)
                if (c.type == Node.ConditionType.STAT_LEVEL) {
                    JComboBox<String> skillBox = new JComboBox<>(SKILL_NAMES);
                    int idx = Math.max(0, Math.min(23, c.val1));
                    skillBox.setSelectedIndex(idx);
                    skillBox.addActionListener(e -> c.val1 = skillBox.getSelectedIndex());
                    input1 = skillBox;
                } 
                else if (c.type.toString().startsWith("QUEST")) {
                    input1 = createQuestDropdown(c, true);
                } else {
                    JTextField t = new JTextField(String.valueOf(c.val1));
                    t.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{c.val1=Integer.parseInt(t.getText());}catch(Exception x){} }});
                    input1 = t;
                }

                JTextField v2 = new JTextField(String.valueOf(c.val2));
                v2.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{c.val2=Integer.parseInt(v2.getText());}catch(Exception x){} }});
                
                row.add(input1); row.add(v2);
                p.add(row);
                
                // Row 3: Labels
                JPanel lblRow = new JPanel(new GridLayout(1,2));
                JLabel l1 = new JLabel(c.type.label1); l1.setForeground(Color.LIGHT_GRAY);
                JLabel l2 = new JLabel(c.type.label2); l2.setForeground(Color.LIGHT_GRAY);
                lblRow.add(l1); lblRow.add(l2);
                p.add(lblRow);

                // [NEW] Row 4: Remove Button
                JButton remBtn = new JButton("Remove Condition");
                remBtn.setBackground(new Color(150, 50, 50)); // Dark Red
                remBtn.setForeground(Color.WHITE);
                remBtn.addActionListener(e -> {
                    n.conditions.remove(c);
                    // Also remove the connection associated with this slot to prevent dangling wires
                    // The connections map uses indices, so we might need to shift them or let the user fix it.
                    // For safety, we just remove the condition.
                    updateInternalPanel();
                    repaint();
                });
                p.add(remBtn);

                inspectorPanel.add(p);
            }
        }

        // ==========================================
        // 2. ACTIONS EDITOR (Items, Tele, Quests)
        // ==========================================
        inspectorPanel.add(Box.createVerticalStrut(15));
        JLabel actHead = new JLabel("ACTIONS");
        actHead.setForeground(Color.ORANGE);
        inspectorPanel.add(actHead);

        JButton addAct = new JButton("+ Add Action");
        addAct.setBackground(new Color(40, 100, 140));
        addAct.setForeground(Color.WHITE);
        addAct.addActionListener(e -> {
            n.actions.add(new Node.Action());
            updateInternalPanel();
            repaint();
        });
        inspectorPanel.add(addAct);

        for(int i=0; i<n.actions.size(); i++) {
            Node.Action a = n.actions.get(i);
            JPanel p = new JPanel(new GridLayout(0, 1));
            p.setBackground(new Color(50,50,50));
            p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.GRAY), "Action " + (i+1)));

            JComboBox<Node.ActionType> typeBox = new JComboBox<>(Node.ActionType.values());
            typeBox.setSelectedItem(a.type);
            typeBox.addActionListener(e -> { a.type = (Node.ActionType)typeBox.getSelectedItem(); updateInternalPanel(); });
            p.add(typeBox);

            // --- INPUTS ROW 1 ---
            JPanel r1 = new JPanel(new GridLayout(1,2));
            JComponent actInput1;

            // [FIX] Quest Dropdown for Actions
            if (a.type == Node.ActionType.SET_QUEST_STAGE) {
            	actInput1 = createQuestDropdown(a, false);
            } else {
                JTextField t1 = new JTextField(String.valueOf(a.val1));
                t1.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val1=Integer.parseInt(t1.getText());}catch(Exception x){} }});
                actInput1 = t1;
            }

            JTextField t2 = new JTextField(String.valueOf(a.val2));
            t2.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val2=Integer.parseInt(t2.getText());}catch(Exception x){} }});
            
            r1.add(new JLabel(a.type.l1)); r1.add(actInput1);
            p.add(r1);
            
            if(!a.type.l2.isEmpty()) { 
                JPanel r2 = new JPanel(new GridLayout(1,2));
                r2.add(new JLabel(a.type.l2)); r2.add(t2);
                p.add(r2);
            }

            // Val 3 (Teleport Height)
            if(!a.type.l3.isEmpty()) {
                JPanel r3 = new JPanel(new GridLayout(1,2));
                JTextField t3 = new JTextField(String.valueOf(a.val3));
                t3.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val3=Integer.parseInt(t3.getText());}catch(Exception x){} }});
                r3.add(new JLabel(a.type.l3)); r3.add(t3);
                p.add(r3);
            }
            
            JButton del = new JButton("Remove");
            del.setBackground(Color.RED);
            del.addActionListener(e -> { n.actions.remove(a); updateInternalPanel(); repaint(); });
            p.add(del);

            inspectorPanel.add(p);
            inspectorPanel.add(Box.createVerticalStrut(5));
        }

        // ==========================================
        // 3. SIMULATOR
        // ==========================================
        JLabel simLabel = new JLabel("PREVIEW");
        simLabel.setForeground(Color.CYAN);
        simLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        simulatorPanel.add(simLabel);
        
        JTextArea previewText = new JTextArea();
        previewText.setLineWrap(true);
        previewText.setWrapStyleWord(true);
        previewText.setEditable(false);
        previewText.setBackground(new Color(30, 30, 30));
        previewText.setForeground(Color.LIGHT_GRAY);
        if (n.type == Node.NodeType.DIALOGUE) {
            StringBuilder sb = new StringBuilder();
            for(String s : n.lines) sb.append(s).append("\n");
            previewText.setText(sb.toString());
        } else {
            previewText.setText("[" + n.type + "]");
        }
        simulatorPanel.add(new JScrollPane(previewText));
        simulatorPanel.add(Box.createVerticalStrut(5));

        int count = n.getOutputCount();
        for (int i = 0; i < count; i++) {
            final int idx = i;
            String label = "Continue";
            if (n.type == Node.NodeType.OPTION && i < n.options.size()) label = n.options.get(i);
            if (n.type == Node.NodeType.LOGIC_BRANCH) label = (i < n.conditions.size()) ? "True (" + (i+1) + ")" : "Else";

            JButton btn = new JButton(label);
            btn.setAlignmentX(Component.CENTER_ALIGNMENT);
            btn.setBackground(new Color(60, 60, 60));
            btn.setForeground(Color.WHITE);
            btn.setMaximumSize(new Dimension(280, 30));
            
            btn.addActionListener(e -> {
                if (n.connections.containsKey(idx)) {
                    int nextId = n.connections.get(idx);
                    Node next = nodes.stream().filter(x -> x.id == nextId).findFirst().orElse(null);
                    if (next != null) {
                        selectNode(next); 
                        if(plugin != null) plugin.forceCurrentNode(next);
                    }
                } else {
                    JOptionPane.showMessageDialog(this, "No connection linked to this option!");
                }
            });
            simulatorPanel.add(btn);
            simulatorPanel.add(Box.createVerticalStrut(2));
        }

        inspectorPanel.revalidate(); inspectorPanel.repaint();
        simulatorPanel.revalidate(); simulatorPanel.repaint();
    }    
    private void renderGraph(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        AffineTransform at = new AffineTransform();
        at.translate(offsetX, offsetY);
        at.scale(scale, scale);
        g2.transform(at);

        g2.setColor(new Color(36, 36, 36));
        for(int i=0; i<3000; i+=40) { g2.drawLine(i, 0, i, 3000); g2.drawLine(0, i, 3000, i); }

        for(Node n : nodes) {
            n.height = 60 + (n.getOutputCount() * 25);
            
            if (n == selectedNode) {
                g2.setColor(new Color(0, 120, 215, 100));
                g2.fillRoundRect(n.x-5, n.y-5, n.width+10, n.height+10, 15, 15);
            }

            g2.setColor(new Color(45, 45, 45));
            g2.fillRoundRect(n.x, n.y, n.width, n.height, 10, 10);
            

         // --- PASTE THIS INSTEAD ---
            Color headerCol = new Color(60, 60, 60);
            String title = n.type.toString();

            // 1. Determine Color and Title based on Type/Mode
            if (n.type == Node.NodeType.SHOP) {
                headerCol = new Color(100, 50, 50); // Red
                String sName = (n.param == null || n.param.isEmpty()) ? "Unknown" : n.param;
                title = "SHOP: " + sName;
            } 
            else if (n.type == Node.NodeType.INTERFACE) {
                headerCol = new Color(50, 50, 100); // Blue
                title = "INTERFACE: " + n.param;    // Show the ID
            }
            else if (n.type == Node.NodeType.OPTION) {
                headerCol = new Color(85, 50, 30); // Orange
                title = "OPTIONS";
            }
            else if (n.type == Node.NodeType.DIALOGUE) {
                // Distinguish NPC vs PLAYER here
            	if (n.mode == Node.DialogueMode.INFO_BOX) {
                    headerCol = new Color(80, 80, 80); // Dark Grey
                    title = "INFO / ITEM";
                } else
                if (n.mode == Node.DialogueMode.PLAYER_TALK) {
                    headerCol = new Color(40, 60, 80); // Dark Blue
                    title = "PLAYER";
                } else {
                    headerCol = new Color(50, 80, 55); // Green
                    title = "NPC";
                }
                
                // [ADDED] Append Name if available
                if (n.param != null && !n.param.isEmpty()) {
                    title += ": " + n.param;
                }
            }
            else if (n.type == Node.NodeType.LOGIC_BRANCH) {
                headerCol = new Color(60, 40, 70); // Purple
                title = "LOGIC";
            }

            // [ADDED] Append Node ID
            title += " (#" + n.id + ")";

            // 2. Draw the Header Background
            g2.setColor(headerCol);
            g2.fillRoundRect(n.x, n.y, n.width, 30, 10, 10);
            g2.fillRect(n.x, n.y+15, n.width, 15);
            
            // 3. Draw Outline
            g2.setColor(Color.GRAY);
            g2.drawRoundRect(n.x, n.y, n.width, n.height, 10, 10);

            // 4. Draw Title Text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            g2.drawString(title, n.x+10, n.y+20);
            
            // [ADDED] Draw Head Animation ID (if Dialogue)
            if (n.type == Node.NodeType.DIALOGUE && n.animationId != -1) {
                Color oldC = g2.getColor();
                Font oldF = g2.getFont();
                
                g2.setColor(Color.CYAN);
                g2.setFont(new Font("Consolas", Font.PLAIN, 10));
                String animTxt = "Anim: " + n.animationId;
                int animW = g2.getFontMetrics().stringWidth(animTxt);
                g2.drawString(animTxt, n.x + n.width - animW - 10, n.y + 20);
                
                g2.setColor(oldC);
                g2.setFont(oldF);
            }
            
            if (!n.actions.isEmpty()) {
                Font originalFont = g2.getFont();
                int iconX = n.x + 10 + g2.getFontMetrics().stringWidth(title) + 10;
                int iconY = n.y + 5;

                for (Node.Action a : n.actions) {
                    Color badgeCol = Color.GRAY;
                    String badgeTxt = "?";

                    switch (a.type) {
                        case GIVE_ITEM:       badgeCol = new Color(50, 200, 50);  badgeTxt = "+"; break;
                        case REMOVE_ITEM:     badgeCol = new Color(200, 50, 50);  badgeTxt = "-"; break;
                        case TELEPORT:        badgeCol = new Color(50, 100, 255); badgeTxt = "T"; break;
                        case ANIMATION:       badgeCol = new Color(150, 50, 200); badgeTxt = "A"; break;
                        case GRAPHICS:        badgeCol = new Color(200, 50, 150); badgeTxt = "G"; break;
                        case SET_QUEST_STAGE: badgeCol = new Color(255, 165, 0);  badgeTxt = "Q"; break;
                    }

                    g2.setColor(badgeCol);
                    g2.fillOval(iconX, iconY, 16, 16);
                    g2.setColor(Color.WHITE);
                    g2.setStroke(new BasicStroke(1));
                    g2.drawOval(iconX, iconY, 16, 16);

                    g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                    int txtW = g2.getFontMetrics().stringWidth(badgeTxt);
                    g2.drawString(badgeTxt, iconX + (8 - txtW/2) + 1, iconY + 12);

                    iconX += 18;
                }
                g2.setFont(originalFont); // Restore font
            }
            
            
            g2.setColor(Color.LIGHT_GRAY);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            int textY = n.y + 50;
            if (n.type == Node.NodeType.DIALOGUE && !n.lines.isEmpty()) {
                String line = n.lines.get(0);
                if (line.length() > 25) line = line.substring(0, 23) + ".."; 
                g2.drawString(line, n.x+10, textY);
            }
            else if (n.type == Node.NodeType.OPTION) {
                for (int i = 0; i < n.options.size(); i++) {
                    String opt = n.options.get(i);
                    
                    // CALCULATION: n.y + 50 (start offset) + (i * 25) (socket spacing) + 10 (text baseline adjustment)
                    int optionY = n.y + 50 + (i * 25) + 10;
                    
                    String line = (i + 1) + ". " + opt; // Added number for clarity
                    if (line.length() > 28) line = line.substring(0, 26) + "..";
                    
                    g2.drawString(line, n.x + 10, optionY);
                }
            }
            // [ADDED] Logic Branch Conditions
            else if (n.type == Node.NodeType.LOGIC_BRANCH) {
                for (int i = 0; i < n.conditions.size(); i++) {
                    Node.Condition c = n.conditions.get(i);
                    int condY = n.y + 50 + (i * 25) + 10;
                    String label = (i+1) + ". " + c.type.toString();
                    if (label.length() > 28) label = label.substring(0, 26) + "..";
                    g2.drawString(label, n.x + 10, condY);
                }
                // Draw Else at the bottom
                int elseY = n.y + 50 + (n.conditions.size() * 25) + 10;
                g2.drawString("Else", n.x + 10, elseY);
            }

            for(int i=0; i<n.getOutputCount(); i++) {
                Rectangle sock = new Rectangle(n.x + n.width - 12, n.y + 50 + (i*25), 12, 12);
                boolean connected = n.connections.containsKey(i);
                
                g2.setColor(connected ? WIRE_COLORS[i % WIRE_COLORS.length] : Color.RED);
                g2.fillOval(sock.x, sock.y, sock.width, sock.height);

                // --- FIX: Using final variable 'i1' for lambda stream ---
                final int i1 = i; 
                if (connected) {
                    Node t = nodes.stream().filter(x -> x.id == n.connections.get(i1)).findFirst().orElse(null);
                    if (t != null) {
                        drawCurve(g2, sock, new Rectangle(t.x, t.y+15, 0,0), WIRE_COLORS[i % WIRE_COLORS.length]);
                    }
                }
            }
        }
        
        if (connSource != null && mousePos != null) {
            Rectangle start = new Rectangle(connSource.x + connSource.width - 12, connSource.y + 50 + (connIndex*25), 12, 12);
            drawCurve(g2, start, new Rectangle(mousePos.x, mousePos.y, 0, 0), Color.WHITE);
        }
    }
    private void drawCurve(Graphics2D g2, Rectangle r1, Rectangle r2, Color c) {
        Path2D p = new Path2D.Float();
        p.moveTo(r1.x+6, r1.y+6);
        p.curveTo(r1.x+50, r1.y, r2.x-50, r2.y, r2.x, r2.y);
        g2.setColor(c);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(p);
    }

    private void setupInteractions(JPanel canvas) {
        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                Point p = e.getPoint();
                Point graphP = new Point((int)((p.x - offsetX)/scale), (int)((p.y - offsetY)/scale));
                
                for(Node n : nodes) {
                    for(int i=0; i<n.getOutputCount(); i++) {
                        Rectangle r = new Rectangle(n.x + n.width - 12, n.y + 50 + (i*25), 12, 12);
                        if (r.contains(graphP)) {
                            connSource = n; connIndex = i;
                            return;
                        }
                    }
                }
                for(int i=nodes.size()-1; i>=0; i--) {
                    Node n = nodes.get(i);
                    if (new Rectangle(n.x, n.y, n.width, n.height).contains(graphP)) {
                        // --- ADDED: Right Click to Delete ---
                    	if (SwingUtilities.isRightMouseButton(e)) {
                            JPopupMenu menu = new JPopupMenu();
                            
                            JMenuItem editItem = new JMenuItem("Edit Node");
                            editItem.addActionListener(ev -> openNodeEditor(n));
                            menu.add(editItem);

                            // [ADD THIS BUTTON]
                            if (n.type == Node.NodeType.DIALOGUE || n.type == Node.NodeType.OPTION) {
                                JMenuItem dateBranch = new JMenuItem("Add Date Branch");
                                dateBranch.addActionListener(ev -> createDateBranch(n));
                                menu.add(dateBranch);
                            }
                            // -----------------

                            JMenuItem deleteItem = new JMenuItem("Delete Node");
                            deleteItem.addActionListener(ev -> {
                                nodes.remove(n);
                                for (Node other : nodes) {
                                    other.connections.values().removeIf(targetId -> targetId == n.id);
                                }
                                if (selectedNode == n) selectedNode = null;
                                repaint();
                            });
                            menu.add(deleteItem);
                            
                            menu.show(canvas, e.getX(), e.getY());
                            return;
                        }
                    	// ------------------------------------

                        selectNode(n);
                        draggingNode = n; dragStart = graphP;
                        return;
                    }
                }
                dragStart = e.getPoint(); 
            }
            public void mouseDragged(MouseEvent e) {
                Point graphP = new Point((int)((e.getX() - offsetX)/scale), (int)((e.getY() - offsetY)/scale));
                if (connSource != null) { mousePos = graphP; canvas.repaint(); }
                else if (draggingNode != null) {
                    draggingNode.x += graphP.x - dragStart.x;
                    draggingNode.y += graphP.y - dragStart.y;
                    dragStart = graphP;
                    canvas.repaint();
                } else if (dragStart != null) {
                    offsetX += e.getX() - dragStart.x;
                    offsetY += e.getY() - dragStart.y;
                    dragStart = e.getPoint();
                    canvas.repaint();
                }
            }
            public void mouseReleased(MouseEvent e) {
                if (connSource != null && mousePos != null) {
                    for(Node n : nodes) {
                        if (n != connSource && new Rectangle(n.x, n.y, n.width, n.height).contains(mousePos)) {
                            connSource.connections.put(connIndex, n.id);
                            updateInternalPanel(); 
                            break;
                        }
                    }
                }
                connSource = null; draggingNode = null; mousePos = null; dragStart = null;
                canvas.repaint();
            }
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (e.getWheelRotation() < 0) scale *= 1.1; else scale /= 1.1;
                canvas.repaint();
            }
        };
        canvas.addMouseListener(ma);
        canvas.addMouseMotionListener(ma);
        canvas.addMouseWheelListener(ma);
    }
    
 // [REPLACE THESE 3 METHODS IN GraphEditor.java]

    private void openNodeEditor(Node n) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Edit Node " + n.id, Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setSize(750, 900);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        
        // --- HELPER TO UPDATE TAB TITLES ---
        Runnable updateTabs = () -> {
            for (int i = 0; i < n.variants.size(); i++) {
                Node.Variant v = n.variants.get(i);
                String title = v.startDate + " - " + v.endDate;
                
                // [FIX] Check if the tab exists before trying to rename it
                // Tab 0 is Default, so Variants start at index 1
                int tabIndex = i + 1;
                if (tabIndex < tabbedPane.getTabCount()) {
                    tabbedPane.setTitleAt(tabIndex, title);
                }
            }
        };

        // TAB 1: DEFAULT
        tabbedPane.addTab("Default / Else", createVariantPanel(n, null, dialog, updateTabs));

        // TABS 2+: VARIANTS
        for (int i = 0; i < n.variants.size(); i++) {
            Node.Variant v = n.variants.get(i);
            String title = v.startDate + " - " + v.endDate;
            tabbedPane.addTab(title, createVariantPanel(n, v, dialog, updateTabs));
        }

        // --- TOP TOOLBAR (Styled Button) ---
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBorder(new EmptyBorder(5, 5, 5, 5));
        topBar.setBackground(new Color(60, 60, 60));

        JButton btnAddVar = new JButton("+ ADD NEW DATE VARIANT");
        btnAddVar.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnAddVar.setBackground(new Color(220, 140, 40)); // Distinct Orange
        btnAddVar.setForeground(Color.WHITE);
        btnAddVar.setFocusPainted(false);
        
        btnAddVar.addActionListener(e -> {
            Node.Variant v = new Node.Variant();
            
            // --- DEEP CLONE FROM DEFAULT ---
            v.npcId = n.npcId; 
            v.animationId = n.animationId;
            v.param = n.param; // Clone Title/Name
            
            // Clone Text
            if (n.type == Node.NodeType.DIALOGUE) v.lines = new ArrayList<>(n.lines);
            if (n.type == Node.NodeType.OPTION) v.options = new ArrayList<>(n.options);
            
            // Clone Conditions
            for(Node.Condition c : n.conditions) {
                Node.Condition newC = new Node.Condition();
                newC.type = c.type; newC.val1 = c.val1; newC.val2 = c.val2;
                v.conditions.add(newC);
            }
            
            // Clone Actions
            for(Node.Action a : n.actions) {
                Node.Action newA = new Node.Action();
                newA.type = a.type; newA.val1 = a.val1; newA.val2 = a.val2; newA.val3 = a.val3;
                v.actions.add(newA);
            }
            // -------------------------------
            
            n.variants.add(v);
            
            // Add Tab and Switch
            String title = v.startDate + " - " + v.endDate;
            tabbedPane.addTab(title, createVariantPanel(n, v, dialog, updateTabs));
            tabbedPane.setSelectedIndex(tabbedPane.getTabCount() - 1);
        });
        
        // Center the big button
        JPanel centerWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centerWrapper.setOpaque(false);
        centerWrapper.add(btnAddVar);
        topBar.add(centerWrapper, BorderLayout.CENTER);

        // Remove Button (Right side)
        JButton btnRemove = new JButton("Delete Tab");
        btnRemove.setBackground(new Color(150, 50, 50));
        btnRemove.setForeground(Color.WHITE);
        btnRemove.addActionListener(e -> {
            int idx = tabbedPane.getSelectedIndex();
            if (idx > 0) {
                n.variants.remove(idx - 1);
                tabbedPane.remove(idx);
            } else {
                JOptionPane.showMessageDialog(dialog, "Cannot remove the Default tab.");
            }
        });
        topBar.add(btnRemove, BorderLayout.EAST);

        dialog.add(topBar, BorderLayout.NORTH);
        dialog.add(tabbedPane, BorderLayout.CENTER);

        // SAVE BUTTON
        JButton btnSave = new JButton("SAVE & CLOSE");
        btnSave.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSave.setBackground(new Color(50, 150, 50));
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(e -> {
            selectNode(n);
            repaint();
            dialog.dispose();
        });
        dialog.add(btnSave, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }
    private JComponent createVariantPanel(Node n, Node.Variant v, JDialog parentDialog, Runnable tabRefresher) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        boolean isDefault = (v == null);

        // --- 1. DATE SELECTORS ---
        if (!isDefault) {
            addHeader(p, "DATE RANGE");
            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel statusIcon = new JLabel("?");
            statusIcon.setFont(new Font("Segoe UI", Font.BOLD, 16));
            
            // Pass the refresher so changing dates updates the tab title
            dateRow.add(createDateSelectors(v, n, statusIcon, tabRefresher));
            dateRow.add(Box.createHorizontalStrut(10));
            dateRow.add(statusIcon);
            p.add(dateRow);
            p.add(Box.createVerticalStrut(10));
        }

        // --- 2. VISUALS (ID & Titles) ---
        addHeader(p, "VISUAL SETTINGS");
        JPanel visPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        
        visPanel.add(new JLabel("NPC ID:"));
        JTextField txtId = new JTextField(String.valueOf(isDefault ? n.npcId : v.npcId));
        txtId.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{
            int val = Integer.parseInt(txtId.getText());
            if(isDefault) n.npcId = val; else v.npcId = val;
        }catch(Exception x){} }});
        visPanel.add(txtId);

        visPanel.add(new JLabel("Anim ID:"));
        JTextField txtAnim = new JTextField(String.valueOf(isDefault ? n.animationId : v.animationId));
        txtAnim.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{
            int val = Integer.parseInt(txtAnim.getText());
            if(isDefault) n.animationId = val; else v.animationId = val;
        }catch(Exception x){} }});
        visPanel.add(txtAnim);

        // [EDITABLE TITLE / PARAM]
        visPanel.add(new JLabel("NPC Name / Window Title:"));
        String currParam = isDefault ? n.param : v.param;
        JTextField txtParam = new JTextField(currParam == null ? "" : currParam);
        txtParam.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { 
            if(isDefault) n.param = txtParam.getText(); else v.param = txtParam.getText();
        }});
        visPanel.add(txtParam);
        
        p.add(visPanel);
        p.add(Box.createVerticalStrut(10));

        // --- 3. CONNECTIONS (Manual Wiring) - DEFAULT TAB ONLY ---
        if (isDefault) {
            addHeader(p, "CONNECTIONS (Manual Wiring)");
            JPanel connPanel = new JPanel(new GridLayout(0, 2, 5, 5));
            connPanel.setBorder(BorderFactory.createTitledBorder("Output -> Target Node ID"));
            
            for(int i = 0; i < n.getOutputCount(); i++) {
                final int outputIdx = i;
                String label = "Output " + (i+1);
                if (n.type == Node.NodeType.OPTION && i < n.options.size()) label = n.options.get(i);
                if (label.length() > 15) label = label.substring(0, 12) + "...";

                connPanel.add(new JLabel(label + ":"));
                
                int target = n.connections.getOrDefault(i, -1);
                JTextField txtConn = new JTextField(String.valueOf(target));
                txtConn.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{
                    int val = Integer.parseInt(txtConn.getText());
                    if (val == -1) n.connections.remove(outputIdx);
                    else n.connections.put(outputIdx, val);
                }catch(Exception x){} }});
                connPanel.add(txtConn);
            }
            p.add(connPanel);
            p.add(Box.createVerticalStrut(10));
        }

        // --- 4. TEXT CONTENT ---
        addHeader(p, "DIALOGUE TEXT / OPTIONS");
        JTextArea txtContent = new JTextArea(4, 20);
        List<String> src = isDefault ? 
            (n.type == Node.NodeType.OPTION ? n.options : n.lines) : 
            (n.type == Node.NodeType.OPTION ? v.options : v.lines);
        txtContent.setText(String.join("\n", src));
        txtContent.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                List<String> list = new ArrayList<>(java.util.Arrays.asList(txtContent.getText().split("\n")));
                if(isDefault) { if(n.type==Node.NodeType.OPTION) n.options=list; else n.lines=list; }
                else { if(n.type==Node.NodeType.OPTION) v.options=list; else v.lines=list; }
            }
        });
        p.add(new JScrollPane(txtContent));
        p.add(Box.createVerticalStrut(15));

        // --- 5. LOGIC & ACTIONS ---
        addHeader(p, "CONDITIONS");
        JPanel condPanel = new JPanel(); condPanel.setLayout(new BoxLayout(condPanel, BoxLayout.Y_AXIS));
        List<Node.Condition> condList = isDefault ? n.conditions : v.conditions;
        
        Runnable refreshConds = () -> {
            condPanel.removeAll();
            for (int i=0; i<condList.size(); i++) condPanel.add(createConditionEditor(condList.get(i), condList, i, parentDialog));
            condPanel.revalidate(); condPanel.repaint();
        };
        refreshConds.run();
        p.add(condPanel);
        JButton btnAddC = new JButton("+ Add Condition");
        btnAddC.addActionListener(e -> { condList.add(new Node.Condition()); refreshConds.run(); });
        p.add(btnAddC);
        p.add(Box.createVerticalStrut(10));

        addHeader(p, "ACTIONS");
        JPanel actPanel = new JPanel(); actPanel.setLayout(new BoxLayout(actPanel, BoxLayout.Y_AXIS));
        List<Node.Action> actList = isDefault ? n.actions : v.actions;

        Runnable refreshActs = () -> {
            actPanel.removeAll();
            for (int i=0; i<actList.size(); i++) actPanel.add(createActionEditor(actList.get(i), actList, i, parentDialog));
            actPanel.revalidate(); actPanel.repaint();
        };
        refreshActs.run();
        p.add(actPanel);
        JButton btnAddA = new JButton("+ Add Action");
        btnAddA.addActionListener(e -> { actList.add(new Node.Action()); refreshActs.run(); });
        p.add(btnAddA);

        return new JScrollPane(p);
    }

    private JPanel createDateSelectors(Node.Variant v, Node n, JLabel statusLabel, Runnable onUpdate) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        // Load Date
        int sDate = v.startDate;
        int sY = sDate / 10000; int sM = (sDate % 10000) / 100; int sD = sDate % 100;
        int eDate = v.endDate;
        int eY = eDate / 10000; int eM = (eDate % 10000) / 100; int eD = eDate % 100;

        // UI Components
        Integer[] years = new Integer[30]; for(int i=0; i<30; i++) years[i] = 2000 + i;
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        Integer[] days = new Integer[31]; for(int i=0; i<31; i++) days[i] = i+1;

        p.add(new JLabel("From:"));
        JComboBox<Integer> startY = new JComboBox<>(years); startY.setSelectedItem(sY);
        JComboBox<String> startM = new JComboBox<>(months); startM.setSelectedIndex(Math.max(0, sM-1));
        JComboBox<Integer> startD = new JComboBox<>(days); startD.setSelectedItem(sD);
        p.add(startY); p.add(startM); p.add(startD);

        p.add(new JLabel("  To:"));
        JComboBox<Integer> endY = new JComboBox<>(years); endY.setSelectedItem(eY);
        JComboBox<String> endM = new JComboBox<>(months); endM.setSelectedIndex(Math.max(0, eM-1));
        JComboBox<Integer> endD = new JComboBox<>(days); endD.setSelectedItem(eD);
        p.add(endY); p.add(endM); p.add(endD);

        // Logic
        ActionListener updateDate = ev -> {
            int y1 = (int) startY.getSelectedItem(); int m1 = startM.getSelectedIndex() + 1; int d1 = (int) startD.getSelectedItem();
            int maxD1 = java.time.YearMonth.of(y1, m1).lengthOfMonth();
            if (d1 > maxD1) { d1 = maxD1; startD.setSelectedItem(d1); }
            int startVal = (y1 * 10000) + (m1 * 100) + d1;
            v.startDate = startVal;

            int y2 = (int) endY.getSelectedItem(); int m2 = endM.getSelectedIndex() + 1; int d2 = (int) endD.getSelectedItem();
            int endVal = (y2 * 10000) + (m2 * 100) + d2;

            if (endVal <= startVal) {
                java.time.LocalDate next = java.time.LocalDate.of(y1, m1, d1).plusDays(1);
                y2 = next.getYear(); m2 = next.getMonthValue(); d2 = next.getDayOfMonth();
                endY.setSelectedItem(y2); endM.setSelectedIndex(m2-1); endD.setSelectedItem(d2);
                endVal = (y2 * 10000) + (m2 * 100) + d2;
            }
            int maxD2 = java.time.YearMonth.of(y2, m2).lengthOfMonth();
            if (d2 > maxD2) { d2 = maxD2; endD.setSelectedItem(d2); endVal = (y2 * 10000) + (m2 * 100) + d2; }
            v.endDate = endVal;

            // Conflict Check
            boolean conflict = false;
            String conflictMsg = "";
            for (Node.Variant other : n.variants) {
                if (other == v) continue;
                if (v.startDate <= other.endDate && v.endDate >= other.startDate) {
                    conflict = true;
                    conflictMsg = "<html>Conflict with:<br>" + other.startDate + " - " + other.endDate + "</html>";
                    break;
                }
            }

            if (conflict) { statusLabel.setText("ERR"); statusLabel.setForeground(Color.RED); statusLabel.setToolTipText(conflictMsg); }
            else { statusLabel.setText("OK"); statusLabel.setForeground(new Color(0, 180, 0)); statusLabel.setToolTipText("Valid"); }
            
            // Refresh Tabs
            if (onUpdate != null) onUpdate.run();
        };

        startY.addActionListener(updateDate); startM.addActionListener(updateDate); startD.addActionListener(updateDate);
        endY.addActionListener(updateDate); endM.addActionListener(updateDate); endD.addActionListener(updateDate);
        
        updateDate.actionPerformed(null);
        return p;
    }
    private JComponent createVariantPanel(Node n, Node.Variant v, JDialog parentDialog) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        boolean isDefault = (v == null);

        // --- 1. DATE SELECTORS (If Variant) ---
        if (!isDefault) {
            addHeader(p, "DATE RANGE");
            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JLabel statusIcon = new JLabel("?");
            statusIcon.setFont(new Font("Segoe UI", Font.BOLD, 16));
            
            dateRow.add(createDateSelectors(v, n, statusIcon));
            dateRow.add(Box.createHorizontalStrut(10));
            dateRow.add(statusIcon);
            p.add(dateRow);
            p.add(Box.createVerticalStrut(10));
        }

        // --- 2. VISUALS ---
        addHeader(p, "VISUAL SETTINGS");
        JPanel visPanel = new JPanel(new GridLayout(0, 4, 5, 5));
        
        visPanel.add(new JLabel("NPC ID:"));
        JTextField txtId = new JTextField(String.valueOf(isDefault ? n.npcId : v.npcId));
        txtId.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{
            int val = Integer.parseInt(txtId.getText());
            if(isDefault) n.npcId = val; else v.npcId = val;
        }catch(Exception x){} }});
        visPanel.add(txtId);

        visPanel.add(new JLabel("Anim ID:"));
        JTextField txtAnim = new JTextField(String.valueOf(isDefault ? n.animationId : v.animationId));
        txtAnim.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{
            int val = Integer.parseInt(txtAnim.getText());
            if(isDefault) n.animationId = val; else v.animationId = val;
        }catch(Exception x){} }});
        visPanel.add(txtAnim);
        
        p.add(visPanel);
        p.add(Box.createVerticalStrut(10));

        // --- 3. TEXT CONTENT ---
        addHeader(p, "DIALOGUE TEXT");
        JTextArea txtContent = new JTextArea(4, 20);
        List<String> src = isDefault ? 
            (n.type == Node.NodeType.OPTION ? n.options : n.lines) : 
            (n.type == Node.NodeType.OPTION ? v.options : v.lines);
        txtContent.setText(String.join("\n", src));
        txtContent.addKeyListener(new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                List<String> list = new ArrayList<>(java.util.Arrays.asList(txtContent.getText().split("\n")));
                if(isDefault) { if(n.type==Node.NodeType.OPTION) n.options=list; else n.lines=list; }
                else { if(n.type==Node.NodeType.OPTION) v.options=list; else v.lines=list; }
            }
        });
        p.add(new JScrollPane(txtContent)); // Use JScrollPane here too
        p.add(Box.createVerticalStrut(15));

        // --- 4. INTERNAL LOGIC (CONDITIONS) ---
        addHeader(p, "CONDITIONS (Requirements)");
        JPanel condPanel = new JPanel(); 
        condPanel.setLayout(new BoxLayout(condPanel, BoxLayout.Y_AXIS));
        List<Node.Condition> condList = isDefault ? n.conditions : v.conditions;
        
        Runnable refreshConds = () -> {
            condPanel.removeAll();
            for (int i=0; i<condList.size(); i++) {
                condPanel.add(createConditionEditor(condList.get(i), condList, i, parentDialog));
            }
            condPanel.revalidate(); condPanel.repaint();
        };
        refreshConds.run();
        p.add(condPanel);
        JButton btnAddC = new JButton("+ Add Condition");
        btnAddC.addActionListener(e -> { condList.add(new Node.Condition()); refreshConds.run(); });
        p.add(btnAddC);
        p.add(Box.createVerticalStrut(15));

        // --- 5. INTERNAL ACTIONS (Rewards) ---
        addHeader(p, "ACTIONS (Rewards)");
        JPanel actPanel = new JPanel();
        actPanel.setLayout(new BoxLayout(actPanel, BoxLayout.Y_AXIS));
        List<Node.Action> actList = isDefault ? n.actions : v.actions;

        Runnable refreshActs = () -> {
            actPanel.removeAll();
            for (int i=0; i<actList.size(); i++) {
                actPanel.add(createActionEditor(actList.get(i), actList, i, parentDialog));
            }
            actPanel.revalidate(); actPanel.repaint();
        };
        refreshActs.run();
        p.add(actPanel);
        JButton btnAddA = new JButton("+ Add Action");
        btnAddA.addActionListener(e -> { actList.add(new Node.Action()); refreshActs.run(); });
        p.add(btnAddA);

        // [FIX] Return JScrollPane (which is a JComponent), NOT JScrollPanel
        return new JScrollPane(p);
    }
    
    private void addHeader(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.ORANGE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 14));
        l.setAlignmentX(Component.LEFT_ALIGNMENT); // Keeps it aligned to the left
        p.add(l);
        p.add(Box.createVerticalStrut(5));
    }
 // [REPLACE THIS METHOD IN GraphEditor.java]
    private JPanel createDateSelectors(Node.Variant v, Node n, JLabel statusLabel) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        
        // 1. DATA PREP
        int sDate = v.startDate;
        int sY = sDate / 10000;
        int sM = (sDate % 10000) / 100;
        int sD = sDate % 100;
        
        int eDate = v.endDate;
        int eY = eDate / 10000;
        int eM = (eDate % 10000) / 100;
        int eD = eDate % 100;

        // 2. DROPDOWNS
        Integer[] years = new Integer[30];
        for(int i=0; i<30; i++) years[i] = 2000 + i;
        
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        
        Integer[] days = new Integer[31];
        for(int i=0; i<31; i++) days[i] = i+1;

        // START DATE
        p.add(new JLabel("From:"));
        JComboBox<Integer> startY = new JComboBox<>(years); startY.setSelectedItem(sY);
        JComboBox<String> startM = new JComboBox<>(months); startM.setSelectedIndex(Math.max(0, sM-1));
        JComboBox<Integer> startD = new JComboBox<>(days); startD.setSelectedItem(sD);
        p.add(startY); p.add(startM); p.add(startD);

        // END DATE
        p.add(new JLabel("  To:"));
        JComboBox<Integer> endY = new JComboBox<>(years); endY.setSelectedItem(eY);
        JComboBox<String> endM = new JComboBox<>(months); endM.setSelectedIndex(Math.max(0, eM-1));
        JComboBox<Integer> endD = new JComboBox<>(days); endD.setSelectedItem(eD);
        p.add(endY); p.add(endM); p.add(endD);

        // 3. LOGIC LISTENER
        ActionListener updateDate = ev -> {
            // A. READ START DATE
            int y1 = (int) startY.getSelectedItem();
            int m1 = startM.getSelectedIndex() + 1;
            int d1 = (int) startD.getSelectedItem();
            
            // A2. Validate Start Day (e.g. Feb 30 -> Feb 28)
            int maxD1 = java.time.YearMonth.of(y1, m1).lengthOfMonth();
            if (d1 > maxD1) { d1 = maxD1; startD.setSelectedItem(d1); }
            
            int currentStartVal = (y1 * 10000) + (m1 * 100) + d1;
            v.startDate = currentStartVal;

            // B. READ END DATE
            int y2 = (int) endY.getSelectedItem();
            int m2 = endM.getSelectedIndex() + 1;
            int d2 = (int) endD.getSelectedItem();
            
            int currentEndVal = (y2 * 10000) + (m2 * 100) + d2;

            // C. AUTO-CORRECT END DATE (If End <= Start)
            if (currentEndVal <= currentStartVal) {
                // Calculate Start + 1 Day using Java Time
                java.time.LocalDate sLd = java.time.LocalDate.of(y1, m1, d1);
                java.time.LocalDate next = sLd.plusDays(1);
                
                y2 = next.getYear();
                m2 = next.getMonthValue();
                d2 = next.getDayOfMonth();
                
                // Update UI to reflect the force-change
                endY.setSelectedItem(y2);
                endM.setSelectedIndex(m2 - 1);
                endD.setSelectedItem(d2);
                
                currentEndVal = (y2 * 10000) + (m2 * 100) + d2;
            }
            // Validate End Day for month (e.g. changing month to Feb while on day 31)
            int maxD2 = java.time.YearMonth.of(y2, m2).lengthOfMonth();
            if (d2 > maxD2) { 
                d2 = maxD2; 
                endD.setSelectedItem(d2); 
                currentEndVal = (y2 * 10000) + (m2 * 100) + d2;
            }
            
            v.endDate = currentEndVal;

            // D. CHECK CONFLICTS WITH OTHER TABS
            boolean conflict = false;
            String conflictMsg = "";
            
            for (Node.Variant other : n.variants) {
                if (other == v) continue; // Don't check against self
                // Overlap Logic: (StartA <= EndB) and (EndA >= StartB)
                if (v.startDate <= other.endDate && v.endDate >= other.startDate) {
                    conflict = true;
                    conflictMsg = "<html>Conflict with range:<br>" + other.startDate + " - " + other.endDate + "</html>";
                    break;
                }
            }

            // E. UPDATE STATUS ICON (No Unicode)
            if (conflict) {
                statusLabel.setText("ERR");
                statusLabel.setForeground(Color.RED);
                statusLabel.setToolTipText(conflictMsg);
            } else {
                statusLabel.setText("OK");
                statusLabel.setForeground(new Color(0, 180, 0)); // Dark Green
                statusLabel.setToolTipText("Valid Date Range");
            }
        };

        // Attach listeners
        startY.addActionListener(updateDate); startM.addActionListener(updateDate); startD.addActionListener(updateDate);
        endY.addActionListener(updateDate); endM.addActionListener(updateDate); endD.addActionListener(updateDate);
        
        // Run logic once to initialize
        updateDate.actionPerformed(null);

        return p;
    }
    
    private JPanel createConditionEditor(Node.Condition c, List<Node.Condition> list, int index, JDialog parent) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("Condition " + (index+1)));
        p.setBackground(new Color(60, 60, 60));

        // --- HEADER (Type Selector) ---
        JPanel header = new JPanel(new GridLayout(1, 1));
        header.setBackground(new Color(60, 60, 60));
        
        JComboBox<Node.ConditionType> typeBox = new JComboBox<>(Node.ConditionType.values());
        typeBox.setSelectedItem(c.type);
        header.add(typeBox);
        p.add(header, BorderLayout.NORTH);

        // --- CONTENT AREA ---
        // We use a wrapper panel that we can rebuild dynamically
        JPanel contentWrapper = new JPanel();
        contentWrapper.setLayout(new BoxLayout(contentWrapper, BoxLayout.Y_AXIS));
        contentWrapper.setBackground(new Color(60, 60, 60));
        p.add(contentWrapper, BorderLayout.CENTER);

        // --- REBUILDER LOGIC ---
        Runnable rebuildUI = new Runnable() {
            @Override
            public void run() {
                contentWrapper.removeAll();

                if (c.type == Node.ConditionType.REQUIRE_ALL) {
                    // === NESTED MODE ===
                    JLabel info = new JLabel("All sub-conditions must be TRUE.");
                    info.setForeground(Color.CYAN);
                    info.setAlignmentX(Component.LEFT_ALIGNMENT);
                    contentWrapper.add(info);
                    contentWrapper.add(Box.createVerticalStrut(5));

                    if (c.subConditions == null) c.subConditions = new ArrayList<>();

                    // Recursively add editors for sub-conditions
                    for (int i = 0; i < c.subConditions.size(); i++) {
                        JPanel subEditor = createConditionEditor(c.subConditions.get(i), c.subConditions, i, parent);
                        // Add left margin to visualize nesting
                        subEditor.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createEmptyBorder(0, 20, 0, 0),
                            subEditor.getBorder()
                        ));
                        contentWrapper.add(subEditor);
                        contentWrapper.add(Box.createVerticalStrut(5));
                    }

                    // "Add Sub-Condition" Button
                    JButton addSub = new JButton("+ Add Requirement");
                    addSub.setAlignmentX(Component.LEFT_ALIGNMENT);
                    addSub.addActionListener(ev -> {
                        c.subConditions.add(new Node.Condition());
                        this.run(); // Rebuild this panel
                    });
                    contentWrapper.add(addSub);

                } else {
                    // === STANDARD MODE (Value Fields) ===
                    JPanel grid = new JPanel(new GridLayout(1, 2));
                    grid.setBackground(new Color(60, 60, 60));
                    
                    // Input 1 (ID / Skill / etc)
                    JComponent input1;
                    if (c.type == Node.ConditionType.STAT_LEVEL) {
                        JComboBox<String> skillBox = new JComboBox<>(SKILL_NAMES);
                        skillBox.setSelectedIndex(Math.max(0, Math.min(23, c.val1)));
                        skillBox.addActionListener(ev -> c.val1 = skillBox.getSelectedIndex());
                        input1 = skillBox;
                    } else if (c.type.toString().startsWith("QUEST")) {
                        // Just use the standard dropdown. 
                        // If you need to select "Points (0)", ensure it's in your loadedQuests map or allow manual entry.
                        input1 = createQuestDropdown(c, true);
                    } else {
                        JTextField t = new JTextField(String.valueOf(c.val1));
                        t.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent ev) { try{c.val1=Integer.parseInt(t.getText());}catch(Exception x){} }});
                        input1 = t;
                    }
                    grid.add(input1);

                    // Input 2 (Amount / State) + Checkbox Wrapper
                    JPanel input2Wrapper = new JPanel(new BorderLayout());
                    input2Wrapper.setBackground(new Color(60, 60, 60));
                    
                    JTextField t2 = new JTextField(String.valueOf(c.val2));
                    t2.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent ev) { try{c.val2=Integer.parseInt(t2.getText());}catch(Exception x){} }});
                    
                    // [NEW] Checkbox for Quest Completion
                    if (c.type == Node.ConditionType.QUEST_STAGE_EQUALS || c.type == Node.ConditionType.QUEST_STAGE_EQUALS_GREATER) {
                        JCheckBox chkComplete = new JCheckBox("Completed?");
                        chkComplete.setBackground(new Color(60, 60, 60));
                        chkComplete.setForeground(Color.LIGHT_GRAY);
                        
                        // Initialize State based on value
                        if (c.val2 == 50) {
                            chkComplete.setSelected(true);
                            t2.setText("COMPLETED");
                            t2.setEnabled(false);
                        }

                        chkComplete.addActionListener(ev -> {
                            if (chkComplete.isSelected()) {
                                c.val2 = 50;
                                t2.setText("COMPLETED");
                                t2.setEnabled(false);
                            } else {
                                // Revert to 0 or allow editing
                                c.val2 = 0;
                                t2.setText("0");
                                t2.setEnabled(true);
                            }
                        });
                        
                        input2Wrapper.add(t2, BorderLayout.CENTER);
                        input2Wrapper.add(chkComplete, BorderLayout.EAST);
                    } else {
                        input2Wrapper.add(t2, BorderLayout.CENTER);
                    }
                    
                    grid.add(input2Wrapper);

                    contentWrapper.add(grid);
                    
                    // Labels
                    JPanel lbls = new JPanel(new GridLayout(1, 2));
                    lbls.setBackground(new Color(60, 60, 60));
                    JLabel l1 = new JLabel(c.type.label1); l1.setForeground(Color.LIGHT_GRAY);
                    JLabel l2 = new JLabel(c.type.label2); l2.setForeground(Color.LIGHT_GRAY);
                    lbls.add(l1); lbls.add(l2);
                    contentWrapper.add(lbls);
                }
                // --- REMOVE BUTTON (Always at bottom) ---
                JButton remove = new JButton("Remove");
                remove.setBackground(new Color(100, 50, 50));
                remove.setForeground(Color.WHITE);
                remove.setAlignmentX(Component.LEFT_ALIGNMENT);
                remove.addActionListener(ev -> {
                    list.remove(c);
                    // Hack to refresh parent container:
                    Container parentContainer = p.getParent();
                    if (parentContainer != null) {
                        parentContainer.remove(p);
                        parentContainer.revalidate();
                        parentContainer.repaint();
                    }
                });
                contentWrapper.add(Box.createVerticalStrut(5));
                contentWrapper.add(remove);

                contentWrapper.revalidate();
                contentWrapper.repaint();
            }
        };

        // Trigger logic on type change
        typeBox.addActionListener(e -> {
            c.type = (Node.ConditionType) typeBox.getSelectedItem();
            rebuildUI.run();
        });

        // Initial Build
        rebuildUI.run();

        return p;
    }
    private JPanel createActionEditor(Node.Action a, List<Node.Action> list, int index, JDialog parent) {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(BorderFactory.createTitledBorder("Action " + (index+1)));

        JComboBox<Node.ActionType> typeBox = new JComboBox<>(Node.ActionType.values());
        typeBox.setSelectedItem(a.type);
        typeBox.addActionListener(e -> a.type = (Node.ActionType)typeBox.getSelectedItem());
        p.add(typeBox);

        JPanel vals = new JPanel(new GridLayout(1, 3));
        
        // Input 1
        if (a.type == Node.ActionType.SET_QUEST_STAGE) {
            vals.add(createQuestDropdown(a, false));
        } else {
            JTextField t1 = new JTextField(String.valueOf(a.val1));
            t1.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val1=Integer.parseInt(t1.getText());}catch(Exception x){} }});
            vals.add(t1);
        }

        JTextField t2 = new JTextField(String.valueOf(a.val2));
        t2.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val2=Integer.parseInt(t2.getText());}catch(Exception x){} }});
        vals.add(t2);

        JTextField t3 = new JTextField(String.valueOf(a.val3));
        t3.addKeyListener(new KeyAdapter() { public void keyReleased(KeyEvent e) { try{a.val3=Integer.parseInt(t3.getText());}catch(Exception x){} }});
        vals.add(t3);
        p.add(vals);

        JButton remove = new JButton("Remove");
        remove.setBackground(new Color(100, 50, 50));
        remove.setForeground(Color.WHITE);
        remove.addActionListener(e -> {
            list.remove(a);
            p.setVisible(false);
            p.getParent().revalidate();
            p.getParent().repaint();
        });
        p.add(remove);
        return p;
    }
    
 // [REPLACE createQuestDropdown METHOD]
    private JComboBox<QuestItem> createQuestDropdown(Object obj, boolean isCondition) {
        java.util.Vector<QuestItem> items = new java.util.Vector<>();
        
        // 1. Identify "Used" (Green) and "Nearby" (Blue) IDs
        java.util.Set<Integer> usedInGraph = getUsedQuestIds();
        java.util.Set<Integer> nearbyInWorld = new java.util.HashSet<>();
        
        // Fetch nearby from plugin using current editor coords (or player coords if not set)
        int checkX = dialogueLocX;
        int checkY = dialogueLocY;
        int checkZ = dialogueHeight;
        
        if (checkX == -1 && plugin.client.getLocalPlayer() != null) {
             net.runelite.api.coords.WorldPoint wp = plugin.client.getLocalPlayer().getWorldLocation();
             checkX = wp.getX(); checkY = wp.getY(); checkZ = wp.getPlane();
        }

        if (checkX != -1) {
            nearbyInWorld = plugin.getNearbyQuestIds(checkX, checkY, checkZ);
        }

        // 2. Build List
        items.add(new QuestItem(0, "Quest Points"));

        java.util.List<QuestItem> topPriority = new java.util.ArrayList<>(); // Green & Blue
        java.util.List<QuestItem> others = new java.util.ArrayList<>();
        
        if (!plugin.loadedQuests.isEmpty()) {
            for (Map.Entry<Integer, String> entry : plugin.loadedQuests.entrySet()) {
                int qId = entry.getKey();
                if (qId == 0) continue; 
                
                // Priority Logic: Used OR Nearby -> Top
                if (usedInGraph.contains(qId) || nearbyInWorld.contains(qId)) {
                    topPriority.add(new QuestItem(qId, entry.getValue()));
                } else {
                    others.add(new QuestItem(qId, entry.getValue()));
                }
            }
        }
        
        items.addAll(topPriority);
        items.addAll(others);
        
        JComboBox<QuestItem> box = new JComboBox<>(items);
        
        // 3. Custom Renderer for Colors
        // Need 'final' copies for lambda
        final java.util.Set<Integer> fUsed = usedInGraph;
        final java.util.Set<Integer> fNear = nearbyInWorld;

        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (value instanceof QuestItem && !isSelected) {
                    QuestItem qi = (QuestItem) value;
                    
                    if (fUsed.contains(qi.id)) {
                        c.setForeground(Color.GREEN); // Used in THIS graph (Highest Priority)
                    } else if (fNear.contains(qi.id)) {
                        c.setForeground(Color.CYAN);  // Used nearby in OTHER graphs (Medium Priority)
                    } else if (qi.id == 0) {
                        c.setForeground(Color.ORANGE);
                    }
                }
                return c;
            }
        });
        
        // 4. Selection Logic (Existing)
        int currentId = -1;
        if (isCondition) currentId = ((Node.Condition)obj).val1;
        else currentId = ((Node.Action)obj).val1;

        for (int i=0; i<items.size(); i++) {
            if (items.get(i).id == currentId) {
                box.setSelectedIndex(i);
                break;
            }
        }
        
        box.addActionListener(e -> {
            QuestItem qi = (QuestItem) box.getSelectedItem();
            if (qi != null) {
                if (isCondition) ((Node.Condition)obj).val1 = qi.id;
                else ((Node.Action)obj).val1 = qi.id;
            }
        });
        return box;
    }
    
    private int getNextSafeId() {
        int max = -1;
        java.util.Set<Integer> used = new java.util.HashSet<>();
        
        for (Node n : nodes) {
            if (n.id > max) max = n.id;
            used.add(n.id);
        }
        
        int next = max + 1;
        while (used.contains(next)) {
            next++;
        }
        return next;
    }
    
    // [ADD THIS METHOD]
    private void createDateBranch(Node target) {
        // 1. Create the Logic Controller (The "Splitter")
        Node logic = new Node();
        logic.id = getNextSafeId(); // Get Valid ID
        logic.type = Node.NodeType.LOGIC_BRANCH;
        logic.x = target.x - 220; 
        logic.y = target.y;
        
        // 2. Add Logic Node IMMEDIATELY to reserve the ID
        nodes.add(logic); 

        // 3. Add the Date Range Condition
        Node.Condition dateCond = new Node.Condition();
        dateCond.type = Node.ConditionType.DATE_RANGE;
        dateCond.val1 = 20010101; 
        dateCond.val2 = 20011231; 
        logic.conditions.add(dateCond);
        
        // 4. Create the Variant (The "Duplicate")
        Node variant = new Node();
        variant.id = getNextSafeId(); // Now guaranteed to be unique because 'logic' is already in 'nodes'
        
        // Copy properties
        variant.type = target.type;
        variant.mode = target.mode;
        variant.npcId = target.npcId;
        variant.animationId = target.animationId;
        variant.param = target.param;
        variant.releaseDate = target.releaseDate;
        variant.lines = new ArrayList<>(target.lines);
        variant.options = new ArrayList<>(target.options);
        variant.actions = new ArrayList<>(target.actions);
        variant.connections = new java.util.HashMap<>(target.connections);
        
        variant.x = target.x;
        variant.y = target.y - 150;
        
        // 5. Add Variant Node
        nodes.add(variant);

        // 6. Wire Logic Node -> Variant & Original
        logic.connections.put(0, variant.id); 
        logic.connections.put(1, target.id); 
        
        // 7. REWIRE INCOMING CONNECTIONS
        for (Node n : nodes) {
            if (n == logic || n == variant) continue;
            for (Map.Entry<Integer, Integer> entry : n.connections.entrySet()) {
                if (entry.getValue() == target.id) {
                    entry.setValue(logic.id);
                }
            }
        }
        
        repaint();
    }
    
    java.util.Set<Integer> getUsedQuestIds() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Node n : nodes) {
            // Check Variants
            if (n.variants != null) {
                for (Node.Variant v : n.variants) {
                    collectUsedQuests(v.conditions, used);
                    for (Node.Action a : v.actions) {
                        if (a.type == Node.ActionType.SET_QUEST_STAGE) used.add(a.val1);
                    }
                }
            }
            // Check Default
            collectUsedQuests(n.conditions, used);
            for (Node.Action a : n.actions) {
                 if (a.type == Node.ActionType.SET_QUEST_STAGE) used.add(a.val1);
            }
        }
        return used;
    }

    private void collectUsedQuests(List<Node.Condition> conds, java.util.Set<Integer> used) {
        if (conds == null) return;
        for (Node.Condition c : conds) {
            if (c.type.toString().startsWith("QUEST")) used.add(c.val1);
            if (c.type == Node.ConditionType.REQUIRE_ALL) {
                 collectUsedQuests(c.subConditions, used);
            }
        }
    }
    
 // [ADD THIS METHOD]
    private void openSettingsDialog() {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "NPC File Settings", Dialog.ModalityType.APPLICATION_MODAL);
        d.setSize(350, 300);
        d.setLocationRelativeTo(this);
        d.setLayout(new BorderLayout());
        d.getContentPane().setBackground(new Color(40, 40, 40));

        JPanel center = new JPanel(new GridLayout(0, 1, 5, 5));
        center.setBackground(new Color(40, 40, 40));
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Checkbox
        JCheckBox chkLoc = new JCheckBox("Location Specific?");
        chkLoc.setSelected(isLocationSpecific);
        chkLoc.setForeground(Color.CYAN);
        chkLoc.setBackground(new Color(40, 40, 40));
        center.add(chkLoc);

        // 2. Coords
        JPanel coordPanel = new JPanel(new GridLayout(0, 2, 5, 5));
        coordPanel.setBackground(new Color(40, 40, 40));
        
        JTextField txtX = new JTextField(String.valueOf(dialogueLocX));
        JTextField txtY = new JTextField(String.valueOf(dialogueLocY));
        JTextField txtH = new JTextField(String.valueOf(dialogueHeight));
        
        coordPanel.add(new JLabel("Abs X:") {{ setForeground(Color.LIGHT_GRAY); }}); coordPanel.add(txtX);
        coordPanel.add(new JLabel("Abs Y:") {{ setForeground(Color.LIGHT_GRAY); }}); coordPanel.add(txtY);
        coordPanel.add(new JLabel("Height:") {{ setForeground(Color.LIGHT_GRAY); }}); coordPanel.add(txtH);
        center.add(coordPanel);

        // Toggle Logic
        java.util.function.Consumer<Boolean> toggle = (enabled) -> {
            txtX.setEnabled(enabled); txtY.setEnabled(enabled); txtH.setEnabled(enabled);
        };
        toggle.accept(isLocationSpecific);
        chkLoc.addActionListener(e -> toggle.accept(chkLoc.isSelected()));

        // 3. Grab Button
        JButton btnGet = new JButton("Grab My Location");
        btnGet.addActionListener(ev -> {
            if (plugin.client.getLocalPlayer() != null) {
                net.runelite.api.coords.WorldPoint wp = plugin.client.getLocalPlayer().getWorldLocation();
                txtX.setText(String.valueOf(wp.getX()));
                txtY.setText(String.valueOf(wp.getY()));
                txtH.setText(String.valueOf(wp.getPlane()));
            }
        });
        center.add(btnGet);

        d.add(center, BorderLayout.CENTER);

        // 4. Save
        JButton btnSave = new JButton("Apply Settings");
        btnSave.setBackground(new Color(50, 100, 50));
        btnSave.setForeground(Color.WHITE);
        btnSave.addActionListener(ev -> {
            try {
                isLocationSpecific = chkLoc.isSelected();
                if (isLocationSpecific) {
                    dialogueLocX = Integer.parseInt(txtX.getText());
                    dialogueLocY = Integer.parseInt(txtY.getText());
                    dialogueHeight = Integer.parseInt(txtH.getText());
                }
                d.dispose();
            } catch(Exception ex) {
                JOptionPane.showMessageDialog(d, "Invalid Numbers");
            }
        });
        d.add(btnSave, BorderLayout.SOUTH);
        d.setVisible(true);
    }
    
}