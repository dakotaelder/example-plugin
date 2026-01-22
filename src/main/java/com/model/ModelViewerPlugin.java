package com.model;

import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.callback.ClientThread;
import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.Color;

@PluginDescriptor(
    name = "Model Viewer",
    description = "Browse and View NPC Models externally",
    tags = {"npc", "model", "viewer"},
    enabledByDefault = false
)
public class ModelViewerPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ClientThread clientThread;

    private ModelViewerPanel panel;
    private NavigationButton navButton;
    
    // We no longer need the Overlay or 'selectedNpcId' state here
    // because the GUI will handle everything locally.

    @Override
    protected void startUp() throws Exception {
        // Generate temporary icon
        BufferedImage icon = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) icon.getGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, 30, 30);
        g.dispose();

        panel = new ModelViewerPanel(this, clientThread);
        
        navButton = NavigationButton.builder()
                .tooltip("Model Viewer")
                .icon(icon)
                .priority(6)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
    }

    @Override
    protected void shutDown() {
        clientToolbar.removeNavigation(navButton);
        if (panel != null) {
            panel.closeGUI();
        }
    }

    public Client getClient() { return client; }
    public ClientThread getClientThread() { return clientThread; }
}