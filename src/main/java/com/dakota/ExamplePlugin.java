package com.dakota;

import com.google.gson.GsonBuilder;
import javax.inject.Inject;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import java.util.HashSet;
import java.util.Set;
import net.runelite.api.events.ClientTick; // <--- Add this
import java.util.Arrays; // <--- Add this

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap; // Keeps quests sorted alphabetically if desired
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text; // Helper for cleaning text
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import javax.swing.JTabbedPane;
import java.util.concurrent.ConcurrentHashMap;
import okhttp3.ResponseBody;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import java.lang.reflect.Field;
import net.runelite.api.Varbits;
import net.runelite.api.VarPlayer;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.Dimension;
import src.main.java.com.model.comparer.*;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
@PluginDescriptor(
    name = "Live Mapper V2",
    description = "Full Suite Dialogue Mapper",
    tags = {"npc", "mapper"}
)

public class ExamplePlugin extends Plugin
{
	@Inject private OverlayManager overlayManager;
    @Inject private NpcOverlay npcOverlay;
    @Inject Client client;
    @Inject private ClientToolbar clientToolbar;
    @Inject private OkHttpClient okHttpClient; // <--- Add this
    @Inject private ChatboxOverlay chatboxOverlay; // <--- ADD THIS
    @Inject private ClientThread clientThread; // [ADD THIS]
    // Cache for release dates: <NpcID, DateString>
    private final Map<Integer, String> releaseDates = new ConcurrentHashMap<>();
    private final Set<Integer> requestedNpcs = new HashSet<>(); // To prevent spamming API
    private int npcMode = 0; 
    private final Set<NPC> finishedNpcs = new HashSet<>();
   // private final File npcDir = new File("C:\\Users\\dakot\\Desktop\\runescapeserverdata\\data\\OSRS_DUMPS\\NpcDialogues\\npcs");
    private final File npcDir = new File("C:\\Users\\dakot\\Server_Workspace\\RuneScape_Server\\data\\dialogues\\npcs");
    private Map<Integer, Integer> inventorySnapshot = new HashMap<>();
    private ExamplePanel panel;
    private NavigationButton navButton;
    private JFrame guiFrame;
    private GraphEditor graphEditor;
    private int activeDialogueNpcId = -1;
    private String activeDialogueNpcName = "";
    private JTabbedPane tabbedPane;
    private boolean wasDialogueOpen = false;
    private int lastClickedIndex = 0;
    private boolean pendingLink = false;
    private boolean showReleaseDates = false;
    private final File questMemoryFile = new File("C:\\Users\\dakot\\Server_Workspace\\RuneScape_Server\\data\\dialogues\\quest_memory.json");
    private final List<QuestMemoryEntry> questMemory = new ArrayList<>();
    private boolean workingOnQuest = false;
    private int workingQuestId = -1;
    private int workingStage = 0;
    private JCheckBox workQuestChk;
    private JComboBox<String> questBox;
    private JSpinner stageSpinner;
    private List<Node.Action> pendingActions = new ArrayList<>();
    private List<Node.Condition> pendingConditions = new ArrayList<>();
    private Set<Integer> pendingCheckRemovals = new HashSet<>();
    private Node previousNode = null;     // The node BEFORE activeNode
    private int previousConnectionIdx = 0; // The index used to reach activeNode
    private Set<Integer> lastNearbyQuests = new HashSet<>();
    private boolean isUpdatingQuestList = false; // Prevents listener firing during auto-sort
    private List<Node> traversalHistory = new ArrayList<>();
    
    private static class QuestMemoryEntry {
        int questId;
        int x, y, z;
        public QuestMemoryEntry(int id, int x, int y, int z) {
            this.questId = id; this.x = x; this.y = y; this.z = z;
        }
    }
    public void setShowReleaseDates(boolean enabled) {
        this.showReleaseDates = enabled;
    }

