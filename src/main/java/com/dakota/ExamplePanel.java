package com.dakota;

import net.runelite.client.ui.PluginPanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ExamplePanel extends PluginPanel
{
    private final JButton launchBtn;
    private final JTextArea statusArea;

    private final ExamplePlugin plugin; // Add reference

    public ExamplePanel(Runnable openGuiAction, ExamplePlugin plugin) // Update constructor
    {
        super();
        this.plugin = plugin; // Store reference
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setLayout(new BorderLayout());

        // Title
        JLabel title = new JLabel("Dialogue Mapper", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);
        
        // Status Area (Simple log)
        statusArea = new JTextArea("Waiting for dialogue...");
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setEditable(false);
        statusArea.setOpaque(false);
        statusArea.setForeground(Color.LIGHT_GRAY);
        statusArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        // The "MUST HAVE" Launch Button
        launchBtn = new JButton("OPEN EDITOR GUI");
        launchBtn.setBackground(new Color(0, 122, 204));
        launchBtn.setForeground(Color.WHITE);
        launchBtn.setFocusPainted(false);
        launchBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        launchBtn.setPreferredSize(new Dimension(100, 40));
        launchBtn.addActionListener(e -> openGuiAction.run());

        JButton dumpQuestsBtn = new JButton("Dump Quest IDs");
        dumpQuestsBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        dumpQuestsBtn.setBackground(new Color(60, 100, 60)); // Dark Green
        dumpQuestsBtn.setForeground(Color.WHITE);
        dumpQuestsBtn.addActionListener(e -> plugin.dumpQuests());

        // [NEW] NPC Comparer Button
        JButton btnComparer = new JButton("Launch NPC Comparer");
        btnComparer.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnComparer.setBackground(new Color(150, 80, 40)); // Brownish
        btnComparer.setForeground(Color.WHITE);
        btnComparer.addActionListener(e -> plugin.openComparer());
        
        // Layout
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        northPanel.setOpaque(false);
        
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusArea.setAlignmentX(Component.CENTER_ALIGNMENT);
        launchBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel modeLabel = new JLabel("NPC Filters:");
        modeLabel.setForeground(Color.GRAY);
        modeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JComboBox<String> modeBox = new JComboBox<>(new String[]{"Disabled", "Mark Finished (Black)", "Hide Finished"});
        modeBox.setFocusable(false);
        modeBox.setMaximumSize(new Dimension(200, 30));
        modeBox.addActionListener(e -> {
            plugin.setNpcMode(modeBox.getSelectedIndex());
        });

        northPanel.add(Box.createVerticalStrut(15));
        northPanel.add(modeLabel);
        northPanel.add(Box.createVerticalStrut(5));
        northPanel.add(modeBox);
        JCheckBox releaseDateCheck = new JCheckBox("Show Release Dates");
        releaseDateCheck.setForeground(Color.LIGHT_GRAY);
        releaseDateCheck.setFocusable(false);
        releaseDateCheck.setOpaque(false);
        releaseDateCheck.setAlignmentX(Component.CENTER_ALIGNMENT);
        releaseDateCheck.addActionListener(e -> {
            plugin.setShowReleaseDates(releaseDateCheck.isSelected());
        });
        
        northPanel.add(Box.createVerticalStrut(5));
        northPanel.add(releaseDateCheck);
        northPanel.add(title);
        northPanel.add(Box.createVerticalStrut(15));
        northPanel.add(launchBtn); 
        northPanel.add(Box.createVerticalStrut(15));
        northPanel.add(Box.createVerticalStrut(5)); // Add spacing
        northPanel.add(dumpQuestsBtn); // Add the button to the panel
        
        // [NEW] Added here in the layout
        northPanel.add(Box.createVerticalStrut(10));
        northPanel.add(btnComparer);
        
        northPanel.add(statusArea);

        add(northPanel, BorderLayout.NORTH);
    }

    public void updateStatus(String status) {
        statusArea.setText(status);
    }
}