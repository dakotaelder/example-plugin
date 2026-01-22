package com.dakota;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import javax.inject.Inject;
import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ChatboxOverlay extends Overlay {

    private final Client client;
    private final ExamplePlugin plugin;

    @Inject
    public ChatboxOverlay(Client client, ExamplePlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.HIGH);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Widget optionsWidget = client.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS);
        if (optionsWidget == null || optionsWidget.isHidden()) return null;

        GraphEditor editor = plugin.getActiveEditor();
        if (editor == null || editor.currentNode == null) return null;

        Node activeNode = editor.currentNode;
        if (activeNode.type != Node.NodeType.OPTION) return null;

        Widget[] children = optionsWidget.getChildren();
        if (children == null) return null;

        // Create a quick lookup map for performance: ID -> Node
        Map<Integer, Node> nodeMap = editor.getNodes().stream()
                .collect(Collectors.toMap(n -> n.id, n -> n));

        // Iterate Options (Index 1+)
        for (int i = 1; i < children.length; i++) {
            Widget w = children[i];
            if (w.getText() == null || w.getText().isEmpty()) continue;

            int optionIndex = i - 1;

            // 1. IMMEDIATE CHECK: Is the link missing right now?
            if (!activeNode.connections.containsKey(optionIndex)) {
                drawGreenBox(graphics, w);
            } 
            else {
                // Connection exists. Let's look into the future.
                int targetId = activeNode.connections.get(optionIndex);
                Node targetNode = nodeMap.get(targetId);

                if (targetNode != null) {
                    // 2. DEPTH CHECK: Is there a missing link within 9 steps?
                    if (hasUnfinishedPath(targetNode, nodeMap, 9, new HashSet<>())) {
                        drawRedX(graphics, w);
                    } 
                    // 3. COMPLETION CHECK: Is the ENTIRE tree finished?
                    else if (isFullyFinished(targetNode, nodeMap, new HashSet<>())) {
                        drawGreenCheck(graphics, w);
                    }
                }
            }
        }
        return null;
    }

    // --- VISUALS ---

    private void drawGreenBox(Graphics2D g, Widget w) {
        g.setColor(new Color(0, 255, 0, 180));
        g.setStroke(new BasicStroke(2));
        g.draw(w.getBounds());
        g.setColor(new Color(0, 255, 0, 30));
        g.fill(w.getBounds());
    }

    private void drawRedX(Graphics2D g, Widget w) {
        Rectangle b = w.getBounds();
        int x = b.x + b.width - 25; // Draw on the right side
        int y = b.y + (b.height / 2);

        g.setColor(Color.RED);
        g.setStroke(new BasicStroke(3));
        g.drawLine(x - 6, y - 6, x + 6, y + 6);
        g.drawLine(x + 6, y - 6, x - 6, y + 6);
    }

    private void drawGreenCheck(Graphics2D g, Widget w) {
        Rectangle b = w.getBounds();
        int x = b.x + b.width - 25;
        int y = b.y + (b.height / 2);

        g.setColor(Color.GREEN);
        g.setStroke(new BasicStroke(3));
        g.drawLine(x - 6, y, x - 2, y + 6);
        g.drawLine(x - 2, y + 6, x + 6, y - 6);
    }

    // --- ALGORITHMS ---

    /**
     * Recursive check: Returns TRUE if we find any "Unfinished Option" node within 'depth' steps.
     */
    private boolean hasUnfinishedPath(Node start, Map<Integer, Node> allNodes, int depth, Set<Integer> visited) {
        if (depth <= 0 || start == null) return false;
        if (visited.contains(start.id)) return false; // Loop detected, assume safe
        visited.add(start.id);

        // If this is an OPTION node, check if it has missing connections
        if (start.type == Node.NodeType.OPTION) {
            // Check if we have fewer connections than actual options
            // (Assuming start.options is populated correctly from the mapper)
            if (start.options != null && start.connections.size() < start.options.size()) {
                return true; // FOUND ONE!
            }
        }

        // Recurse children
        for (Integer childId : start.connections.values()) {
            Node child = allNodes.get(childId);
            if (hasUnfinishedPath(child, allNodes, depth - 1, new HashSet<>(visited))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Recursive check: Returns TRUE only if EVERY path leads to a valid end or loop, 
     * with NO missing option links anywhere in the subtree.
     */
    private boolean isFullyFinished(Node start, Map<Integer, Node> allNodes, Set<Integer> visited) {
        if (start == null) return true;
        if (visited.contains(start.id)) return true; // Loop is considered "finished"
        visited.add(start.id);

        // If it's an Option node, it MUST have all slots filled
        if (start.type == Node.NodeType.OPTION) {
            if (start.options != null && start.connections.size() < start.options.size()) {
                return false; // Not finished
            }
        }

        // Verify ALL children are finished
        for (Integer childId : start.connections.values()) {
            Node child = allNodes.get(childId);
            if (!isFullyFinished(child, allNodes, new HashSet<>(visited))) {
                return false;
            }
        }

        return true;
    }
}