    public boolean isShowReleaseDates() {
        return showReleaseDates;
    }
    public Map<Integer, String> loadedQuests = new TreeMap<>();
    @Override
    protected void startUp() {
    	loadQuests();
    	loadQuestMemory();
    	clientThread.invokeLater(() -> inventorySnapshot = captureInventory());
    	panel = new ExamplePanel(this::openGui, this); // Pass 'this' to panel
    	
        java.awt.image.BufferedImage icon = new java.awt.image.BufferedImage(32, 32, 2);
        Graphics2D g = icon.createGraphics(); g.setColor(Color.ORANGE); g.fillOval(0,0,32,32); g.dispose();

        navButton = NavigationButton.builder()
            .tooltip("Open Mapper")
            .icon(icon)
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navButton);
        overlayManager.add(npcOverlay);
        overlayManager.add(chatboxOverlay);
        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
    	overlayManager.remove(npcOverlay);
    	overlayManager.remove(chatboxOverlay);
        clientToolbar.removeNavigation(navButton);
        if(guiFrame != null) guiFrame.dispose();
        }

    private void openGui() {
        if (guiFrame != null && guiFrame.isVisible()) {
            guiFrame.toFront();
            return;
        }
        guiFrame = new JFrame("Dialogue Mapper V2");
        guiFrame.setSize(775, 610);
        guiFrame.setAlwaysOnTop(true);
        guiFrame.setLayout(new BorderLayout());
        GraphicsConfiguration config = guiFrame.getGraphicsConfiguration();
        Rectangle bounds = config.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);

        int x = bounds.x + bounds.width - insets.right - guiFrame.getWidth();
        int y = bounds.y + bounds.height - insets.bottom - guiFrame.getHeight();

        guiFrame.setLocation(x, y);
        // --- TOP TOOLBAR ---
        JPanel topContainer = new JPanel(new GridLayout(2, 1)); // 2 Rows
        
        // Row 1: Quest Controls
        JPanel questToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        questToolbar.setBackground(new Color(50, 50, 50));
        
        workQuestChk = new JCheckBox("Working On Quest");
        workQuestChk.setForeground(Color.ORANGE);
        workQuestChk.setBackground(new Color(50, 50, 50));
        
        questBox = new JComboBox<>();
        questBox.setPreferredSize(new Dimension(200, 25));
        questBox.setEnabled(false);
        
        updateQuestList(lastNearbyQuests);        
        
        
        // 1. Identify Nearby Quests
        Set<Integer> nearbyQuests = new HashSet<>();
        if (client.getLocalPlayer() != null) {
            net.runelite.api.coords.WorldPoint wp = client.getLocalPlayer().getWorldLocation();
            nearbyQuests = getNearbyQuestIds(wp.getX(), wp.getY(), wp.getPlane());
        }
        final Set<Integer> finalNearby = nearbyQuests; 

        // 2. Populate Quest Box (SORTED: Nearby First)
        if (!loadedQuests.isEmpty()) {
            List<String> topList = new ArrayList<>();
            List<String> bottomList = new ArrayList<>();

            for (Map.Entry<Integer, String> entry : loadedQuests.entrySet()) {
                String label = entry.getValue() + " (" + entry.getKey() + ")";
                if (finalNearby.contains(entry.getKey())) {
                    topList.add(label); // Add to Priority List
                } else {
                    bottomList.add(label); // Add to Standard List
                }
            }

            // Add Priority Quests (Top of dropdown)
            for (String s : topList) questBox.addItem(s);
            
            // Add Separator if needed (optional, or just add directly)
            // for (String s : bottomList) questBox.addItem(s);
            
            // Add Rest of Quests
            for (String s : bottomList) questBox.addItem(s);
        }
        
        // 3. Renderer (Color Blue for Nearby)
        questBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                
                if (!isSelected && value instanceof String) {
                    try {
                        String str = (String) value;
                        int start = str.lastIndexOf('(') + 1;
                        int end = str.lastIndexOf(')');
                        int id = Integer.parseInt(str.substring(start, end));
                        
                        if (finalNearby.contains(id)) {
                            c.setForeground(Color.CYAN); 
                            c.setFont(c.getFont().deriveFont(Font.BOLD)); // Optional: Make bold too
                        }
                    } catch (Exception ex) { }
                }
                return c;
            }
        });        
        JLabel lblStage = new JLabel(" Stage:");
        lblStage.setForeground(Color.LIGHT_GRAY);
        
        stageSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 999, 1)); // 0-999, step 1
        stageSpinner.setPreferredSize(new Dimension(60, 25));
        stageSpinner.setEnabled(false);

        // [ADDED] Completed Checkbox
        JCheckBox completedChk = new JCheckBox("Completed");
        completedChk.setForeground(Color.GREEN);
        completedChk.setBackground(new Color(50, 50, 50));
        completedChk.setEnabled(false);
        
        // Logic: Enable/Disable & Update State
        workQuestChk.addActionListener(e -> {
            boolean sel = workQuestChk.isSelected();
            workingOnQuest = sel;
            questBox.setEnabled(sel);
            completedChk.setEnabled(sel);
            
            // Spinner enabled only if "Working" is TRUE and "Completed" is FALSE
            stageSpinner.setEnabled(sel && !completedChk.isSelected());
        });
        
        // Logic: Completed Toggle
        completedChk.addActionListener(e -> {
            if (completedChk.isSelected()) {
                stageSpinner.setValue(50);
                stageSpinner.setEnabled(false); // Lock spinner so it stays at 50
            } else {
                stageSpinner.setValue(0);
                stageSpinner.setEnabled(true); // Unlock so user can type stage
            }
        });
        
        questBox.addActionListener(e -> {
            // [FIX] Ignore events triggered by our auto-sorting
            if (isUpdatingQuestList) return;

            String item = (String) questBox.getSelectedItem();
            if (item != null) {
                try {
                    int start = item.lastIndexOf('(') + 1;
                    int end = item.lastIndexOf(')');
                    workingQuestId = Integer.parseInt(item.substring(start, end));
                } catch (Exception ex) { workingQuestId = -1; }
            }
        });        
        stageSpinner.addChangeListener(e -> {
            workingStage = (int) stageSpinner.getValue();
        });
        
        // Set initial ID if list not empty
        if (questBox.getItemCount() > 0) questBox.setSelectedIndex(0);

        questToolbar.add(workQuestChk);
        questToolbar.add(questBox);
        questToolbar.add(lblStage);
        questToolbar.add(stageSpinner);
        questToolbar.add(completedChk); // Add to UI
        
        topContainer.add(questToolbar);

        // Row 2: Standard Buttons (Save, Reset, Add Cond)
        JPanel buttonToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        
        JButton saveBtn = new JButton("Save JSON");
        saveBtn.addActionListener(e -> saveJson());
        buttonToolbar.add(saveBtn);

        JButton resetBtn = new JButton("Reset / Clear All");
        resetBtn.setBackground(new Color(180, 50, 50));
        resetBtn.setForeground(Color.WHITE);
        resetBtn.addActionListener(e -> {
            GraphEditor editor = getActiveEditor();
            if (editor == null) return;
            int confirm = JOptionPane.showConfirmDialog(guiFrame, "Clear map for " + editor.npcNameForSave + "?", "Reset Map", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                editor.getNodes().clear();
                editor.currentNode = null;
                editor.selectNode(null);
                editor.repaint();
                lastClickedIndex = 0;
                if (panel != null) panel.updateStatus("Map Reset.");
            }
        });
        buttonToolbar.add(resetBtn);

        JButton addCondBtn = new JButton("Add Condition");
        addCondBtn.setBackground(new Color(100, 70, 140));
        addCondBtn.setForeground(Color.WHITE);
        addCondBtn.addActionListener(e -> {
            GraphEditor editor = getActiveEditor();
            if (editor == null) {
                JOptionPane.showMessageDialog(guiFrame, "No NPC Tab Open!");
                return;
            }
            Node n = new Node();
            n.id = getNextSafeId(editor.getNodes());
            n.type = Node.NodeType.LOGIC_BRANCH;
            Point p = editor.getCanvasCenter();
            n.x = p.x; n.y = p.y;
            Node.Condition c = new Node.Condition();
            c.type = Node.ConditionType.NONE; 
            n.conditions.add(c);
            editor.getNodes().add(n);
            editor.selectNode(n);
            editor.repaint();
        });
        buttonToolbar.add(addCondBtn);
        
        topContainer.add(buttonToolbar);
        guiFrame.add(topContainer, BorderLayout.NORTH);
        
        
        
        
        tabbedPane = new JTabbedPane();
        tabbedPane.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int index = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (index != -1) {
                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem closeItem = new JMenuItem("Close Tab");
                        closeItem.addActionListener(ev -> {
                            tabbedPane.remove(index);
                            // If we closed the active tab, the ChangeListener will handle updating state
                        });
                        menu.add(closeItem);
                        menu.show(tabbedPane, e.getX(), e.getY());
                    }
                }
            }
        });
        tabbedPane.addChangeListener(e -> {
            // Update panel status when switching tabs
            GraphEditor current = (GraphEditor) tabbedPane.getSelectedComponent();
            if (current != null) {
                this.graphEditor = current; // Keep local ref sync'd
                if (panel != null) panel.updateStatus("Switched to: " + current.npcNameForSave);
            }
        });
        
        guiFrame.add(tabbedPane, BorderLayout.CENTER);
        guiFrame.setVisible(true);
        }
    
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) return;

        // [UPDATED GUARD CLAUSE]
        // Only process changes if a dialogue is ACTIVELY open (wasDialogueOpen).
        // Otherwise, just update the snapshot silently so we are ready for the next talk.
        GraphEditor editor = findEditorForActiveDialogue();
        if (editor == null && tabbedPane.getTabCount() > 0) {
            editor = (GraphEditor) tabbedPane.getSelectedComponent();
        }

        if (editor == null || editor.currentNode == null || !wasDialogueOpen) {
            inventorySnapshot = captureInventory();
            return;
        }
        Map<Integer, Integer> currentInv = captureInventory();
        boolean coinsWereRemoved = false;

        // --- DETECT REMOVALS ---
        List<Map.Entry<Integer, Integer>> removed = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : inventorySnapshot.entrySet()) {
            int id = e.getKey();
            int qty = e.getValue();
            int newQty = currentInv.getOrDefault(id, 0);
            if (newQty < qty) {
                removed.add(new java.util.AbstractMap.SimpleEntry<>(id, qty - newQty));
                if (id == 995) coinsWereRemoved = true;
            }
        }

        // --- PROCESS REMOVALS ---
        if (!removed.isEmpty()) {
            for (Map.Entry<Integer, Integer> e : removed) {
                Node.Action act = new Node.Action();
                act.type = Node.ActionType.REMOVE_ITEM;
                act.val1 = e.getKey(); act.val2 = e.getValue();
                pendingActions.add(act);
            }
            if (removed.size() > 1) {
                Node.Condition group = new Node.Condition();
                group.type = Node.ConditionType.REQUIRE_ALL;
                for (Map.Entry<Integer, Integer> e : removed) {
                    Node.Condition sub = new Node.Condition();
                    sub.type = Node.ConditionType.HAS_ITEM;
                    sub.val1 = e.getKey(); sub.val2 = e.getValue();
                    group.subConditions.add(sub);
                }
                pendingConditions.add(group);
            } else {
                Node.Condition cond = new Node.Condition();
                cond.type = Node.ConditionType.HAS_ITEM;
                cond.val1 = removed.get(0).getKey(); 
                cond.val2 = removed.get(0).getValue();
                pendingConditions.add(cond);
            }
        }

        // --- DETECT ADDITIONS ---
        List<Map.Entry<Integer, Integer>> added = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : currentInv.entrySet()) {
            int id = e.getKey();
            int qty = e.getValue();
            int oldQty = inventorySnapshot.getOrDefault(id, 0);
            if (qty > oldQty) {
                added.add(new java.util.AbstractMap.SimpleEntry<>(id, qty - oldQty));
            }
        }

        // --- PROCESS ADDITIONS (Updated Logic) ---
        if (!added.isEmpty()) {
            // 1. Actions
            for (Map.Entry<Integer, Integer> e : added) {
                Node.Action act = new Node.Action();
                act.type = Node.ActionType.GIVE_ITEM;
                act.val1 = e.getKey(); act.val2 = e.getValue();
                pendingActions.add(act);
            }

            // 2. Logic & Cleanup
            if (!coinsWereRemoved) {
                List<Map.Entry<Integer, Integer>> filtered = new ArrayList<>();
                for(Map.Entry<Integer, Integer> e : added) {
                    int id = e.getKey();
                    int oldQty = inventorySnapshot.getOrDefault(id, 0);

                    if (id != 995) {
                        if (oldQty == 0) {
                            // We didn't have it -> Check is valid
                            filtered.add(e);
                        } else {
                            // We already had it -> Check is redundant!
                            // Queue it for removal from existing graph
                            pendingCheckRemovals.add(id);
                        }
                    }
                }

                if (!filtered.isEmpty()) {
                    if (filtered.size() > 1) {
                        Node.Condition group = new Node.Condition();
                        group.type = Node.ConditionType.REQUIRE_ALL;
                        for (Map.Entry<Integer, Integer> e : filtered) {
                            Node.Condition sub = new Node.Condition();
                            sub.type = Node.ConditionType.MISSING_ITEM;
                            sub.val1 = e.getKey(); sub.val2 = 1; 
                            group.subConditions.add(sub);
                        }
                        pendingConditions.add(group);
                    } else {
                        Node.Condition cond = new Node.Condition();
                        cond.type = Node.ConditionType.MISSING_ITEM;
                        cond.val1 = filtered.get(0).getKey(); 
                        cond.val2 = 1;
                        pendingConditions.add(cond);
                    }
                }
            }
        }

        inventorySnapshot = currentInv;
        if (panel != null && (!removed.isEmpty() || !added.isEmpty())) {
            panel.updateStatus("Queued Inventory Changes...");
        }
    }
    
    private void applyPendingInventoryLogic(GraphEditor editor) {
        if (editor == null || editor.currentNode == null) return;
        
        // 1. Apply Actions
        if (!pendingActions.isEmpty()) {
            for (Node.Action newAct : pendingActions) {
                boolean exists = editor.currentNode.actions.stream()
                    .anyMatch(oldAct -> actionsMatch(newAct, oldAct));
                if (!exists) editor.currentNode.actions.add(newAct);
            }
            pendingActions.clear();
            if (panel != null) panel.updateStatus("Syncing Inventory Actions...");
        }
        
        // 2. Remove Redundant Checks (Cleanup)
        if (!pendingCheckRemovals.isEmpty()) {
            List<Node> parents = getParents(editor.currentNode, editor.getNodes());
            List<Node> nodesToRemove = new ArrayList<>();

            for (Node p : parents) {
                if (p.type == Node.NodeType.LOGIC_BRANCH) {
                    // Remove top-level MISSING_ITEM checks
                    p.conditions.removeIf(c -> 
                        c.type == Node.ConditionType.MISSING_ITEM && 
                        pendingCheckRemovals.contains(c.val1)
                    );
                    
                    // Remove from nested REQUIRE_ALL groups
                    for (Node.Condition c : p.conditions) {
                        if (c.type == Node.ConditionType.REQUIRE_ALL) {
                            c.subConditions.removeIf(sub -> 
                                sub.type == Node.ConditionType.MISSING_ITEM && 
                                pendingCheckRemovals.contains(sub.val1)
                            );
                        }
                    }
                    // Clean up empty groups
                    p.conditions.removeIf(c -> c.type == Node.ConditionType.REQUIRE_ALL && c.subConditions.isEmpty());

                    // If logic node is now empty, mark for deletion/bypass
                    if (p.conditions.isEmpty()) {
                        nodesToRemove.add(p);
                    }
                }
            }
            
            // Execute Bypass
            for (Node logicNode : nodesToRemove) {
                 bypassLogicNode(editor, logicNode, editor.currentNode);
            }
            
            pendingCheckRemovals.clear();
        }

        // 3. Insert New Logic
        if (!pendingConditions.isEmpty()) {
            List<Node> parents = getParents(editor.currentNode, editor.getNodes());
            for (Node.Condition newCond : pendingConditions) {
                boolean alreadyChecked = false;
                for (Node p : parents) {
                    if (p.type == Node.NodeType.LOGIC_BRANCH) {
                        for (Map.Entry<Integer, Integer> entry : p.connections.entrySet()) {
                            if (entry.getValue() == editor.currentNode.id) {
                                int slot = entry.getKey();
                                if (slot < p.conditions.size()) {
                                    if (conditionsMatch(p.conditions.get(slot), newCond)) alreadyChecked = true;
                                }
                            }
                        }
                    }
                }
                if (!alreadyChecked) {
                    insertLogicBefore(editor, editor.currentNode, newCond);
                    parents = getParents(editor.currentNode, editor.getNodes()); // Refresh
                }
            }
            pendingConditions.clear();
        }
    }    
    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        
        int id = event.getGroupId();
        
        // 1. IGNORE LIST
        // specific widgets we do NOT want to treat as generic "Interface" nodes.
        // We ignore them here so 'onGameTick' can handle them properly as Dialogue/Info/Shop nodes.
        // Added: 193, 229, 210, 211, 212, 306, 307, 309, 4950, 11864 (Info Boxes)
        if (id == 231 || id == 217 || id == 219 || id == 300 || id == 301 || 
            id == 193 || id == 229 || id == 210 || id == 211 || id == 212 || 
            id == 306 || id == 307 || id == 309 || id == 4950 || id == 11864 || id == 11) {
            return;
        }
        process("Interface Opened", Node.NodeType.INTERFACE, null, String.valueOf(id));
    }
    
    
    private void loadNpcTab(int npcId, String npcName, net.runelite.api.coords.WorldPoint currentLoc) {
        if (guiFrame == null || !guiFrame.isVisible()) openGui();

        GraphEditor editor = null;

        // 1. Check if Tab is Already Open
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            GraphEditor existing = (GraphEditor) tabbedPane.getComponentAt(i);
            if (existing.npcIdForSave == npcId) {
                editor = existing;
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }

        // 2. If NOT open, Load from File
        if (editor == null) {
            String cleanName = npcName.replaceAll("[^a-zA-Z0-9.-]", "_");
            File file = new File(npcDir, cleanName + "-" + npcId + ".json");
            
            List<Node> loadedNodes = new ArrayList<>();
            int lX = -1, lY = -1, lH = -1;

            if (file.exists()) {
                // Attempt 1: Try Loading as New Format (Wrapper Object)
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    DialogueFileWrapper wrapper = new Gson().fromJson(reader, DialogueFileWrapper.class);
                    
                    if (wrapper != null && wrapper.nodes != null) {
                        loadedNodes = wrapper.nodes;
                        lX = wrapper.absX;
                        lY = wrapper.absY;
                        lH = wrapper.height;
                    }
                } catch (Exception e) {
                    // Attempt 2: Fallback to Old Format (List Array)
                    // If the file is [ ... ] (Array), the first try throws an exception.
                    // We catch it and try parsing as a simple List<Node>.
                    try (BufferedReader retryReader = new BufferedReader(new FileReader(file))) {
                         loadedNodes = new Gson().fromJson(retryReader, new TypeToken<List<Node>>(){}.getType());
                         // Old files have no location data, so lX/lY defaults remain -1
                         if (panel != null) panel.updateStatus("Loaded legacy format for " + npcName);
                    } catch (Exception ex) {
                         // Only print if both fail
                         System.err.println("Failed to load dialogue: " + ex.getMessage());
                    }
                }
            }
            editor = new GraphEditor(loadedNodes, this);
            editor.npcIdForSave = npcId;
            editor.npcNameForSave = npcName;
            
            // Apply loaded coords
            editor.dialogueLocX = lX;
            editor.dialogueLocY = lY;
            editor.dialogueHeight = lH;
            
            // Force checkbox visual state
            editor.suppressAutoGrab = true;
            editor.locCheckBox.setSelected(lX != -1 && lY != -1);
            editor.suppressAutoGrab = false;

            // [REMOVED] Do NOT set currentNode to the last node.
            // We want 'activeNode' to be null so 'process' treats the next dialogue as a START.
            // if (!loadedNodes.isEmpty()) { editor.currentNode = ...; } 
            
            tabbedPane.addTab(npcName + " (" + npcId + ")", editor);
            tabbedPane.setSelectedComponent(editor);
        }

        // 3. PERFORM DISTANCE CHECK
        editor.suppressAutoGrab = true; 

        boolean savedLocExists = (editor.dialogueLocX != -1 && editor.dialogueLocY != -1);
        boolean locationMismatch = false;

        if (savedLocExists && currentLoc != null) {
            int dist = Math.max(Math.abs(currentLoc.getX() - editor.dialogueLocX), Math.abs(currentLoc.getY() - editor.dialogueLocY));
            if (dist > 32 || currentLoc.getPlane() != editor.dialogueHeight) {
                locationMismatch = true;
            }
        }

        if (locationMismatch) {
            editor.locCheckBox.setSelected(false);
            editor.dialogueLocX = -1; editor.dialogueLocY = -1; editor.dialogueHeight = -1;
            JOptionPane.showMessageDialog(guiFrame, 
                "NPC is outside its saved location! Unchecked specific location.");
        }
        else if (!savedLocExists && currentLoc != null) {
            editor.locCheckBox.setSelected(true);
            editor.dialogueLocX = currentLoc.getX();
            editor.dialogueLocY = currentLoc.getY();
            editor.dialogueHeight = currentLoc.getPlane();
        }
        
        editor.suppressAutoGrab = false; 
        previousNode = null;
        previousConnectionIdx = 0;
        pendingActions.clear();
        pendingConditions.clear();
        pendingCheckRemovals.clear();

    }
    private Node forkNode(Node original, Node parent, int parentIdx, List<Node> allNodes) {
        // 1. Clone Data
        Node clone = new Node();
        clone.id = getNextSafeId(allNodes);
        clone.type = original.type;
        clone.mode = original.mode;
        clone.lines = new ArrayList<>(original.lines);
        clone.options = new ArrayList<>(original.options);
        clone.param = original.param;
        clone.npcId = original.npcId;
        clone.animationId = original.animationId;
        clone.x = original.x + 40; 
        clone.y = original.y + 40;
        
        // [FIX] Deep Copy Conditions & Actions
        for (Node.Condition c : original.conditions) {
            clone.conditions.add(copyCondition(c));
        }
        for (Node.Action a : original.actions) {
            Node.Action newA = new Node.Action();
            newA.type = a.type; newA.val1 = a.val1; newA.val2 = a.val2; newA.val3 = a.val3;
            clone.actions.add(newA);
        }

        // 2. Add to Graph
        allNodes.add(clone);
        
        // 3. Re-wire Parent to point to Clone instead of Original
        if (parent != null) {
            parent.connections.put(parentIdx, clone.id);
        }
        
        if (panel != null) panel.updateStatus("Forked Path: Node " + original.id + " -> " + clone.id);
        return clone;
    }
    private Node findMatch(List<Node> nodes, List<String> lines, Node.NodeType type, Node.DialogueMode mode) {
        for (Node n : nodes) {
            if (n.type != type) continue;
            
            boolean match = false;
            if (type == Node.NodeType.DIALOGUE) {
                if (n.lines.equals(lines) && n.mode == mode) match = true;
            } else if (type == Node.NodeType.OPTION) {
                if (n.options.equals(lines)) match = true;
            }
            
            if (match) return n;
        }
        return null;
    }
    public void forceCurrentNode(Node n) {
        GraphEditor editor = getActiveEditor();
        if (editor != null) {
            editor.currentNode = n;
        }
        if (panel != null) {
            panel.updateStatus("Simulating Jump: ID " + n.id);
        }
    }
    
    // Called by UI to change mode
    public void setNpcMode(int mode) {
        this.npcMode = mode;
        if (mode == 0) finishedNpcs.clear(); // Clear cache if disabled
    }

    public boolean isMarkingEnabled() {
        return npcMode == 1;
    }

    public Set<NPC> getFinishedNpcs() {
        return finishedNpcs;
    }
    public int getNpcMode() {
        return npcMode;
    }
    private void loadQuests() {
        File qFile = new File("C:\\Users\\dakot\\Desktop\\runescapeserverdata\\data\\Quest\\questIds.txt");
        if (!qFile.exists()) return;

        try (BufferedReader br = new BufferedReader(new FileReader(qFile))) {
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = br.readLine()) != null) sb.append(line);
            
            String content = sb.toString();
            // Regex to find childId and text in the JSON-like structure
            Pattern p = Pattern.compile("\"childId\":\\s*(\\d+).*?\"text\":\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(content);
            
            loadedQuests.clear();
            while (m.find()) {
                int id = Integer.parseInt(m.group(1));
                String name = m.group(2);
                loadedQuests.put(id, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private boolean hasTalkOption(NPC npc) {
        // 1. Fail-Safe: If composition is null/loading, assume it MIGHT be important.
        // Returning 'true' here prevents it from being hidden prematurely.
        if (npc.getComposition() == null) return true;
        
        String[] actions = npc.getComposition().getActions();
        
        // 2. If actions are strictly null, it likely has no interaction.
        if (actions == null) return false; 
        
        for (String a : actions) {
            // 3. Strict Check: Looks for "Talk" (covers "Talk-to", "Talk", etc.)
            if (a != null && a.toLowerCase().contains("talk")) return true;
        }
        return false;
    }
    private boolean isNpcFinished(NPC npc) {
        String cleanName = npc.getName().replaceAll("[^a-zA-Z0-9.-]", "_");
        String filename = cleanName + "-" + npc.getId() + ".json";
        return new File(npcDir, filename).exists();
    }
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        // 1. NPC CLICK
    	if (event.getMenuAction() == MenuAction.NPC_FIRST_OPTION || 
            event.getMenuAction() == MenuAction.NPC_SECOND_OPTION ||
            event.getMenuAction() == MenuAction.NPC_THIRD_OPTION ||
            event.getMenuAction() == MenuAction.NPC_FOURTH_OPTION ||
            event.getMenuAction() == MenuAction.NPC_FIFTH_OPTION) 
        {
            NPC targetNpc = null;
            for (NPC npc : client.getNpcs()) {
                if (npc.getIndex() == event.getId()) {
                    targetNpc = npc;
                    break;
                }
            }
            if (targetNpc != null) {
                String name = targetNpc.getName();
                int id = targetNpc.getId();                    
                activeDialogueNpcId = id;
                activeDialogueNpcName = name;                    
                net.runelite.api.coords.WorldPoint loc = targetNpc.getWorldLocation();
                SwingUtilities.invokeLater(() -> loadNpcTab(id, name, loc));
            }    
        }

        // 2. STANDARD OPTIONS & CONTINUE (Restore normal behavior)
        if (event.getMenuAction() == MenuAction.WIDGET_CONTINUE) {
            // [RESTORED] This allows Option 2, 3, 4 etc to be detected correctly
            lastClickedIndex = Math.max(0, event.getActionParam() - 1);
            pendingLink = true;
            return; 
        }

        // 3. INFO BOX CONTINUE BUTTON (The Fix for connection failure)
        // Info boxes often don't trigger WIDGET_CONTINUE, but use a generic opcode.
        // We detect them by the text "Continue".
        boolean isInfoContinue = false;
        String opt = event.getMenuOption();
        
        if (opt != null && opt.equalsIgnoreCase("Continue")) {
            isInfoContinue = true;
        } else {
             // Fallback: Check Widget Text
             Widget w = client.getWidget(event.getParam1()); 
             if (w != null && w.getText() != null && w.getText().toLowerCase().contains("continue")) {
                 isInfoContinue = true;
             }
        }

        if (isInfoContinue) {
            // Info boxes are always linear, so we force index 0
            lastClickedIndex = 0; 
            pendingLink = true;
        }
    }
    
    @Subscribe
    public void onGameTick(GameTick tick) {
        if (client.getGameState() != GameState.LOGGED_IN) return;
        if (!wasDialogueOpen) {
            pendingActions.clear();
            pendingConditions.clear();
            pendingCheckRemovals.clear();
            // Keep the snapshot fresh so we have a valid baseline for the next convo
            if (inventorySnapshot.isEmpty()) inventorySnapshot = captureInventory(); 
       }
        // Wiki & Cache
        for (NPC npc : client.getNpcs()) {
            if (npc != null && !releaseDates.containsKey(npc.getId())) {
                fetchReleaseDate(npc.getId(), npc.getName());
            }
        }
        if (npcMode > 0 || showReleaseDates) {
            finishedNpcs.clear();
            for (NPC npc : client.getNpcs()) {
                if (!hasTalkOption(npc) || isNpcFinished(npc)) finishedNpcs.add(npc);
            }
        }

        boolean isDialogueOpen = false;

        // 1. WIDGET DEFINITIONS
        Widget npcWidget = client.getWidget(WidgetInfo.DIALOG_NPC_TEXT);
        Widget playerWidget = client.getWidget(WidgetInfo.DIALOG_PLAYER_TEXT);
        Widget optionsWidget = client.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
        Widget npcNameWidget = client.getWidget(WidgetInfo.DIALOG_NPC_NAME);
        Widget npcHeadWidget = client.getWidget(WidgetInfo.DIALOG_NPC_HEAD_MODEL);

        // 2. ROBUST INFO BOX SCANNING
        int[] infoWidgets = { 229, 193, 210, 211, 212, 306, 307, 309, 4950, 11864,11 }; 
        
        Widget foundInfo = null;
        String foundText = "";
        List<Integer> foundItems = new ArrayList<>();
        int lastZoom = -1;

        for (int id : infoWidgets) {
            Widget root = client.getWidget(id, 0);
            if (root != null && !root.isHidden()) {
                foundInfo = root; 
                
                // [FIX] INCREASED SCAN RANGE TO 100
                // Some interfaces place the model at higher indices (e.g. 25, 30)
                for (int i = 0; i < 100; i++) {
                     Widget w = client.getWidget(id, i);
                     if (w == null) continue;
                     
                     if (isValidText(w.getText())) foundText += w.getText() + "<br>";
                     
                     // Check for Model (Type 6)
                     if (w.getType() == 6 && w.getModelId() > -1) {
                         foundItems.add(w.getModelId());
                         lastZoom = w.getModelZoom();
                     }
                }
                if (!foundText.isEmpty() || !foundItems.isEmpty()) break;
            }
        }

        // 3. PROCESS FINDINGS

        // A. INFO BOX
        if (foundInfo != null) {
            isDialogueOpen = true; 
            if (!foundText.isEmpty() || !foundItems.isEmpty()) {
                processInfoBox(foundText, foundItems, lastZoom);
            }
        }
        // B. NPC TALK
        else if (npcWidget != null && !npcWidget.isHidden()) {
            isDialogueOpen = true;
            String clean = cleanText(npcWidget.getText());
            String name = (npcNameWidget != null) ? npcNameWidget.getText() : "";
            
            if (!clean.isEmpty()) {
                // If NO Name is present, it's likely an Item/Info box pretending to be NPC dialogue
                boolean isActuallyInfo = (name == null || name.isEmpty());

                if (isActuallyInfo) {
                    if (npcHeadWidget != null && npcHeadWidget.getModelId() > -1) {
                        foundItems.add(npcHeadWidget.getModelId());
                        lastZoom = npcHeadWidget.getModelZoom();
                    }
                    processInfoBox(clean, foundItems, lastZoom);
                } else {
                    process(clean, Node.NodeType.DIALOGUE, Node.DialogueMode.NPC_TALK, name);
                }
            }
        } 
        // C. PLAYER TALK
        else if (playerWidget != null && !playerWidget.isHidden()) {
            isDialogueOpen = true;
            String clean = cleanText(playerWidget.getText());
            if (!clean.isEmpty()) {
                process(clean, Node.NodeType.DIALOGUE, Node.DialogueMode.PLAYER_TALK, "");
            }
        } 
        // D. OPTIONS
        else if (optionsWidget != null && !optionsWidget.isHidden()) {
            Widget[] children = optionsWidget.getChildren();
            if (children != null && children.length > 0) {
                String titleText = children[0].getText();
                if (titleText != null) titleText = titleText.replaceAll("<[^>]*>", "").trim();
                if (titleText == null || titleText.isEmpty()) titleText = "Select an Option";

                List<String> opts = new ArrayList<>();
                for (int i = 1; i < children.length; i++) {
                    Widget w = children[i];
                    if (!isValidText(w.getText())) continue;
                    String clean = w.getText().replaceAll("<[^>]*>", "").trim();
                    if (!clean.isEmpty()) opts.add(clean);
                }

                if (!opts.isEmpty()) {
                    process(String.join("|", opts), Node.NodeType.OPTION, null, titleText);
                    isDialogueOpen = true;
                }
            }
        }
        
        // 4. Shop / Bank / Interface Handling (Keep existing logic)
        Widget shopTitle = client.getWidget(300, 1); 
        Widget bank = client.getWidget(WidgetInfo.BANK_TITLE_BAR);
        Widget levelUp = client.getWidget(WidgetInfo.LEVEL_UP_LEVEL);

        if (shopTitle != null && !shopTitle.isHidden()) {
            process("Shop", Node.NodeType.SHOP, null, activeDialogueNpcName + "'s Shop");
            isDialogueOpen = true;
        }
        else if (bank != null && !bank.isHidden()) {
            process("Bank", Node.NodeType.INTERFACE, null, "Bank");
            isDialogueOpen = true;
        }
        else if (levelUp != null && !levelUp.isHidden()) {
            process("Level Up", Node.NodeType.INTERFACE, null, "Level Up Event");
            isDialogueOpen = true;
        }

     // 6. DETECT DIALOGUE OPEN (For Location Grab)
        if (!wasDialogueOpen && isDialogueOpen) {
            
            // [UPDATE THIS SECTION]
            inventorySnapshot = captureInventory();
            
            // Clear History & Stale Data
            traversalHistory.clear(); // <--- ADD THIS
            pendingActions.clear();
            pendingConditions.clear();
            pendingCheckRemovals.clear();
            
            if (panel != null) panel.updateStatus("Dialogue Started - Inventory Snapshot Reset.");
            

            GraphEditor editor = findEditorForActiveDialogue();
            if (editor != null && editor.locCheckBox.isSelected() && editor.dialogueLocX == -1) {
                editor.grabLocation();
                if (panel != null) panel.updateStatus("Auto-Locked Location: " + editor.dialogueLocX + "," + editor.dialogueLocY);
            }
        }        
        // 5. BREAK CHAIN IF CLOSED
        if (wasDialogueOpen && !isDialogueOpen) {
            GraphEditor editor = findEditorForActiveDialogue();
            // [FIX] Also fallback to selected tab if ID lookup failed (prevents dangling logic)
            if (editor == null && tabbedPane.getTabCount() > 0) {
                 editor = (GraphEditor) tabbedPane.getSelectedComponent();
            }

            if (editor != null) editor.currentNode = null;
            activeDialogueNpcId = -1;
            activeDialogueNpcName = "";
            pendingLink = false;
            if (panel != null) panel.updateStatus("Dialogue Ended.");
        }
        wasDialogueOpen = isDialogueOpen;
        
        
     // 7. QUEST LIST AUTO-UPDATE
        if (client.getLocalPlayer() != null) {
            net.runelite.api.coords.WorldPoint wp = client.getLocalPlayer().getWorldLocation();
            
            // Check what quests are nearby NOW
            Set<Integer> currentNearby = getNearbyQuestIds(wp.getX(), wp.getY(), wp.getPlane());
            
            // If different from the last check (e.g. walked away), Update UI
            if (!currentNearby.equals(lastNearbyQuests)) {
                lastNearbyQuests = currentNearby; // Update cache
                
                final Set<Integer> toUpdate = currentNearby;
                SwingUtilities.invokeLater(() -> updateQuestList(toUpdate));
            }
        }
    }

    private void processInfoBox(String text, List<Integer> items, int zoom) {
        process(text, Node.NodeType.DIALOGUE, Node.DialogueMode.INFO_BOX, "");
        
        // [FIX] Use smarter editor finding. If ID/Name lookup fails (common for Info boxes), use Selected Tab.
        GraphEditor editor = findEditorForActiveDialogue();
        if (editor == null && tabbedPane.getTabCount() > 0) {
            editor = (GraphEditor) tabbedPane.getSelectedComponent();
        }

        if (editor != null && editor.currentNode != null && editor.currentNode.mode == Node.DialogueMode.INFO_BOX) {
             if (items.size() >= 2) {
                 editor.currentNode.npcId = items.get(0);       
                 editor.currentNode.animationId = items.get(1); 
                 editor.currentNode.param = "DUAL_ITEM";             
             } else if (items.size() == 1) {
                 editor.currentNode.npcId = items.get(0);       
                 editor.currentNode.animationId = zoom;          
                 editor.currentNode.param = "SINGLE_ITEM";
             } else {
                 editor.currentNode.npcId = -1;
                 editor.currentNode.animationId = -1;
                 editor.currentNode.param = "";
             }
        }
    }
    // --- KEEP THESE HELPERS ---
    private boolean isValidText(String text) {
        if (text == null) return false;
        if (text.toLowerCase().contains("click here to continue")) return false;
        if (text.toLowerCase().contains("please wait")) return false;
        return true;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        // [FIX] Do NOT remove HTML tags here (like <br>). 
        // We only want to remove the system prompts.
        // The process() method handles splitting by <br> and cleaning tags later.
        return text.replaceAll("(?i)click here to continue", "")
                   .replaceAll("(?i)please wait\\.\\.\\.", "")
                   .trim();
    }
    @Subscribe
    public void onMenuEntryAdded(MenuEntryAdded event) {
        if (npcMode != 2) return; // Only in Hide Mode

        for (NPC npc : finishedNpcs) {
            // Check if the entry targets a finished NPC
            if (event.getTarget().contains(npc.getName())) {
                // Completely remove the entry from the menu
                MenuEntry[] entries = client.getMenuEntries();
                MenuEntry[] newEntries = Arrays.stream(entries)
                    .filter(e -> !e.getTarget().contains(npc.getName()))
                    .toArray(MenuEntry[]::new);
                client.setMenuEntries(newEntries);
            }
        }
    }    
    public GraphEditor getActiveEditor() {
        if (tabbedPane == null || tabbedPane.getTabCount() == 0) return null;
        return (GraphEditor) tabbedPane.getSelectedComponent();
    }
    
    private GraphEditor findEditorForActiveDialogue() {
        if (tabbedPane == null) return null;
        
        // 1. Try matching by Locked ID (From Click)
        if (activeDialogueNpcId != -1) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                GraphEditor ed = (GraphEditor) tabbedPane.getComponentAt(i);
                if (ed.npcIdForSave == activeDialogueNpcId) return ed;
            }
        }
        
        // 2. Try matching by Name (Fallback if clicked ID is missing)
        if (!activeDialogueNpcName.isEmpty()) {
            for (int i = 0; i < tabbedPane.getTabCount(); i++) {
                GraphEditor ed = (GraphEditor) tabbedPane.getComponentAt(i);
                if (ed.npcNameForSave.equalsIgnoreCase(activeDialogueNpcName)) return ed;
            }
        }
        
        // 3. If no matching tab is found, return null 
        // (Prevents dumping dialogue into unrelated tabs)
        return null; 
    }
    private int getNextSafeId(List<Node> nodes) {
        // 1. Scan for Max ID
        int max = -1;
        Set<Integer> used = new HashSet<>();
        
        for (Node n : nodes) {
            if (n.id > max) max = n.id;
            used.add(n.id);
        }
        
        // 2. Propose Next ID (Max + 1)
        int next = max + 1;
        
        // 3. Paranoid Verify: Ensure this ID is truly unique 
        // (Handles edge cases where list might be unsorted or corrupted)
        while (used.contains(next)) {
            next++;
        }
        
        return next;
    }
    
 // [REPLACE process METHOD]
    private void process(String rawText, Node.NodeType type, Node.DialogueMode mode, String param) {
        // 1. PARSE LINES
        List<String> incomingLines = new ArrayList<>();
        if (type == Node.NodeType.DIALOGUE) {
            String[] rawLines = rawText.split("<br>");
            for (String line : rawLines) {
                String clean = line.replaceAll("<[^>]*>", "").trim();
                if (!clean.isEmpty()) incomingLines.add(clean);
            }
        } 
        else if (type == Node.NodeType.OPTION) {
            String clean = rawText.replaceAll("<[^>]*>", ""); 
            for (String s : clean.split("\\|")) incomingLines.add(s.trim());
        }
        else {
            incomingLines.add(rawText.replaceAll("<[^>]*>", "").trim());
        }

        // 2. GET EDITOR & ACTIVE NODE
        GraphEditor editor = findEditorForActiveDialogue();
        if (editor == null) return;
        List<Node> currentNodes = editor.getNodes();
        Node activeNode = editor.currentNode;
     // ============================================================
        // 2.4. PRE-TRAVERSAL (The "Suck-In" Fix)
        // ============================================================
        // If we are sitting on a Dialogue that links to a Logic Node, 
        // we MUST move into the Logic Node immediately. 
        // Otherwise, Step 5 will see a mismatch (Dialogue vs Logic) and fork the Dialogue, skipping the logic.
        if (activeNode != null && activeNode.connections.containsKey(lastClickedIndex)) {
            int nextId = activeNode.connections.get(lastClickedIndex);
            Node next = currentNodes.stream().filter(n -> n.id == nextId).findFirst().orElse(null);
            
            if (next != null && next.type == Node.NodeType.LOGIC_BRANCH) {
                if (panel != null) panel.updateStatus("Implicitly entering Logic Node " + next.id);
                previousNode = activeNode;
                previousConnectionIdx = lastClickedIndex;
                
                activeNode = next;
                editor.currentNode = next;
                // We do NOT reset lastClickedIndex yet; the Logic Traversal below will calculate the correct slot.
            }
        }
        // ============================================================
        // 2.5. LOGIC LOOK-AHEAD & TRAVERSAL
        // ============================================================
        // Traverse logic nodes to predict where we SHOULD be.
        Node logicParent = null; // Track the Logic Node in case we need to backtrack
        int logicDepth = 0;
        
        while (activeNode != null && activeNode.type == Node.NodeType.LOGIC_BRANCH && logicDepth < 10) {
            logicDepth++;
            
            int targetIndex = -1;
            
            // 1. Strict Quest Look-Ahead
            boolean isQuestNode = activeNode.conditions.stream().anyMatch(c -> c.type.toString().startsWith("QUEST"));
            if (workingOnQuest && workingQuestId != -1 && isQuestNode) {
                targetIndex = getStrictQuestSlot(activeNode);
            } 
            
            // 2. Standard Evaluation (Item Checks, Stat Checks)
            if (targetIndex == -1 || targetIndex >= activeNode.conditions.size()) {
                targetIndex = evaluateLogicNode(activeNode);
            }

            // Save parent reference
            logicParent = activeNode;

            // 3. Move to the Child Node
            if (activeNode.connections.containsKey(targetIndex)) {
                int nextId = activeNode.connections.get(targetIndex);
                Node nextNode = currentNodes.stream().filter(n -> n.id == nextId).findFirst().orElse(null);
                
                if (nextNode != null) {
                    if (panel != null) panel.updateStatus("Traversing Logic ID " + activeNode.id + " -> Path " + targetIndex);
                    
                    previousNode = activeNode;
                    previousConnectionIdx = targetIndex;
                    
                    // [ADD THIS]
                    traversalHistory.add(activeNode);
                    if (traversalHistory.size() > 5) traversalHistory.remove(0);

                    activeNode = nextNode;
                    editor.currentNode = nextNode;
                    } else {
                    break; // Dead end connection
                }
            } else {
                // [FIX] No connection on predicted path. 
                // STOP HERE. Stay on the Logic Node so Step 6 creates the link FROM the logic node.
                activeNode = logicParent;
                editor.currentNode = logicParent;
                lastClickedIndex = targetIndex;
                break; 
            }
        }
        
        // 3. DUPLICATE CHECK
        if (activeNode != null) {
            if (activeNode.type == type) {
                boolean sameContent = false;
                if (type == Node.NodeType.DIALOGUE) {
                    if (activeNode.lines.equals(incomingLines) && activeNode.mode == mode) sameContent = true;
                } else if (type == Node.NodeType.OPTION) {
                    sameContent = activeNode.options.equals(incomingLines);
                } else {
                    sameContent = activeNode.param.equals(param);
                }
                if (sameContent) return; 
            }
        }
        
        boolean wasEmpty = currentNodes.isEmpty();

        // 4. AUTO-CREATE ROOT
        if (wasEmpty) {
            Node root = new Node();
            root.id = 0; root.x = 50; root.y = 50;
            if (workingOnQuest && workingQuestId != -1) {
                root.type = Node.NodeType.LOGIC_BRANCH;
                Node.Condition cComp = new Node.Condition();
                cComp.type = Node.ConditionType.QUEST_STAGE_EQUALS;
                cComp.val1 = workingQuestId; cComp.val2 = 50; 
                root.conditions.add(cComp);
                Node.Condition cStart = new Node.Condition();
                cStart.type = Node.ConditionType.QUEST_STAGE_EQUALS;
                cStart.val1 = workingQuestId; cStart.val2 = 0;
                root.conditions.add(cStart);
                int targetIndex = -1;
                if (workingStage == 50) targetIndex = 0;
                else if (workingStage == 0) targetIndex = 1;
                else {
                    Node.Condition cCurr = new Node.Condition();
                    cCurr.type = Node.ConditionType.QUEST_STAGE_EQUALS;
                    cCurr.val1 = workingQuestId; cCurr.val2 = workingStage;
                    root.conditions.add(cCurr);
                    targetIndex = 2;
                }
                lastClickedIndex = targetIndex; 
            } else {
                root.type = Node.NodeType.LOGIC_BRANCH;
                Node.Condition c = new Node.Condition();
                c.type = Node.ConditionType.NONE; 
                root.conditions.add(c);
                lastClickedIndex = 0;
            }
            currentNodes.add(root);
            editor.currentNode = root; 
            activeNode = root;         
            pendingLink = true; 
        }

        // 4.5. SMART ROOT LINKING
        if (!wasEmpty && activeNode == null && workingOnQuest && workingQuestId != -1) {
            Node root = currentNodes.stream().filter(n -> n.id == 0).findFirst().orElse(null);
            
            if (root != null && root.type == Node.NodeType.LOGIC_BRANCH) {
                int targetIndex = -1;
                for (int i = 0; i < root.conditions.size(); i++) {
                    Node.Condition c = root.conditions.get(i);
                    if (c.type == Node.ConditionType.QUEST_STAGE_EQUALS && 
                        c.val1 == workingQuestId && c.val2 == workingStage) {
                        targetIndex = i;
                        break;
                    }
                }
                if (targetIndex == -1) {
                     Node.Condition cNew = new Node.Condition();
                     cNew.type = Node.ConditionType.QUEST_STAGE_EQUALS;
                     cNew.val1 = workingQuestId; cNew.val2 = workingStage;
                     root.conditions.add(cNew);
                     targetIndex = root.conditions.size() - 1; 
                }
                activeNode = root;
                editor.currentNode = root;
                lastClickedIndex = targetIndex;
                pendingLink = true;
            }
        }

        // ============================================================
        // 5. DIVERGENCE & SELF-CORRECTION
        // ============================================================
        Node existingMatch = null;

        if (activeNode != null && activeNode.connections.containsKey(lastClickedIndex)) {
            int existingChildId = activeNode.connections.get(lastClickedIndex);
            Node existingChild = currentNodes.stream().filter(n -> n.id == existingChildId).findFirst().orElse(null);
            
            if (existingChild != null) {
                // Check match
                boolean childMatches = false;
                if (existingChild.type == type) {
                    if (type == Node.NodeType.DIALOGUE) childMatches = existingChild.lines.equals(incomingLines);
                    else if (type == Node.NodeType.OPTION) childMatches = existingChild.options.equals(incomingLines);
                }

                if (!childMatches) {
                    // DIVERGENCE DETECTED!
                    
                    // [FIX] BACKTRACK: If we just traversed Logic, but the result was wrong, return to Logic.
                    if (logicParent != null) {
                        if (panel != null) panel.updateStatus("Logic Prediction Failed. Backtracking to Logic Node " + logicParent.id);
                        activeNode = logicParent;
                        editor.currentNode = logicParent;
                        // Fall through to Logic Handler below...
                    }

                    // A. LOGIC NODE SELF-CORRECTION
                    if (activeNode.type == Node.NodeType.LOGIC_BRANCH) {
                        // 1. Search SIBLINGS (Did we just pick the wrong slot?)
                        int matchIndex = -1;
                        for (Map.Entry<Integer, Integer> entry : activeNode.connections.entrySet()) {
                            Node child = currentNodes.stream().filter(n -> n.id == entry.getValue()).findFirst().orElse(null);
                            if (child != null && child.type == type) {
                                boolean m = (type == Node.NodeType.DIALOGUE) ? child.lines.equals(incomingLines) : child.options.equals(incomingLines);
                                if (m) { matchIndex = entry.getKey(); break; }
                            }
                        }

                        if (matchIndex != -1) {
                            // [SUCCESS] Found it in another slot! Snap to it.
                            lastClickedIndex = matchIndex;
                            final Node activeNode1 = activeNode;
                            existingMatch = currentNodes.stream().filter(n -> n.id == activeNode1.connections.get(lastClickedIndex)).findFirst().orElse(null);
                            if (panel != null) panel.updateStatus("Logic Self-Corrected to Path " + matchIndex);
                        } 
                        else {
                            // [FAILURE] No match. Expand EXISTING Logic Node.
                            if (workingOnQuest && activeNode.conditions.stream().anyMatch(c -> c.type.toString().startsWith("QUEST"))) {
                                lastClickedIndex = getStrictQuestSlot(activeNode);
                                if (lastClickedIndex >= activeNode.conditions.size()) {
                                    Node.Condition cNew = new Node.Condition();
                                    cNew.type = Node.ConditionType.QUEST_STAGE_EQUALS;
                                    cNew.val1 = workingQuestId; cNew.val2 = workingStage;
                                    activeNode.conditions.add(cNew);
                                    lastClickedIndex = activeNode.conditions.size() - 1;
                                }
                            } else {
                                // Generic/Item Logic -> Add "Else" or next slot
                                lastClickedIndex = activeNode.conditions.size(); 
                                if (activeNode.connections.containsKey(lastClickedIndex)) {
                                    activeNode.conditions.add(new Node.Condition()); // Dummy condition to expand
                                    lastClickedIndex = activeNode.conditions.size() - 1;
                                }
                            }
                            // Clear slot for new link
                            activeNode.connections.remove(lastClickedIndex);
                            existingMatch = null;
                            if (panel != null) panel.updateStatus("Logic Divergence. Creating new branch at Slot " + lastClickedIndex);
                        }
                    }
                    // B. STANDARD 2-IN-A-ROW (Legacy Forking for non-logic)
                    else {
                    	Node twoStepChild = findStructuralMatch(currentNodes, activeNode, incomingLines, type, traversalHistory);
                        
                        if (twoStepChild != null) {
                             List<Node> parents = getParents(twoStepChild, currentNodes);
                             final Node activeRef = activeNode;
                             Node correctParent = parents.stream().filter(p -> nodesMatch(p, activeRef)).findFirst().orElse(null);
                             if (correctParent != null) {
                                 activeNode = correctParent;
                                 editor.currentNode = correctParent;
                                 existingMatch = twoStepChild;
                                 lastClickedIndex = (activeNode.type == Node.NodeType.LOGIC_BRANCH) ? getStrictQuestSlot(activeNode) : 0;
                                 pendingLink = true;
                             }
                         } else if (previousNode != null || activeNode.id == 0) {
                             // Standard Fork
                             Node forked = forkNode(activeNode, previousNode, previousConnectionIdx, currentNodes);
                             activeNode = forked;
                             editor.currentNode = forked;
                             activeNode.connections.remove(lastClickedIndex);
                             existingMatch = null;
                         }
                    }
                } else {
                    existingMatch = existingChild;
                }
            }
        }
        
        // 6. FIND OR CREATE MATCH
        if (activeNode != null && activeNode.type == Node.NodeType.LOGIC_BRANCH) {
             // Final sanity check for Quest Nodes
             if (workingOnQuest && activeNode.conditions.stream().anyMatch(c -> c.type.toString().startsWith("QUEST"))) {
                 int strict = getStrictQuestSlot(activeNode);
                 if (lastClickedIndex != strict) lastClickedIndex = strict;
             }
        }

        if (existingMatch == null) {
            existingMatch = findMatch(currentNodes, incomingLines, type, mode);
        }
        
        if (existingMatch != null) {
            link(existingMatch, editor);
            
            int usedIndex = lastClickedIndex;
            editor.currentNode = existingMatch;
            applyPendingInventoryLogic(editor);
            
            previousNode = activeNode; 
            previousConnectionIdx = usedIndex;
            
            // [ADD THIS]
            if (activeNode != null) {
                traversalHistory.add(activeNode);
                if (traversalHistory.size() > 5) traversalHistory.remove(0);
            }            
        } else {
            // CREATE NEW NODE
            Node n = new Node();
            n.id = getNextSafeId(currentNodes); 
            n.type = type;
            if (mode != null) n.mode = mode;
            n.param = param;
            
            if (activeNode != null) {
                n.x = activeNode.x + 230;
                n.y = activeNode.y + (lastClickedIndex * 125);
                boolean collision;
                do {
                    collision = false;
                    for (Node other : currentNodes) {
                        if (Math.abs(other.x - n.x) < 200 && Math.abs(other.y - n.y) < 100) {
                            n.y += 130;
                            collision = true;
                            break;
                        }
                    }
                } while (collision);
            }

            if (type == Node.NodeType.DIALOGUE) {
                n.lines = incomingLines; 
                Widget head = null;
                if (n.mode == Node.DialogueMode.NPC_TALK) head = client.getWidget(WidgetInfo.DIALOG_NPC_HEAD_MODEL);
                else if (n.mode == Node.DialogueMode.PLAYER_TALK) head = client.getWidget(WidgetInfo.DIALOG_PLAYER_TEXT);
                if (head != null) {
                    n.npcId = head.getModelId();
                    n.animationId = head.getAnimationId(); 
                }
            } else if (type == Node.NodeType.OPTION) {
                n.options = incomingLines;
            }

            currentNodes.add(n);
            int usedIndex = lastClickedIndex;
            Node oldActive = activeNode;
            
            link(n, editor); 
            editor.currentNode = n;
            applyPendingInventoryLogic(editor);
            
            previousNode = oldActive;
            previousConnectionIdx = usedIndex;
            if (oldActive != null) {
                traversalHistory.add(oldActive);
                if (traversalHistory.size() > 5) traversalHistory.remove(0);
            }
        }

        if (panel != null) panel.updateStatus("Node: " + editor.currentNode.id);
        if (guiFrame != null && guiFrame.isVisible()) {
            editor.selectNode(editor.currentNode);
            editor.repaint();
        }
    }    
    
    private void link(Node target, GraphEditor editor) {
        if (editor.currentNode != null && pendingLink) {
            
        	// ============================================================
            // 1. SMART DUPLICATE CHECK
            // ============================================================
            // Check if we are already connected to this target on THIS SPECIFIC slot.
            if (editor.currentNode.connections.containsKey(lastClickedIndex)) {
                int existingId = editor.currentNode.connections.get(lastClickedIndex);
                if (existingId == target.id) {
                     if (panel != null) panel.updateStatus("Link already exists on this slot. Skipping.");
                     pendingLink = false;
                     lastClickedIndex = 0;
                     return; 
                }
            }

            // [FIX] Allow Logic Nodes (Quest Hubs) to share targets across different slots.
            // (e.g. Stage 0 and Stage 50 can both point to "Hello")
            if (editor.currentNode.type != Node.NodeType.LOGIC_BRANCH) {
                // For normal dialogues, prevent multi-wiring (loops/errors)
                if (editor.currentNode.connections.containsValue(target.id)) {
                    if (panel != null) panel.updateStatus("Target already connected (Non-Logic). Skipping.");
                    pendingLink = false;
                    lastClickedIndex = 0;
                    return;
                }
            }
            // ============================================================

            // 2. PARENT IS A LOGIC NODE (Expand it if target is NEW)
            if (editor.currentNode.type == Node.NodeType.LOGIC_BRANCH) {
                 // We passed the duplicate check, so this MUST be a new random variation.
                 
                 // If the specific slot we clicked (lastClickedIndex) is taken, 
                 // we add a NEW branch to the end.
                 if (editor.currentNode.connections.containsKey(lastClickedIndex)) {
                     Node.Condition c = new Node.Condition();
                     c.type = Node.ConditionType.NONE;
                     editor.currentNode.conditions.add(c);
                     
                     int newSlot = editor.currentNode.conditions.size() - 1;
                     editor.currentNode.connections.put(newSlot, target.id);
                     
                     if (panel != null) panel.updateStatus("Added new random branch.");
                 } else {
                     // The slot was empty (e.g., the default "Else" slot), just fill it.
                     editor.currentNode.connections.put(lastClickedIndex, target.id);
                 }
                 
                 finishLink(editor);
                 return;
            }

            // 3. PARENT IS A DIALOGUE NODE (Handle Conflicts)
            if (editor.currentNode.connections.containsKey(lastClickedIndex)) {
                int existingId = editor.currentNode.connections.get(lastClickedIndex);
                
                // Get the node we are currently connected to
                Node existingNode = editor.getNodes().stream()
                    .filter(n -> n.id == existingId).findFirst().orElse(null);

                // CASE A: Connected to existing Logic Node -> Add to it
                if (existingNode != null && existingNode.type == Node.NodeType.LOGIC_BRANCH) {
                    
                    // Double check existing logic node doesn't already have this link
                    if (!existingNode.connections.containsValue(target.id)) {
                        Node.Condition c = new Node.Condition();
                        c.type = Node.ConditionType.NONE;
                        existingNode.conditions.add(c);
                        
                        int newSlot = existingNode.conditions.size() - 1;
                        existingNode.connections.put(newSlot, target.id);
                        if (panel != null) panel.updateStatus("Added to existing Logic Node.");
                    }
                    
                    finishLink(editor);
                    return;
                }

                // CASE B: Connected to normal Dialogue -> Create Splitter
                if (existingId != target.id) {
                    Node logicNode = new Node();
                    
                    // [FIX] CRITICAL: Generate a safe ID for the splitter
                    logicNode.id = getNextSafeId(editor.getNodes()); 
                    
                    logicNode.type = Node.NodeType.LOGIC_BRANCH;
                    logicNode.x = editor.currentNode.x + 220; 
                    logicNode.y = editor.currentNode.y;
                    
                    // Branch 1: The OLD connection
                    Node.Condition c1 = new Node.Condition();
                    c1.type = Node.ConditionType.NONE;
                    logicNode.conditions.add(c1);
                    logicNode.connections.put(0, existingId); 

                    // Branch 2: The NEW connection (Target)
                    Node.Condition c2 = new Node.Condition();
                    c2.type = Node.ConditionType.NONE;
                    logicNode.conditions.add(c2);
                    logicNode.connections.put(1, target.id); 

                    editor.getNodes().add(logicNode);

                    // Re-wire Parent to point to this new Logic Node
                    editor.currentNode.connections.put(lastClickedIndex, logicNode.id);

                    if (panel != null) panel.updateStatus("Created Logic Splitter (ID " + logicNode.id + ").");
                    if (guiFrame != null) editor.selectNode(logicNode);
                    
                    finishLink(editor);
                    return;
                }
            }

            // 4. STANDARD LINK (Empty Slot)
            editor.currentNode.connections.put(lastClickedIndex, target.id);
            finishLink(editor);
        }
        lastClickedIndex = 0; 
    }
    // Helper to clean up UI after linking
    private void finishLink(GraphEditor editor) {
        pendingLink = false;
        lastClickedIndex = 0;
        if (guiFrame != null) {
            editor.updateInternalPanel();
            editor.repaint();
        }
    }
    
    private void saveJson() {
        if (tabbedPane.getTabCount() == 0) return;
        
        GraphEditor editor = (GraphEditor) tabbedPane.getSelectedComponent();
        if (editor == null) return;

        String cleanName = editor.npcNameForSave.replaceAll("[^a-zA-Z0-9.-]", "_");
        if (cleanName.isEmpty()) cleanName = "Unknown";
        
        String fileName = cleanName + "-" + editor.npcIdForSave + ".json";
        File file = new File(npcDir, fileName);
        file.getParentFile().mkdirs();
        
        try (FileWriter w = new FileWriter(file)) {
            // Create Wrapper
            DialogueFileWrapper wrapper = new DialogueFileWrapper();
            wrapper.nodes = editor.getNodes();
            
            // CHECK THE BOX STATE
            if (editor.locCheckBox.isSelected()) {
                wrapper.absX = editor.dialogueLocX;
                wrapper.absY = editor.dialogueLocY;
                wrapper.height = editor.dialogueHeight;
                
                // [ADDED] Record Quest Usage for Memory
                // (Make sure getUsedQuestIds() is public in GraphEditor!)
                if (wrapper.absX != -1) {
                    java.util.Set<Integer> usedQuests = editor.getUsedQuestIds();
                    for (int qId : usedQuests) {
                        recordQuestUsage(qId, wrapper.absX, wrapper.absY, wrapper.height);
                    }
                }
            } else {
                wrapper.absX = -1; 
                wrapper.absY = -1; 
                wrapper.height = -1;
            }

            new GsonBuilder().setPrettyPrinting().create().toJson(wrapper, w);
            JOptionPane.showMessageDialog(guiFrame, "Saved to:\n" + fileName);
        } catch (Exception e) { 
            e.printStackTrace();
            JOptionPane.showMessageDialog(guiFrame, "Error saving: " + e.getMessage());
        }
    }    
    private List<String> smartWrap(String text) {
        List<String> finalLines = new ArrayList<>();
        // 1. Handle explicit breaks from RuneLite (<br>)
        String[] rawLines = text.split("<br>");

        for (String raw : rawLines) {
            String[] words = raw.split(" ");
            StringBuilder sb = new StringBuilder();

            for (String word : words) {
                // If adding this word exceeds 45 chars, push the line and start new
                if (sb.length() + word.length() + 1 > 45) {
                    finalLines.add(sb.toString());
                    sb = new StringBuilder();
                }
                if (sb.length() > 0) sb.append(" ");
                sb.append(word);
            }
            if (sb.length() > 0) finalLines.add(sb.toString());
        }
        return finalLines;
    }
    
    private void fetchReleaseDate(int npcId, String npcName) {
        if (npcName == null || npcName.isEmpty() || requestedNpcs.contains(npcId)) return;
        requestedNpcs.add(npcId);
        
        // Use the safer capitalization to fix "TzTok-Jad" etc.
        String cleanName = capitalize(npcName);
        
        doWikiRequest(npcId, cleanName, false);
    }
    private void doWikiRequest(int npcId, String searchName, boolean isRetry) {
        String urlName = searchName.replace(" ", "_");
        String url = "https://oldschool.runescape.wiki/api.php?action=parse&prop=wikitext&format=json&page=" + urlName;

        Request request = new Request.Builder().url(url).header("User-Agent", "RuneLite-Dialogue-Mapper").build();

        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) { /* Ignore */ }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody body = response.body()) {
                    if (!response.isSuccessful()) {
                        // If 404 and haven't retried yet, try adding (NPC)
                        if (!isRetry) {
                            doWikiRequest(npcId, searchName + " (NPC)", true);
                        }
                        return;
                    }

                    String json = body.string();
                    
                    // Regex to find release date
                    Matcher m = Pattern.compile("\\|\\s*release\\s*=\\s*(.*?)(\\||\\})", Pattern.CASE_INSENSITIVE).matcher(json);
                    
                    if (m.find()) {
                        String rawDate = m.group(1).trim();
                        String cleanDate = rawDate.replaceAll("\\[\\[|\\]\\]", ""); // Remove [[ ]]
                        if (!cleanDate.isEmpty()) {
                            releaseDates.put(npcId, cleanDate);
                        }
                    } else if (!isRetry) {
                        // If page exists but has no release date (e.g. Disambiguation "Hero"), try (NPC)
                        doWikiRequest(npcId, searchName + " (NPC)", true);
                    }
                }
            }
        });
    }
    
    // Safer method: Only capitalizes the first letter, preserves "TzTok-Jad" casing
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }
    
    public Map<Integer, String> getReleaseDates() { return releaseDates; }
    private int getQuestStage(Quest q) {
        try {
            // 1. Try to find 'varbit' field (Most quests use this)
            try {
                Field varbitField = Quest.class.getDeclaredField("varbit");
                varbitField.setAccessible(true);
                Object varbitObj = varbitField.get(q); 
                
                if (varbitObj != null) {
                    // Reflectively get 'id' from the Varbits enum/object
                    // We assume the field is named "id".
                    Field idField = varbitObj.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    int id = idField.getInt(varbitObj);
                    return client.getVarbitValue(id);
                }
            } catch (NoSuchFieldException ignored) {
                // If "varbit" field doesn't exist, ignore and try varPlayer
            }

            // 2. Try to find 'varPlayer' field (Older quests use this)
            try {
                Field varPlayerField = Quest.class.getDeclaredField("varPlayer");
                varPlayerField.setAccessible(true);
                Object varpObj = varPlayerField.get(q);
                
                if (varpObj != null) {
                    // Reflectively get 'id' from the VarPlayer enum/object
                    Field idField = varpObj.getClass().getDeclaredField("id");
                    idField.setAccessible(true);
                    int id = idField.getInt(varpObj);
                    return client.getVarpValue(id);
                }
            } catch (NoSuchFieldException ignored) {
                // If "varPlayer" field doesn't exist either
            }
            
        } catch (Exception e) {
            // Uncomment to debug if you still see -1 everywhere
            // e.printStackTrace(); 
        }
        return -1; // Fail-safe if no stage found
    }    
    public void dumpQuests() {
        clientThread.invokeLater(() -> {
            System.out.println("=== DEEP QUEST ANALYSIS START ===");
            
            for (Quest q : Quest.values()) {
                String name = q.getName();
                
                // 1. REFLECT ON THE QUEST OBJECT ITSELF
                StringBuilder hiddenFields = new StringBuilder();
                try {
                    for (Field f : q.getClass().getDeclaredFields()) {
                        f.setAccessible(true);
                        if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("name") || f.getName().equals("ordinal")) continue;
                        Object val = f.get(q);
                        hiddenFields.append(f.getName()).append(":").append(val).append(" ");
                    }
                } catch (Exception e) { hiddenFields.append("Error reading fields"); }

                // 2. FUZZY SEARCH VARBITS AND VARPLAYERS
                String cleanName = name.toUpperCase().replaceAll("[^A-Z]", ""); 
                String bestMatchVar = "None";
                int bestMatchValue = -1;

                try {
                    // A. Search Varbits (Class with Static Ints)
                    for (Field f : Varbits.class.getDeclaredFields()) {
                        if (f.getType() == int.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                            String varName = f.getName().toUpperCase().replaceAll("[^A-Z]", "");
                            // Filter: Must contain name and be long enough to avoid noise
                            if (varName.length() > 4 && (varName.contains(cleanName) || cleanName.contains(varName))) {
                                int id = f.getInt(null);
                                int val = client.getVarbitValue(id);
                                bestMatchVar = "Varbit." + f.getName();
                                bestMatchValue = val;
                                break; 
                            }
                        }
                    }
                    
                    // B. Search VarPlayer (Class with Static Ints - FIX)
                    if (bestMatchValue == -1) {
                        for (Field f : VarPlayer.class.getDeclaredFields()) {
                            // Check if field is static int
                            if (f.getType() == int.class && java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                                String vpName = f.getName().toUpperCase().replaceAll("[^A-Z]", "");
                                if (vpName.length() > 4 && vpName.contains(cleanName)) {
                                    int id = f.getInt(null); // Get the static ID
                                    int val = client.getVarpValue(id);
                                    bestMatchVar = "VarPlayer." + f.getName();
                                    bestMatchValue = val;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) { /* Ignore errors during search */ }

                // 3. PRINT RESULT
                boolean isMapped = false;
                int serverId = -1;
                for (Map.Entry<Integer, String> entry : loadedQuests.entrySet()) {
                    if (entry.getValue().trim().equalsIgnoreCase(name.trim())) {
                        serverId = entry.getKey();
                        isMapped = true;
                        break;
                    }
                }
                
                if (isMapped || bestMatchValue > 0) {
                     System.out.println("Quest: " + name);
                     if (serverId != -1) System.out.println("  > Server ID: " + serverId);
                     System.out.println("  > Internal State: " + q.getState(client));
                     System.out.println("  > Object Fields: [" + hiddenFields.toString() + "]");
                     System.out.println("  > Best Guess Stage: " + bestMatchVar + " = " + bestMatchValue);
                     System.out.println("--------------------------------------------------");
                }
            }
            System.out.println("=== DEEP QUEST ANALYSIS END ===");
            
            SwingUtilities.invokeLater(() -> {
                if (panel != null) panel.updateStatus("Check Console for Deep Dump.");
            });
        });
    }    
    private List<Node> getParents(Node child, List<Node> allNodes) {
        List<Node> parents = new ArrayList<>();
        for (Node n : allNodes) {
            if (n.connections.containsValue(child.id)) {
                parents.add(n);
            }
        }
        return parents;
    }

    // [ADD] Helper to compare content of two nodes (Text/Type/Mode)
    private boolean nodesMatch(Node n1, Node n2) {
        if (n1 == null || n2 == null) return false;
        if (n1.type != n2.type) return false;
        
        // Mode Check (Only for Dialogue)
        if (n1.type == Node.NodeType.DIALOGUE && n1.mode != n2.mode) return false;

        // Content Check
        if (n1.type == Node.NodeType.DIALOGUE) return n1.lines.equals(n2.lines);
        if (n1.type == Node.NodeType.OPTION) return n1.options.equals(n2.options);
        return n1.param.equals(n2.param);
    }
    private static class DialogueFileWrapper {
        int absX = -1;
        int absY = -1;
        int height = -1;
        List<Node> nodes;        
    }
    private void loadQuestMemory() {
        if (!questMemoryFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(questMemoryFile))) {
            List<QuestMemoryEntry> loaded = new Gson().fromJson(reader, new TypeToken<List<QuestMemoryEntry>>(){}.getType());
            if (loaded != null) {
                questMemory.clear();
                questMemory.addAll(loaded);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void saveQuestMemory() {
        try {
            questMemoryFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(questMemoryFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(questMemory, writer);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void recordQuestUsage(int questId, int x, int y, int z) {
        if (questId <= 0) return;
        // Check if we already have this ID recorded near this location (prevent duplicate spam)
        boolean exists = questMemory.stream().anyMatch(e -> 
            e.questId == questId && 
            e.z == z && 
            Math.abs(e.x - x) < 5 && Math.abs(e.y - y) < 5); // 5 tile tolerance for exact same spot

        if (!exists) {
            questMemory.add(new QuestMemoryEntry(questId, x, y, z));
            saveQuestMemory(); // Save immediately or on shutdown
        }
    }

    public Set<Integer> getNearbyQuestIds(int x, int y, int z) {
        Set<Integer> near = new HashSet<>();
        for (QuestMemoryEntry e : questMemory) {
            if (e.z == z && Math.abs(e.x - x) <= 32 && Math.abs(e.y - y) <= 32) {
                near.add(e.questId);
            }
        }
        return near;
    }
    private Map<Integer, Integer> captureInventory() {
        Map<Integer, Integer> snap = new HashMap<>();
        ItemContainer c = client.getItemContainer(InventoryID.INVENTORY);
        if (c != null) {
            for (Item i : c.getItems()) {
                if (i.getId() != -1) {
                    snap.merge(i.getId(), i.getQuantity(), Integer::sum);
                }
            }
        }
        return snap;
    }

    private void insertLogicBefore(GraphEditor editor, Node target, Node.Condition condition) {
        List<Node> parents = getParents(target, editor.getNodes());
        if (parents.isEmpty()) return; 

        // 1. Shift Target & Children RIGHT to make space
        shiftSubtree(target, 280, editor.getNodes()); 

        // 2. Create Logic Node (Placed in the newly created gap)
        Node logic = new Node();
        logic.id = getNextSafeId(editor.getNodes());
        logic.type = Node.NodeType.LOGIC_BRANCH;
        
        // logic takes the space relative to the NEW target position
        logic.x = target.x - 250; 
        logic.y = target.y;
        
        // 3. Add Condition
        logic.conditions.add(condition);
        
        // 4. Connect Logic -> Target
        logic.connections.put(0, target.id);
        
        // 5. Rewire Parents
        for (Node p : parents) {
            for (Map.Entry<Integer, Integer> entry : p.connections.entrySet()) {
                if (entry.getValue() == target.id) {
                    entry.setValue(logic.id);
                }
            }
        }
        
        editor.getNodes().add(logic);
        if (panel != null) panel.updateStatus("Auto-inserted Logic Node ID " + logic.id);
    }
    
    private boolean checkCondition(Node.Condition c) {
        if (c.type == Node.ConditionType.REQUIRE_ALL) {
            // All sub-conditions must be true
            for (Node.Condition sub : c.subConditions) {
                if (!checkCondition(sub)) return false;
            }
            return true;
        }
        else if (c.type == Node.ConditionType.HAS_ITEM) {
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            if (inv == null) return false;
            return inv.count(c.val1) >= c.val2;
        }
        else if (c.type == Node.ConditionType.MISSING_ITEM) {
            ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
            if (inv == null) return true; // Empty inventory means missing item? Or assume true.
            // "Missing" means we have LESS than val2
            return inv.count(c.val1) < c.val2;
        }
        else if (c.type == Node.ConditionType.QUEST_STAGE_EQUALS) {
            // Simple Quest check (if using quest memory or varbits)
            // For now, we can check our working vars if active, or just return false
            if (c.val1 == workingQuestId) return workingStage == c.val2;
            return false; // Fallback
        }
        
        return true; // Default to true for unknown types (None, etc)
    }

    private int evaluateLogicNode(Node logicNode) {
        // Loop through conditions. First one that returns TRUE is the path.
        for (int i = 0; i < logicNode.conditions.size(); i++) {
            if (checkCondition(logicNode.conditions.get(i))) {
                return i; // Index of the matching condition
            }
        }
        // If none match, return the ELSE path (last index)
        return logicNode.conditions.size();
    }
    
    private boolean conditionsMatch(Node.Condition c1, Node.Condition c2) {
        if (c1.type != c2.type) return false;
        if (c1.val1 != c2.val1) return false;
        if (c1.val2 != c2.val2) return false;
        
        // Deep check for Groups
        if (c1.type == Node.ConditionType.REQUIRE_ALL) {
             if (c1.subConditions.size() != c2.subConditions.size()) return false;
             for (int i=0; i<c1.subConditions.size(); i++) {
                 if (!conditionsMatch(c1.subConditions.get(i), c2.subConditions.get(i))) return false;
             }
        }
        return true;
    }

    // Checks if two actions are identical
    private boolean actionsMatch(Node.Action a1, Node.Action a2) {
        return a1.type == a2.type && 
               a1.val1 == a2.val1 && 
               a1.val2 == a2.val2 && 
               a1.val3 == a2.val3;
    }
    private void bypassLogicNode(GraphEditor editor, Node logicNode, Node targetNode) {
        List<Node> grandParents = getParents(logicNode, editor.getNodes());
        
        // 1. Rewire Grandparents -> Target
        for (Node gp : grandParents) {
            for (Map.Entry<Integer, Integer> entry : gp.connections.entrySet()) {
                if (entry.getValue() == logicNode.id) {
                    entry.setValue(targetNode.id);
                }
            }
        }
        
        // 2. Remove the Logic Node
        editor.getNodes().remove(logicNode);
        
        // 3. Shift Target & Children LEFT to close the gap
        shiftSubtree(targetNode, -280, editor.getNodes());

        if (panel != null) panel.updateStatus("Removed Logic Node & Re-organized graph.");
    }
    private Node.Condition copyCondition(Node.Condition original) {
        Node.Condition copy = new Node.Condition();
        copy.type = original.type;
        copy.val1 = original.val1;
        copy.val2 = original.val2;
        
        for (Node.Condition sub : original.subConditions) {
            copy.subConditions.add(copyCondition(sub));
        }
        return copy;
    }
    private void shiftSubtree(Node root, int dx, List<Node> allNodes) {
        if (root == null) return;
        Set<Integer> visited = new HashSet<>();
        List<Node> queue = new ArrayList<>();
        
        queue.add(root);
        visited.add(root.id);
        
        int head = 0;
        while(head < queue.size()){
            Node current = queue.get(head++);
            current.x += dx;
            
            for(Integer childId : current.connections.values()){
                if(!visited.contains(childId)){
                    Node child = allNodes.stream().filter(n -> n.id == childId).findFirst().orElse(null);
                    if(child != null){
                        visited.add(childId);
                        queue.add(child);
                    }
                }
            }
        }
    }
    private Node findStructuralMatch(List<Node> nodes, Node parent, List<String> childLines, Node.NodeType childType, List<Node> history) {
        if (parent == null) return null;

        for (Node candidateParent : nodes) {
            // 1. Level 1 Match: Does this candidate match our current parent?
            if (!nodesMatch(candidateParent, parent)) continue;

            // 2. Ancestry Check: Match up to 3 previous nodes (Total 4-in-a-row)
            // If checking fails, skip this candidate.
            if (!checkAncestry(candidateParent, nodes, history)) continue;

            // 3. Child Check: Does this candidate HAVE a child that matches the new text?
            for (Integer childId : candidateParent.connections.values()) {
                Node child = nodes.stream().filter(n -> n.id == childId).findFirst().orElse(null);
                if (child == null) continue;
                
                boolean match = false;
                if (child.type == childType) {
                     if (childType == Node.NodeType.DIALOGUE) match = child.lines.equals(childLines);
                     else if (childType == Node.NodeType.OPTION) match = child.options.equals(childLines);
                }
                
                if (match) return child; // Found the sequence!
            }
        }
        return null;
    }

    // [ADD THIS HELPER]
    private boolean checkAncestry(Node graphNode, List<Node> allNodes, List<Node> history) {
        if (history.isEmpty()) return true;

        // We traverse BACKWARDS through history
        // history.size()-1 is the node we just left (GrandParent relative to Child)
        // BUT 'graphNode' corresponds to 'parent' (which is effectively current active).
        // So we compare graphNode's PARENTS against history.get(size-1).
        
        return recursiveAncestryCheck(graphNode, allNodes, history, history.size() - 1, 0);
    }

    private boolean recursiveAncestryCheck(Node currentGraphNode, List<Node> allNodes, List<Node> history, int historyIdx, int depth) {
        // SUCCESS CONDITIONS:
        // 1. We matched enough nodes (Depth 3 ancestors + Child = 4 total)
        if (depth >= 3) return true;
        
        // 2. We ran out of history to check (Start of conversation matched)
        if (historyIdx < 0) return true;

        // 3. OPTION DISMISSAL: If we match an OPTION node, it's a unique branch point. Confirm immediately.
        if (currentGraphNode.type == Node.NodeType.OPTION) return true;

        // GET PARENTS of the current graph node
        List<Node> parents = getParents(currentGraphNode, allNodes);
        if (parents.isEmpty()) {
            // No parents in graph, but we have history? Mismatch unless history is also empty (handled above).
            return false; 
        }

        Node historicNode = history.get(historyIdx);
        
        // Check if ANY parent matches the historic node
        for (Node p : parents) {
            if (nodesMatch(p, historicNode)) {
                // Recurse deeper
                if (recursiveAncestryCheck(p, allNodes, history, historyIdx - 1, depth + 1)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    private int getStrictQuestSlot(Node logicNode) {
        if (logicNode == null || logicNode.type != Node.NodeType.LOGIC_BRANCH) return 0;
        
        // 1. STRICT MODE: If we are working on a Quest, prioritize the Stage Match.
        // We removed 'logicNode.id == 0' so this works for ALL Quest Hubs (forked or root).
        if (workingOnQuest && workingQuestId != -1) {
            
            // Check if this node is actually related to Quests (has at least one Quest condition)
            // If it's a randomizer node (Type=NONE), we shouldn't force quest logic.
            boolean isQuestNode = logicNode.conditions.stream()
                .anyMatch(c -> c.type.toString().startsWith("QUEST"));

            if (isQuestNode) {
                for (int i = 0; i < logicNode.conditions.size(); i++) {
                    Node.Condition c = logicNode.conditions.get(i);
                    if (c.type == Node.ConditionType.QUEST_STAGE_EQUALS && 
                        c.val1 == workingQuestId && c.val2 == workingStage) {
                        return i; // Found exact stage slot
                    }
                }
                // If stage not found, return the END (New Slot/Else), NEVER 0 (unless 0 is empty)
                return logicNode.conditions.size();
            }
        }
        
        // 2. Fallback: Use standard evaluation (for Randomizers/Stat Checks)
        return evaluateLogicNode(logicNode);
    }
    private void updateQuestList(Set<Integer> nearby) {
        if (questBox == null) return;
        
        isUpdatingQuestList = true; // Lock listener
        
        // 1. Preserve Selection
        Object selectedItem = questBox.getSelectedItem();
        String selectedString = (selectedItem instanceof String) ? (String) selectedItem : null;
        
        // 2. Clear & Re-populate
        questBox.removeAllItems();
        
        if (!loadedQuests.isEmpty()) {
            List<String> topList = new ArrayList<>();
            List<String> bottomList = new ArrayList<>();

            for (Map.Entry<Integer, String> entry : loadedQuests.entrySet()) {
                String label = entry.getValue() + " (" + entry.getKey() + ")";
                // If it is in the 'nearby' set, goes to TOP. Else BOTTOM.
                if (nearby.contains(entry.getKey())) {
                    topList.add(label);
                } else {
                    bottomList.add(label);
                }
            }

            for (String s : topList) questBox.addItem(s);
            for (String s : bottomList) questBox.addItem(s);
        }
        
        // 3. Restore Selection (If it still exists)
        if (selectedString != null) {
            questBox.setSelectedItem(selectedString);
        }
        
        // 4. Update Renderer (To Color the Nearby items)
        final Set<Integer> highlightSet = nearby;
        questBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (!isSelected && value instanceof String) {
                    try {
                        String str = (String) value;
                        int start = str.lastIndexOf('(') + 1;
                        int end = str.lastIndexOf(')');
                        int id = Integer.parseInt(str.substring(start, end));
                        if (highlightSet.contains(id)) {
                            c.setForeground(Color.CYAN);
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        }
                    } catch (Exception ex) { }
                }
                return c;
            }
        });
        
        isUpdatingQuestList = false; // Unlock listener
    }
    private Node insertLogicSplitter(Node parent, int parentOutputIdx, Node existingChild) {
        // 1. Create the Logic Node
        Node logic = new Node();
        logic.id = getNextSafeId(parent == null ? new ArrayList<>() : getParents(parent, getActiveEditor().getNodes())); 
        // (Safe ID fetch might need editor context, simplified here to use existing helper if available or active editor)
        if (getActiveEditor() != null) logic.id = getNextSafeId(getActiveEditor().getNodes());
        
        logic.type = Node.NodeType.LOGIC_BRANCH;
        logic.x = parent.x + 220;
        logic.y = parent.y;

        // 2. Configure Conditions based on Mode
        if (workingOnQuest && workingQuestId != -1) {
            // Condition 0: Current Quest Stage (The NEW path we are about to create)
            Node.Condition cQuest = new Node.Condition();
            cQuest.type = Node.ConditionType.QUEST_STAGE_EQUALS;
            cQuest.val1 = workingQuestId;
            cQuest.val2 = workingStage;
            logic.conditions.add(cQuest);
            
            // Condition 1: Else (The OLD path)
            Node.Condition cElse = new Node.Condition();
            cElse.type = Node.ConditionType.NONE; // Acts as 'Else' or 'Default'
            logic.conditions.add(cElse);
        } else {
            // Generic Split (Cond 0 = New, Cond 1 = Old)
            logic.conditions.add(new Node.Condition()); // Slot 0
            logic.conditions.add(new Node.Condition()); // Slot 1
        }

        // 3. Wire the OLD path to the 'Else' / Secondary slot
        // Slot 0 is left EMPTY. The 'process' loop will naturally link the NEW dialogue to it in Step 6.
        int elseSlot = logic.conditions.size() - 1; 
        if (existingChild != null) {
            logic.connections.put(elseSlot, existingChild.id);
        }

        // 4. Add to Graph
        if (getActiveEditor() != null) getActiveEditor().getNodes().add(logic);

        // 5. Rewire Parent -> Logic
        parent.connections.put(parentOutputIdx, logic.id);
        
        if (panel != null) panel.updateStatus("Divergence detected: Splitting path with Logic Node " + logic.id);
        
        return logic;
    }
    
    public void openComparer() {
        SwingUtilities.invokeLater(() -> {
            // Pass 'this' (ExamplePlugin instance) to the GUI constructor
            new NpcComparerGUI(this).setVisible(true);
        });
    }
    public Client getClient() {
        return client;
    }

    /**
     * Exposes the ClientThread.
     * Required by RenderPanelOSRS to run code on the game thread.
     */
    public ClientThread getClientThread() {
        return clientThread;
    }
}