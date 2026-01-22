package com.model;

import com.model.rs317.Viewer317;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.callback.ClientThread;
import javax.swing.*;
import java.awt.*;

public class ModelViewerPanel extends PluginPanel {

    private final ModelViewerPlugin plugin;
    
    // Instance for the new 317 Viewer
    private Viewer317 viewer317;
    // Instance for the original OSRS Viewer
    private ModelViewerGUI osrsGui;

    public ModelViewerPanel(ModelViewerPlugin plugin, ClientThread clientThread) {
        this.plugin = plugin;
        setLayout(new BorderLayout());

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Cache Tool Suite");
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // --- BUTTON 1: Original OSRS Viewer ---
        JButton btnLaunchOSRS = new JButton("Open OSRS Viewer");
        btnLaunchOSRS.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLaunchOSRS.addActionListener(e -> openOSRSViewer());

        // --- BUTTON 2: New 317 Viewer ---
        JButton btnLaunch317 = new JButton("Open 317 NPC Viewer");
        btnLaunch317.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLaunch317.setBackground(new Color(60, 80, 60)); // Slight color diff
        btnLaunch317.addActionListener(e -> open317Viewer());

        container.add(title);
        container.add(Box.createVerticalStrut(10));
        container.add(btnLaunchOSRS);
        container.add(Box.createVerticalStrut(10));
        container.add(btnLaunch317);
        
        container.add(Box.createVerticalStrut(20));
        JTextArea help = new JTextArea("OSRS Viewer: Uses Runelite API\n317 Viewer: Uses standalone cache_372");
        help.setLineWrap(true);
        help.setWrapStyleWord(true);
        help.setEditable(false);
        help.setOpaque(false);
        help.setFont(new Font("SansSerif", Font.ITALIC, 11));
        container.add(help);

        add(container, BorderLayout.NORTH);
    }

    private void openOSRSViewer() {
        try {
            if (osrsGui == null || !osrsGui.isVisible()) {
                osrsGui = new ModelViewerGUI(plugin);
                osrsGui.setVisible(true);
            } else {
                osrsGui.toFront();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void open317Viewer() {
        if (viewer317 == null || !viewer317.isVisible()) {
            // Run on Swing thread
            SwingUtilities.invokeLater(() -> {
                viewer317 = new Viewer317();
                viewer317.setVisible(true);
            });
        } else {
            viewer317.toFront();
        }
    }
    public void closeGUI() {
        if (osrsGui != null) {
        	osrsGui.dispose();
        	osrsGui = null;
        }
    }

}