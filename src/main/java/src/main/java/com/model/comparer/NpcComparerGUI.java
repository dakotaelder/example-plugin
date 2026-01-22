package src.main.java.com.model.comparer;

import com.model.rs317.*;
import net.runelite.api.NPCComposition;

import com.dakota.ExamplePlugin;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class NpcComparerGUI extends JFrame {

    private final ExamplePlugin plugin;
    private final File saveFile = new File("C:\\Users\\dakot\\Server_Workspace\\RuneScape_Server\\data\\dialogues\\npc_mappings.json");
    
    // UI Components
    private JPanel mainContainer;
    private JSplitPane renderSplitPane;
    private JPanel osrsListPanel;
    private JPanel rs317ListPanel;
    private RenderPanelOSRS osrsRenderPanel;
    private RenderPanel317 rs317RenderPanel;
    private RenderPanelOSRS rs459RenderPanel;
    
    private boolean isCustomOsrsMode = false;
    private Map<Integer, NpcDefFullOsrs> customOsrsMap = new HashMap<>();
    // Lists
    private JList<String> osrsList;
    private DefaultListModel<String> osrsListModel;
    private JList<String> rs317List;
    private DefaultListModel<String> rs317ListModel;
    
    // Data Caches
    private Map<Integer, RS317Npc> cache317 = new HashMap<>();
    private List<LightweightNpc> osrsCache = new ArrayList<>(); 
    private RS317Decompressor modelDecompressor317;
    private List<MappingEntry> mappings = new ArrayList<>();
    
    // Mapped List Tab
    private JList<MappingEntry> mappedList;
    private DefaultListModel<MappingEntry> mappedModel;
    private JPanel infoPanel;
    private JList<String> unmatchedList;
    private DefaultListModel<String> unmatchedModel;

    private JTextPane osrsInfoArea;
    private JTextPane rs317InfoArea;
    
    // [NEW] Track current selections for live diffing
    private LightweightNpc currentOsrsNpc;
    private LightweightNpc current317Npc;
    // [NEW] Caches for fast renderer lookups (Avoids lag)
    private Map<Integer, List<Integer>> osrsTo317Map = new HashMap<>();
    private Map<Integer, List<Integer>> rs317ToOsrsMap = new HashMap<>();
    // State
    private boolean isOsrsLeft = true;
    private int selectedOsrsId = -1;
    private int selected317Id = -1;
    private boolean isProgrammaticUpdate = false; // [FIX] Prevents Infinite Loop
    private List<NpcDef459> cache459 = new ArrayList<>();
    private List<String> rs459NamesFull = new ArrayList<>();
    
    // [NEW] Version Selector
    private JComboBox<String> versionSelector;
    private boolean is459Mode = true; // Default to 459
    // Search Source Data
    private List<String> osrsNamesFull = new ArrayList<>();
    private List<String> rs317NamesFull = new ArrayList<>();

    public NpcComparerGUI(ExamplePlugin plugin) {
        this.plugin = plugin;
        setTitle("NPC Comparer & Mapper");
        setSize(1400, 900);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        load317Cache();
        cache459 = NpcLoader459.load();
        
        // 1. Init Renderers
        osrsRenderPanel = new RenderPanelOSRS(plugin);
        rs459RenderPanel = new RenderPanelOSRS(plugin);
        rs317RenderPanel = new RenderPanel317(modelDecompressor317);
        
        
        // 2. Init Lists
        osrsListModel = new DefaultListModel<>();
        osrsList = new JList<>(osrsListModel);
        osrsList.setCellRenderer(new NpcListRenderer(true));
        osrsList.addListSelectionListener(e -> {
            if(!e.getValueIsAdjusting()) onOsrsSelect();
        });        
        
        rs317ListModel = new DefaultListModel<>();
        rs317List = new JList<>(rs317ListModel);
        rs317List.setCellRenderer(new NpcListRenderer(false)); 
        rs317List.addListSelectionListener(e -> { if(!e.getValueIsAdjusting()) on317Select(); });
        
        // 3. Side Panels
        osrsListPanel = createListPanel("OSRS NPCs", osrsList, true);
        rs317ListPanel = createListPanel("317 NPCs", rs317List, false);
        
        // 4. Toolbar
        JToolBar toolbar = new JToolBar();
        JButton btnSwap = new JButton("Swap Sides (<->)");
        btnSwap.addActionListener(e -> swapSides());
        JButton btnSave = new JButton("Save Mappings");
        btnSave.addActionListener(e -> saveMappings());
        JButton btnConfirm = new JButton("CONFIRM MATCH");
        btnConfirm.setBackground(new Color(50, 150, 50));
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.addActionListener(e -> confirmMatch());
        
        toolbar.add(btnSwap);
        toolbar.add(Box.createHorizontalStrut(20));
        toolbar.add(btnConfirm);
        toolbar.add(Box.createHorizontalStrut(20));
        JButton btnAuto = new JButton("Run Auto-Convert");
        btnAuto.setBackground(new Color(70, 130, 180));
        btnAuto.setForeground(Color.WHITE);
        btnAuto.addActionListener(e -> runAutoConversion());
        toolbar.add(btnAuto);
        toolbar.add(Box.createHorizontalStrut(20));


        JButton btnAudit = new JButton("Dump Missing IDs");
        btnAudit.setToolTipText("Prints skipped/null NPCs to the Console");
        btnAudit.addActionListener(e -> auditMissingIds());
        toolbar.add(btnAudit);
        JButton btnLoadCustom = new JButton("Load fullOsrs.json");
        btnLoadCustom.setBackground(new Color(200, 100, 0));
        btnLoadCustom.setForeground(Color.WHITE);
        btnLoadCustom.addActionListener(e -> loadCustomOsrsData());
        toolbar.add(Box.createHorizontalStrut(10));
        toolbar.add(btnLoadCustom);
        
        versionSelector = new JComboBox<>(new String[]{"459 (Default)", "317"});
        versionSelector.addActionListener(e -> toggleVersion());
        toolbar.add(versionSelector);
        toolbar.add(Box.createHorizontalStrut(10));
        
        toolbar.add(btnSave);
        add(toolbar, BorderLayout.NORTH);

        
        // 5. Main Layout (Tabs)
        JTabbedPane mainTabs = new JTabbedPane();
        
        // --- COMPARE TAB ---
        JPanel comparePanel = new JPanel(new BorderLayout());
        
        // Split Pane for Renders
        mainContainer = new JPanel(new BorderLayout());
        renderSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        renderSplitPane.setResizeWeight(0.5);
        renderSplitPane.setContinuousLayout(true);
        setupLayoutPositions(); // Places lists and renderers
        
        comparePanel.add(mainContainer, BorderLayout.CENTER);
        
        // InfoBox (Bottom)
        infoPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        infoPanel.setPreferredSize(new Dimension(0, 180)); // Taller for details
        infoPanel.setBorder(BorderFactory.createTitledBorder("Comparison Details"));
        
        osrsInfoArea = new JTextPane(); 
        osrsInfoArea.setContentType("text/html");
        osrsInfoArea.setEditable(false);
        
        rs317InfoArea = new JTextPane(); 
        rs317InfoArea.setContentType("text/html");
        rs317InfoArea.setEditable(false);
        
        infoPanel.add(new JScrollPane(osrsInfoArea));
        infoPanel.add(new JScrollPane(rs317InfoArea));
        
        comparePanel.add(infoPanel, BorderLayout.SOUTH);
        mainTabs.addTab("Comparator", comparePanel);
        
        // --- MAPPED LIST TAB ---
        JPanel mappedTab = new JPanel(new BorderLayout());
        mappedModel = new DefaultListModel<>();
        mappedList = new JList<>(mappedModel);
        mappedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof MappingEntry) {
                    MappingEntry m = (MappingEntry) value;
                    setText("OSRS: " + m.osrsId + " (" + m.osrsName + ") <-> 317: " + m.rs317Id + " (" + m.rs317Name + ")");
                }
                return this;
            }
        });
        mappedList.addListSelectionListener(e -> viewMapping(mappedList.getSelectedValue()));
        
        JButton btnRemove = new JButton("Remove Selected Mapping");
        btnRemove.addActionListener(e -> removeMapping());
        
        mappedTab.add(new JScrollPane(mappedList), BorderLayout.CENTER);
        mappedTab.add(btnRemove, BorderLayout.SOUTH);
        
        mainTabs.addTab("Mapped List", mappedTab);
     // --- UNMATCHED TAB ---
        JPanel unmatchedTab = new JPanel(new BorderLayout());
        unmatchedModel = new DefaultListModel<>();
        unmatchedList = new JList<>(unmatchedModel);
        // Use HTML renderer for unmatched too
        unmatchedList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof String) {
                    String s = (String) value;
                    if (s.startsWith("<html>")) setText(s);
                }
                return this;
            }
        });
        unmatchedTab.add(new JScrollPane(unmatchedList), BorderLayout.CENTER);
        mainTabs.addTab("Unmatched Audit", unmatchedTab);
        
        add(mainTabs, BorderLayout.CENTER);
        
        populateLists();
        loadMappings();
        
        SwingUtilities.invokeLater(() -> renderSplitPane.setDividerLocation(0.5));
    }
    private void toggleVersion() {
        String sel = (String) versionSelector.getSelectedItem();
        is459Mode = sel.startsWith("459");
        
        // Refresh the Right-Hand List
        populateLists(); 
        
        // Clear current right-side selection
        selected317Id = -1;
        current317Npc = null;
        if (rs317InfoArea != null) rs317InfoArea.setText("");
        
        // [FIX] Force the layout to swap the render panel (459 <-> 317)
        setupLayoutPositions();
        
        // Re-filter lists
        filter(false, "");
    }
    
    // --- Layout Logic ---
    
    private void setupLayoutPositions() {
        mainContainer.removeAll();
        
        // Determine which panel goes on the Right (or Left if swapped)
        JPanel rightList = rs317ListPanel; // Reused for 459 list
        Component rightRender = is459Mode ? rs459RenderPanel : rs317RenderPanel;
        
        if (isOsrsLeft) {
            mainContainer.add(osrsListPanel, BorderLayout.WEST);
            mainContainer.add(rightList, BorderLayout.EAST);
            renderSplitPane.setLeftComponent(osrsRenderPanel);
            renderSplitPane.setRightComponent(rightRender);
        } else {
            mainContainer.add(rightList, BorderLayout.WEST);
            mainContainer.add(osrsListPanel, BorderLayout.EAST);
            renderSplitPane.setLeftComponent(rightRender);
            renderSplitPane.setRightComponent(osrsRenderPanel);
        }
        
        mainContainer.add(renderSplitPane, BorderLayout.CENTER);
        mainContainer.revalidate();
        mainContainer.repaint();
        SwingUtilities.invokeLater(() -> renderSplitPane.setDividerLocation(0.5));
    }
    
    
    private JPanel createListPanel(String title, JList<String> list, boolean isOsrs) {
        JPanel p = new JPanel(new BorderLayout());
        p.setPreferredSize(new Dimension(260, 0));
        
        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(new Font("SansSerif", Font.BOLD, 14));
        
        JTextField search = new JTextField();
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { filter(isOsrs, search.getText()); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { filter(isOsrs, search.getText()); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filter(isOsrs, search.getText()); }
        });
        
        JPanel top = new JPanel(new BorderLayout());
        top.add(lblTitle, BorderLayout.NORTH);
        top.add(search, BorderLayout.SOUTH);
        
        p.add(top, BorderLayout.NORTH);
        p.add(new JScrollPane(list), BorderLayout.CENTER);
        return p;
    }
    
    private void filter(boolean isOsrs, String query) {
        String q = query.toLowerCase();
        DefaultListModel<String> model = isOsrs ? osrsListModel : rs317ListModel;
        List<String> source = isOsrs ? osrsNamesFull : rs317NamesFull;
        
        model.clear();
        for (String s : source) {
            if (q.isEmpty() || s.toLowerCase().contains(q)) model.addElement(s);
        }
    }
    
    private void swapSides() {
        isOsrsLeft = !isOsrsLeft;
        setupLayoutPositions();
        
        // [FIX] Uses the class field 'infoPanel'
        if (infoPanel != null) {
            infoPanel.removeAll();
            if (isOsrsLeft) {
                infoPanel.add(new JScrollPane(osrsInfoArea));
                infoPanel.add(new JScrollPane(rs317InfoArea));
            } else {
                infoPanel.add(new JScrollPane(rs317InfoArea));
                infoPanel.add(new JScrollPane(osrsInfoArea));
            }
            infoPanel.revalidate();
            infoPanel.repaint();
        }
    }
    
    private int extractId(String val) {
        if (val == null) return -1;
        try {
            // Find "ID: " first
            String marker = "ID: ";
            int start = val.indexOf(marker);
            if (start == -1) return -1;
            start += marker.length();
            
            // Read forward until we hit a non-digit (like a space or HTML tag)
            int end = start;
            while (end < val.length() && Character.isDigit(val.charAt(end))) {
                end++;
            }
            
            if (start == end) return -1; // No digits found
            return Integer.parseInt(val.substring(start, end));
        } catch (Exception e) {
            return -1;
        }
    }    
    private void onOsrsSelect() {
        if (isProgrammaticUpdate) return;
        
        int id = extractId(osrsList.getSelectedValue());
        if (id == -1) return;
        
        selectedOsrsId = id;
        
        // [FIX] Check if we are in Custom Mode or Default RuneLite Mode
        if (isCustomOsrsMode) {
            NpcDefFullOsrs def = customOsrsMap.get(id);
            if (def != null) {
                SwingUtilities.invokeLater(() -> {
                    currentOsrsNpc = new LightweightNpc(def);
                    // Use the custom load method we created earlier!
                    int[] src = (currentOsrsNpc.srcColors != null) ? currentOsrsNpc.srcColors : new int[0];
                    int[] dst = (currentOsrsNpc.dstColors != null) ? currentOsrsNpc.dstColors : new int[0];
                    osrsRenderPanel.loadCustomNpc(def.models, src, dst);
                    updateInfoPanels();
                });
            }
        } else {
            // Default RuneLite Cache Behavior
            plugin.getClientThread().invoke(() -> {
                NPCComposition def = plugin.getClient().getNpcDefinition(id);
                SwingUtilities.invokeLater(() -> {
                    currentOsrsNpc = new LightweightNpc(id, def);
                    osrsRenderPanel.loadNpc(def);
                    updateInfoPanels();
                });
            });
        }
        
        if (isOsrsLeft) {
            LightweightNpc source = getOsrsData(id);
            if (source != null) rankAndDisplayMatches(source, false);
        }
    }    
    private void on317Select() {
        if (isProgrammaticUpdate) return;
        
        int id = extractId(rs317List.getSelectedValue());
        if (id == -1) return;
        
        selected317Id = id; // reuse variable for 459 ID
        
        if (is459Mode) {
            // [NEW] 459 Logic
            NpcDef459 def = cache459.stream().filter(n -> n.id == id).findFirst().orElse(null);
            if (def != null) {
                // Convert to Lightweight for InfoBox
                current317Npc = new LightweightNpc(def); 
                updateInfoPanels();
                
                // RENDER using OSRS Assets (Hijack the 317 panel or use a new one)
                // Since RenderPanel317 is strictly for 317 cache, we should arguably swap the component
                // or just use RenderPanelOSRS logic.
                // Hack: Pass the data to the RIGHT render panel if we can, or just re-purpose.
                // Ideally: mainContainer should have swapped the Right Panel to a RenderPanelOSRS instance.
                
                // For this snippet, assuming you want to see it:
                // We need to render it on the RIGHT side. 
                // Let's assume you added a method to 'rs317RenderPanel' or replaced it.
                // BETTER PLAN: Use 'osrsRenderPanel' to render it momentarily? No, that confuses the view.
                
                // FIX: Let's assume you created a 'RenderPanelOSRS' instance called 'rs459RenderPanel'
                // and added it to the layout in 'setupLayoutPositions'.
                // For now, let's just assume we call the helper we added to RenderPanelOSRS:
                // But wait, rs317RenderPanel is a RenderPanel317 class. 
                // We cannot call loadCustomNpc on it.
                
                // [ACTION REQUIRED] You must instantiate a second RenderPanelOSRS for the right side!
                // See step F below.
                
                rs459RenderPanel.loadCustomNpc(def.models, def.originalColors, def.modifiedColors);
            }
        } else {
            // Existing 317 Logic
            RS317Npc def = cache317.get(id);
            if (def != null) {
                current317Npc = new LightweightNpc(def);
                rs317RenderPanel.loadNpc(def);
                updateInfoPanels();
            }
        }
        
        // Auto-Search Logic...
        if (!isOsrsLeft) {
            // pass current317Npc (which is now 459 compatible)
            rankAndDisplayMatches(current317Npc, true);
        }
    }

    private void rankAndDisplayMatches(LightweightNpc source, boolean searchInOsrs) {
        new Thread(() -> {
            try {
                List<ScoredMatch> matches = new ArrayList<>();
                List<LightweightNpc> targetCache;
                
                // [FIX] Select the correct cache based on mode
                if (searchInOsrs) {
                    targetCache = osrsCache;
                } else {
                    if (is459Mode) {
                        targetCache = cache459.stream().map(LightweightNpc::new).collect(Collectors.toList());
                    } else {
                        targetCache = cache317.values().stream().map(LightweightNpc::new).collect(Collectors.toList());
                    }
                }

                for (LightweightNpc tgt : targetCache) {
                    if (tgt.name == null) continue; 
                    double score = calculateScore(source, tgt);
                    if (score > 0.3) matches.add(new ScoredMatch(tgt, score));
                }
                
                matches.sort((a, b) -> Double.compare(b.score, a.score));

                SwingUtilities.invokeLater(() -> {
                    try {
                        isProgrammaticUpdate = true; 
                        
                        DefaultListModel<String> targetModel = searchInOsrs ? osrsListModel : rs317ListModel;
                        JList<String> targetList = searchInOsrs ? osrsList : rs317List;
                        
                        targetModel.clear();
                        int limit = Math.min(matches.size(), 50);
                        
                        for (int i = 0; i < limit; i++) {
                            ScoredMatch m = matches.get(i);
                            String color = m.score >= 0.95 ? "#00FF00" : (m.score >= 0.5 ? "#FFA500" : "#FF0000");
                            String opts = m.npc.actions == null ? "[]" : Arrays.toString(m.npc.actions).replace("null", "-");
                            String html = String.format("<html><font color='%s'><b>[%.0f%%]</b></font> ID: %d %s <font color='#AAAAAA'>(Lvl: %d)</font><br><font size='3'>Opts: %s</font></html>", 
                                color, m.score * 100, m.npc.id, m.npc.name, m.npc.combatLevel, opts);
                            targetModel.addElement(html);
                        }
                        
                        if (!matches.isEmpty()) {
                            targetList.setSelectedIndex(0);
                            targetList.ensureIndexIsVisible(0);
                            
                            // Manually render best match
                            int bestId = matches.get(0).npc.id;
                            if (searchInOsrs) {
                                selectedOsrsId = bestId;
                                plugin.getClientThread().invoke(() -> {
                                    NPCComposition def = plugin.getClient().getNpcDefinition(bestId);
                                    SwingUtilities.invokeLater(() -> {
                                        currentOsrsNpc = new LightweightNpc(bestId, def);
                                        osrsRenderPanel.loadNpc(def);
                                        updateInfoPanels();
                                    });
                                });
                            } else {
                                selected317Id = bestId;
                                // [FIX] Handle 459 or 317 Best Match Rendering
                                if (is459Mode) {
                                    NpcDef459 def = cache459.stream().filter(n -> n.id == bestId).findFirst().orElse(null);
                                    if (def != null) {
                                        current317Npc = new LightweightNpc(def);
                                        rs459RenderPanel.loadCustomNpc(def.models, def.originalColors, def.modifiedColors);
                                    }
                                } else {
                                    RS317Npc def = cache317.get(bestId);
                                    if (def != null) {
                                        current317Npc = new LightweightNpc(def);
                                        rs317RenderPanel.loadNpc(def);
                                    }
                                }
                                updateInfoPanels();
                            }
                        } else {
                            targetModel.addElement("No match found.");
                        }
                    } finally {
                        isProgrammaticUpdate = false; 
                    }
                });
            } catch (Exception e) { 
                e.printStackTrace(); 
                isProgrammaticUpdate = false; 
            }
        }).start();
    }
    
    
    
    
    
    private double calculateScore(LightweightNpc src, LightweightNpc tgt) {
        if (tgt.name == null || tgt.name.equalsIgnoreCase("null")) return 0;
        
        // --- 1. Calculate Fuzzy Scores (for sorting) ---
        
        // Name (25%)
        double nameScore = getSimilarity(src.name, tgt.name);
        
        // Combat (10%)
        double combatScore = (src.combatLevel == tgt.combatLevel) ? 1.0 : 0.0;
        if (src.combatLevel > 0 || tgt.combatLevel > 0) {
            int diff = Math.abs(src.combatLevel - tgt.combatLevel);
            int max = Math.max(src.combatLevel, tgt.combatLevel);
            if (max > 0) combatScore = 1.0 - ((double)diff / max);
        } else combatScore = 1.0; 

        // Actions (15%)
        double actionScore = 0;
        if (src.actions == null && tgt.actions == null) actionScore = 1.0;
        else if (src.actions != null && tgt.actions != null) {
            int matches = 0, total = 0;
            for (String s : src.actions) {
                if (s != null) {
                    total++;
                    for (String t : tgt.actions) {
                        if (t != null && t.equalsIgnoreCase(s)) { matches++; break; }
                    }
                }
            }
            actionScore = (total > 0) ? (double) matches / total : 1.0;
        }

        // Colors (20%) - Check BOTH Original and Modified colors
        double srcColorScore = getIntArrayScore(src.srcColors, tgt.srcColors);
        double dstColorScore = getIntArrayScore(src.dstColors, tgt.dstColors);
        double colorScore = (srcColorScore + dstColorScore) / 2.0;

        // Models (30%)
        double modelScore = getIntArrayScore(src.models, tgt.models);

        double weightedScore = (nameScore * 0.25) + (combatScore * 0.10) + (actionScore * 0.15) + (colorScore * 0.20) + (modelScore * 0.30);
        
        // --- 2. Strict "100%" Validation ---
        // If anything is not EXACTLY identical, cap the score at 0.99.
        
        boolean nameExact = src.name.equalsIgnoreCase(tgt.name);
        boolean combatExact = src.combatLevel == tgt.combatLevel;
        // Check arrays strictly (order matters for exact 100% match)
        boolean actsExact = Arrays.equals(src.actions, tgt.actions); 
        boolean modelsExact = safeArraysEquals(src.models, tgt.models);
        boolean colorsExact = safeArraysEquals(src.srcColors, tgt.srcColors) && 
                              safeArraysEquals(src.dstColors, tgt.dstColors);

        boolean isPerfect = nameExact && combatExact && actsExact && modelsExact && colorsExact;
        
        if (isPerfect) return 1.0;
        
        // Cap at 0.99 if not perfect, even if weighted math rounded to 1.0
        return Math.min(0.99, weightedScore);
    }
    
    private boolean safeArraysEquals(int[] a, int[] b) {
        boolean aEmpty = (a == null || a.length == 0);
        boolean bEmpty = (b == null || b.length == 0);
        if (aEmpty && bEmpty) return true; // Both empty -> Equal
        if (aEmpty != bEmpty) return false; // One empty -> Not Equal
        return Arrays.equals(a, b); // Strict order check
    }
    private double getSimilarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) { longer = s2; shorter = s1; }
        int longerLength = longer.length();
        if (longerLength == 0) return 1.0;
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }
    
    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase(); s2 = s2.toLowerCase();
        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) costs[j] = j;
                else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) costs[s2.length()] = lastValue;
        }
        return costs[s2.length()];
    }

    // --- Helpers & Loading ---

    private LightweightNpc getOsrsData(int id) {
        for (LightweightNpc n : osrsCache) { if (n.id == id) return n; }
        return null;
    }

    private void load317Cache() {
        try {
            File path = new File("C:\\Users\\dakot\\Server_Workspace\\RuneScape_Server\\data\\cache_377\\");
            if (!path.exists()) return;
            
            RandomAccessFile data = new RandomAccessFile(new File(path, "main_file_cache.dat"), "r");
            RandomAccessFile idx0 = new RandomAccessFile(new File(path, "main_file_cache.idx0"), "r");
            RandomAccessFile idx1 = new RandomAccessFile(new File(path, "main_file_cache.idx1"), "r");
            
            RS317Decompressor configDec = new RS317Decompressor(data, idx0, 0);
            this.modelDecompressor317 = new RS317Decompressor(data, idx1, 1);
            
            byte[] configRaw = configDec.decompress(2);
            RS317Archive archive = new RS317Archive(configRaw);
            
            byte[] dat = archive.getFile("npc.dat");
            byte[] idx = archive.getFile("npc.idx");
            RS317Stream ds = new RS317Stream(dat);
            RS317Stream is = new RS317Stream(idx);
            
            int count = is.readUnsignedWord();
            int offset = 2;
            for (int i = 0; i < count; i++) {
                ds.offset = offset;
                try {
                    RS317Npc def = RS317Npc.decode(i, ds);
                    cache317.put(i, def);
                } catch(Exception e) {}
                offset += is.readUnsignedWord();
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private void populateLists() {
        plugin.getClientThread().invoke(() -> {
            osrsCache.clear();
            osrsNamesFull.clear();
            // [FIX] Increased limit to 50,000 to catch new OSRS NPCs
            for (int i = 0; i < 50000; i++) {
                NPCComposition def = plugin.getClient().getNpcDefinition(i);
                if (def != null && def.getName() != null && !def.getName().equals("null")) {
                    osrsCache.add(new LightweightNpc(i, def));
                    osrsNamesFull.add("ID: " + i + " " + def.getName() + " (Lvl: " + def.getCombatLevel() + ")");
                }
            }
            SwingUtilities.invokeLater(() -> filter(true, ""));
        });
        
        if (is459Mode) {
            rs317NamesFull.clear(); // We reuse this list for the UI filter
            for (NpcDef459 def : cache459) {
                rs317NamesFull.add("ID: " + def.id + " " + def.name + " (Lvl: " + def.combatLevel + ")");
            }
        } else {
            rs317NamesFull.clear();
            for (RS317Npc def : cache317.values()) {
                if (def.name != null && !def.name.equals("null")) {
                    rs317NamesFull.add("ID: " + def.id + " " + def.name + " (Lvl: " + def.combatLevel + ")");
                }
            }
        }
        filter(false, "");
   
    }
    
    private void confirmMatch() {
        if (selectedOsrsId == -1 || selected317Id == -1) return;
        
        // Calculate score for this manual match so it sorts correctly
        LightweightNpc osrs = getOsrsData(selectedOsrsId);
        LightweightNpc rs317 = new LightweightNpc(cache317.get(selected317Id));
        double score = calculateScore(osrs, rs317);
        
        MappingEntry entry = new MappingEntry(selectedOsrsId, "OSRS " + selectedOsrsId, selected317Id, rs317.name, 0, score);
        
        mappings.add(entry);
        mappedModel.addElement(entry);
        saveMappings();
        rebuildMappingCache();
        JOptionPane.showMessageDialog(this, "Mapped: " + selectedOsrsId + " -> " + selected317Id);
    }
    private void runAutoConversion() {
        String modeName = is459Mode ? "459" : "317";
        int confirm = JOptionPane.showConfirmDialog(this, 
            "This will scan ALL OSRS NPCs and auto-map to " + modeName + " if match >= 69%.\nThis may take a moment. Continue?", 
            "Auto-Convert (" + modeName + ")", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        new Thread(() -> {
            try {
                Set<Integer> alreadyMapped = mappings.stream().map(m -> m.osrsId).collect(Collectors.toSet());
                List<MappingEntry> newMatches = new ArrayList<>();
                List<UnmatchedEntry> failedMatches = new ArrayList<>();
                
                // [FIX] Build target list based on selected version
                List<LightweightNpc> targets;
                if (is459Mode) {
                    targets = cache459.stream().map(LightweightNpc::new).collect(Collectors.toList());
                } else {
                    targets = cache317.values().stream().map(LightweightNpc::new).collect(Collectors.toList());
                }
                
                for (LightweightNpc src : osrsCache) {
                    if (alreadyMapped.contains(src.id)) continue;
                    
                    double bestScore = -1.0;
                    LightweightNpc bestTarget = null;
                    
                    for (LightweightNpc tgt : targets) {
                        double s = calculateScore(src, tgt);
                        if (s > bestScore) {
                            bestScore = s;
                            bestTarget = tgt;
                        }
                    }
                    
                    if (bestScore >= 0.69 && bestTarget != null) {
                        newMatches.add(new MappingEntry(src.id, src.name, bestTarget.id, bestTarget.name, src.combatLevel, bestScore));
                    } else {
                        failedMatches.add(new UnmatchedEntry(src, bestTarget, bestScore));
                    }
                }
                
                mappings.addAll(newMatches);
                mappings.sort((a, b) -> Double.compare(b.score, a.score));
                failedMatches.sort((a, b) -> Double.compare(b.score, a.score));
                
                SwingUtilities.invokeLater(() -> {
                    mappedModel.clear();
                    for (MappingEntry m : mappings) mappedModel.addElement(m);
                    
                    unmatchedModel.clear();
                    for (UnmatchedEntry u : failedMatches) {
                        String color = u.score >= 0.5 ? "#FFA500" : "#FF4444";
                        String bestName = (u.bestMatch != null) ? u.bestMatch.name : "None";
                        String html = String.format("<html>ID: %d <b>%s</b> (Best: %s - <font color='%s'>%.0f%%</font>)</html>",
                             u.src.id, u.src.name, bestName, color, u.score * 100);
                        unmatchedModel.addElement(html);
                    }
                    
                    saveMappings();
                    rebuildMappingCache();
                    JOptionPane.showMessageDialog(this, 
                        "Auto-Convert Complete (" + modeName + ")!\n" +
                        "New Matches: " + newMatches.size() + "\n" +
                        "Unmatched: " + failedMatches.size());
                });
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }    
    // Helper class for the Unmatched Tab
    private static class UnmatchedEntry {
        LightweightNpc src;
        LightweightNpc bestMatch;
        double score;
        public UnmatchedEntry(LightweightNpc src, LightweightNpc bestMatch, double score) {
            this.src = src; this.bestMatch = bestMatch; this.score = score;
        }
    }
    private void saveMappings() {
        try (FileWriter writer = new FileWriter(saveFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(mappings, writer);
            JOptionPane.showMessageDialog(this, "Saved " + mappings.size() + " mappings successfully!");
        } catch (Exception e) { 
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage());
        }
    }
    
    private void loadMappings() {
        if (!saveFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(saveFile))) {
            List<MappingEntry> list = new Gson().fromJson(reader, new TypeToken<List<MappingEntry>>(){}.getType());
            if (list != null) {
                mappings = list;
                if (mappedModel != null) {
                    mappedModel.clear();
                    for (MappingEntry m : mappings) mappedModel.addElement(m);
                }
            }
            rebuildMappingCache(); 
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    
    private void removeMapping() {
        MappingEntry e = mappedList.getSelectedValue();
        if (e != null) { 
            mappings.remove(e); 
            mappedModel.removeElement(e); 
            saveMappings(); 
            // [FIX] Rebuilds cache so the green highlight disappears immediately
            rebuildMappingCache(); 
        }
    }    
    private void viewMapping(MappingEntry e) {
        if (e == null) return;
        selectedOsrsId = e.osrsId; selected317Id = e.rs317Id;
        
        plugin.getClientThread().invoke(() -> {
            NPCComposition def = plugin.getClient().getNpcDefinition(e.osrsId);
            SwingUtilities.invokeLater(() -> {
                currentOsrsNpc = new LightweightNpc(e.osrsId, def); // [FIX]
                osrsRenderPanel.loadNpc(def);
                updateInfoPanels();
            });
        });
        
        RS317Npc def = cache317.get(e.rs317Id);
        current317Npc = new LightweightNpc(def); // [FIX]
        rs317RenderPanel.loadNpc(def);
        updateInfoPanels();
    }
    
    private void updateInfoPanels() {
        if (osrsInfoArea != null) osrsInfoArea.setText(generateDiffHtml(currentOsrsNpc, current317Npc, true));
        if (rs317InfoArea != null) rs317InfoArea.setText(generateDiffHtml(current317Npc, currentOsrsNpc, false));
        
        // Scroll to top
        SwingUtilities.invokeLater(() -> {
            osrsInfoArea.setCaretPosition(0);
            rs317InfoArea.setCaretPosition(0);
        });
    }

    private void updateInfoBox(boolean isOsrs, LightweightNpc npc) {
        if (isOsrs) currentOsrsNpc = npc; else current317Npc = npc;
        
        if (osrsInfoArea != null) osrsInfoArea.setText(generateDiffHtml(currentOsrsNpc, current317Npc, true));
        if (rs317InfoArea != null) rs317InfoArea.setText(generateDiffHtml(current317Npc, currentOsrsNpc, false));
        
        SwingUtilities.invokeLater(() -> {
            if (osrsInfoArea != null) osrsInfoArea.setCaretPosition(0);
            if (rs317InfoArea != null) rs317InfoArea.setCaretPosition(0);
        });
    }

    private String generateDiffHtml(LightweightNpc self, LightweightNpc other, boolean isOsrs) {
        if (self == null) return "";
        
        String title = isOsrs ? "OSRS NPC" : "317 NPC";
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif; font-size:10px; background-color:#222; color:#DDD;'>");
        sb.append("<div style='font-size:11px; font-weight:bold; color:#CCC; margin-bottom:3px;'>").append(title).append("</div>");
        
        boolean hasOther = (other != null);
        
        // ID
        sb.append("<b>ID:</b> ").append(self.id).append("<br>");
        
        // Name
        boolean nameMatch = hasOther && self.name.equalsIgnoreCase(other.name);
        sb.append("<b>Name:</b> ").append(colorVal(self.name, hasOther, nameMatch)).append("<br>");
        
        // Combat
        boolean cmbMatch = hasOther && self.combatLevel == other.combatLevel;
        sb.append("<b>Combat:</b> ").append(colorVal(String.valueOf(self.combatLevel), hasOther, cmbMatch)).append("<br>");
        
        // Models (Granular Diff)
        sb.append("<b>Models:</b> ");
        if (self.models == null) sb.append("None");
        else {
            sb.append("[");
            Set<Integer> otherModels = new HashSet<>();
            if (hasOther && other.models != null) for(int m : other.models) otherModels.add(m);
            
            for (int i = 0; i < self.models.length; i++) {
                boolean match = hasOther && otherModels.contains(self.models[i]);
                if (i > 0) sb.append(", ");
                sb.append(colorVal(String.valueOf(self.models[i]), hasOther, match));
            }
            sb.append("]");
        }
        sb.append("<br>");
        
        // Options (Granular Diff)
        sb.append("<b>Options:</b> ");
        if (self.actions == null) sb.append("[]");
        else {
            sb.append("[");
            // Simple index-based comparison for options
            for (int i = 0; i < self.actions.length; i++) {
                String sAct = self.actions[i];
                String oAct = (hasOther && other.actions != null && i < other.actions.length) ? other.actions[i] : null;
                
                if (sAct == null) sAct = "-";
                if (oAct == null) oAct = "-";
                
                boolean match = hasOther && sAct.equalsIgnoreCase(oAct);
                if (i > 0) sb.append(", ");
                sb.append(colorVal(sAct, hasOther, match));
            }
            sb.append("]");
        }
        sb.append("<br>");
        
        // Colors (Granular Diff)
        if (self.srcColors != null && self.srcColors.length > 0) {
            sb.append("<b>Colors (Orig->Mod):</b><br>");
            
            // Build map of Other's replacements for comparison
            Map<Integer, Integer> otherColors = new HashMap<>();
            if (hasOther && other.srcColors != null) {
                for(int i=0; i<other.srcColors.length; i++) {
                    int d = (other.dstColors != null && i < other.dstColors.length) ? other.dstColors[i] : -1;
                    otherColors.put(other.srcColors[i], d);
                }
            }
            
            for (int i = 0; i < self.srcColors.length; i++) {
                int s = self.srcColors[i];
                int d = (self.dstColors != null && i < self.dstColors.length) ? self.dstColors[i] : -1;
                
                boolean match = false;
                if (hasOther && otherColors.containsKey(s)) {
                    if (otherColors.get(s) == d) match = true;
                }
                
                sb.append("&nbsp;&nbsp;").append(s).append(" -> ").append(colorVal(String.valueOf(d), hasOther, match)).append("<br>");
            }
        } else {
            sb.append("<b>Colors:</b> Default");
        }
        
        sb.append("</body></html>");
        return sb.toString();
    }
    
    private String colorVal(String text, boolean comparing, boolean match) {
        if (!comparing) return text; // No color if nothing to compare against
        if (match) return "<font color='#00FF00'>" + text + "</font>"; // Green
        return "<font color='#FF4444'>" + text + "</font>"; // Red
    }
    
    private String formatDiff(String text, boolean isDiff) {
        if (isDiff) return "<font color='#FF4444'><b>" + text + "</b></font>"; // Shining Red
        return text;
    }
    // --- Internal Classes ---
    
    private static class LightweightNpc {
        int id;
        String name;
        int combatLevel;
        String[] actions;
        int[] models;      // [NEW]
        int[] srcColors;   // [NEW]
        int[] dstColors;   // [NEW]
        String description;

        
        public LightweightNpc(int id, NPCComposition def) {
            this.id = id;
            this.name = def.getName();
            this.combatLevel = def.getCombatLevel();
            this.actions = def.getActions();
            this.models = def.getModels();
            this.description = "N/A"; 

            short[] f = def.getColorToReplace();
            short[] r = def.getColorToReplaceWith();
            if (f != null) {
                this.srcColors = new int[f.length];
                for(int i=0; i<f.length; i++) this.srcColors[i] = f[i];
            }
            if (r != null) {
                this.dstColors = new int[r.length];
                for(int i=0; i<r.length; i++) this.dstColors[i] = r[i];
            }
        }
        public LightweightNpc(NpcDef459 def) {
            this.id = def.id;
            this.name = def.name;
            this.combatLevel = def.combatLevel;
            this.actions = def.actions;
            this.models = def.models;
            this.srcColors = def.originalColors;
            this.dstColors = def.modifiedColors;
            this.description = "459 Def";
        }
        public LightweightNpc(RS317Npc def) {
            this.id = def.id;
            this.name = def.name;
            this.combatLevel = def.combatLevel;
            this.actions = def.actions;
            this.models = def.models;
            this.srcColors = def.originalColors;
            this.dstColors = def.modifiedColors;
            this.description = (def.description != null) ? new String(def.description) : "None";

        }
        public LightweightNpc(NpcDefFullOsrs def) {
            this.id = def.id;
            this.name = def.name;
            this.combatLevel = def.combatLevel;
            this.actions = def.actions;
            this.models = def.models;
            this.description = "Custom JSON";
            
            if (def.colourReplacements != null) {
                this.srcColors = new int[def.colourReplacements.size()];
                this.dstColors = new int[def.colourReplacements.size()];
                for (int i = 0; i < def.colourReplacements.size(); i++) {
                    this.srcColors[i] = def.colourReplacements.get(i).original;
                    this.dstColors[i] = def.colourReplacements.get(i).replacement;
                }
            }
        }
    }    
    private static class ScoredMatch {
        LightweightNpc npc;
        double score;
        public ScoredMatch(LightweightNpc npc, double score) { this.npc = npc; this.score = score; }
    }
    
    private void rebuildMappingCache() {
        osrsTo317Map.clear();
        rs317ToOsrsMap.clear();
        for (MappingEntry m : mappings) {
            osrsTo317Map.computeIfAbsent(m.osrsId, k -> new ArrayList<>()).add(m.rs317Id);
            rs317ToOsrsMap.computeIfAbsent(m.rs317Id, k -> new ArrayList<>()).add(m.osrsId);
        }
        if (osrsList != null) osrsList.repaint();
        if (rs317List != null) rs317List.repaint();
    }

    private class NpcListRenderer extends DefaultListCellRenderer {
        private final boolean isOsrs;
        public NpcListRenderer(boolean isOsrs) { this.isOsrs = isOsrs; }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value instanceof String) {
                String text = (String) value;
                int id = extractId(text);
                List<Integer> mappedIds = isOsrs ? osrsTo317Map.get(id) : rs317ToOsrsMap.get(id);
                
                if (mappedIds != null && !mappedIds.isEmpty()) {
                    if (!isSelected) {
                        setBackground(new Color(34, 139, 34)); // Dark Green
                        setForeground(Color.WHITE);
                    }
                    String mapStr = mappedIds.toString().replace("[", "").replace("]", "");
                    if (text.startsWith("<html>")) {
                        String clean = text.replace("</html>", "");
                        setText(clean + " <b>(Map: " + mapStr + ")</b></html>");
                    } else {
                        setText(text + " (Map: " + mapStr + ")");
                    }
                }
            }
            return this;
        }
    }    
    private double getIntArrayScore(int[] a, int[] b) {
        boolean aHas = a != null && a.length > 0;
        boolean bHas = b != null && b.length > 0;
        
        // If both empty/null -> Perfect match (both have "nothing")
        if (!aHas && !bHas) return 1.0;
        // If one has data and the other doesn't -> Complete mismatch
        if (aHas != bHas) return 0.0;
        
        // Calculate Intersection vs Union-ish (Max length)
        int matches = 0;
        int total = Math.max(a.length, b.length);
        Set<Integer> sSet = new HashSet<>();
        for(int val : a) sSet.add(val);
        
        for(int val : b) {
            if(sSet.contains(val)) matches++;
        }
        
        return (double) matches / total;
    }
    private void auditMissingIds() {
        // [FIX] Runs on the Client Thread (prevents crash)
        plugin.getClientThread().invoke(() -> {
            System.out.println("=== AUDIT START: Scanning for Missing/Null NPCs (0-50000) ===");
            int missingCount = 0;
            
            for (int i = 0; i < 8000; i++) {
                NPCComposition def = plugin.getClient().getNpcDefinition(i);
                
                // If def is null, the ID is completely empty (no data exists)
                if (def == null) continue;
                
                String name = def.getName();
                boolean isNameNull = (name == null || name.equalsIgnoreCase("null"));
                
                // Dump IDs that exist in cache but are "hidden" (named null)
                if (isNameNull) {
                    missingCount++;
                    String models = def.getModels() == null ? "None" : Arrays.toString(def.getModels());
                    String actions = def.getActions() == null ? "[]" : Arrays.toString(def.getActions());
                    
                    // Prints data for the skipped ID (e.g., 2877)
                    System.out.println(String.format("SKIPPED ID: %-5d | Name: %-6s | Lvl: %-3d | Models: %s | Acts: %s", 
                        i, name, def.getCombatLevel(), models, actions));
                }
            }
            System.out.println("=== AUDIT COMPLETE: Found " + missingCount + " skipped NPCs ===");
        });
    }
    
    private void loadCustomOsrsData() {
        new Thread(() -> {
            System.out.println("Loading custom OSRS data from JSON...");
            List<NpcDefFullOsrs> customData = NpcLoaderFullOsrs.load();
            
            if (customData.isEmpty()) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Failed to load fullOsrs.json or file empty!"));
                return;
            }
            int customCount = 0;

            // We need to fetch cache defs on the client thread if we fallback
            plugin.getClientThread().invoke(() -> {
                List<LightweightNpc> newCache = new ArrayList<>();
                List<String> newNames = new ArrayList<>();
                Map<Integer, NpcDefFullOsrs> newMap = new HashMap<>();
                
                int customCount1 = 0;
                int fallbackCount = 0;
                Set<Integer> processedIds = new HashSet<>();

                for (NpcDefFullOsrs def : customData) {
                    // 1. Deduplicate
                    if (processedIds.contains(def.id)) continue;
                    processedIds.add(def.id);

                    // 2. Validate (Models & Colors must exist)
                    boolean hasModels = def.models != null && def.models.length > 0;
                    boolean hasColors = def.colourReplacements != null && !def.colourReplacements.isEmpty();
                    
                    if (hasModels && hasColors) {
                        // --- GOOD DATA: Use Custom ---
                        
                        // 3. Clean Options ("None" -> null)
                        if (def.actions != null) {
                            for (int i = 0; i < def.actions.length; i++) {
                                if ("None".equalsIgnoreCase(def.actions[i])) {
                                    def.actions[i] = null;
                                }
                            }
                        }

                        newMap.put(def.id, def);
                        LightweightNpc lwn = new LightweightNpc(def);
                        newCache.add(lwn);
                        newNames.add("ID: " + def.id + " " + def.name + " (Lvl: " + def.combatLevel + ")");
                        customCount1++;
                     //   customCount = customCount1;
                    } else {
                        // --- BAD DATA: Fallback to OSRS Cache ---
                        // "Do not replace the npc" -> Load the original from RuneLite cache
                        NPCComposition osrsDef = plugin.getClient().getNpcDefinition(def.id);
                        if (osrsDef != null) {
                            LightweightNpc lwn = new LightweightNpc(def.id, osrsDef);
                            newCache.add(lwn);
                            newNames.add("ID: " + def.id + " " + osrsDef.getName() + " (Lvl: " + osrsDef.getCombatLevel() + ") [ORIG]");
                            fallbackCount++;
                        }
                    }
                }

                // Update UI on Swing Thread
                SwingUtilities.invokeLater(() -> {
                    isCustomOsrsMode = true;
                    osrsCache = newCache;
                    osrsNamesFull = newNames;
                    customOsrsMap = newMap;
                    
                    filter(true, ""); // Refresh List
                    
                    JOptionPane.showMessageDialog(this, 
                        "Custom Load Complete!\n" +
                       // "Replaced (Custom): " + customCount + "\n" +
                        //"Kept Original (Invalid Custom): " + fallbackCount + "\n" +
                        "Total Unique IDs: " + processedIds.size());
                });
            });
        }).start();
    }
    
    
}