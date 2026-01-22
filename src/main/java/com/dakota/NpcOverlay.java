package com.dakota;

import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import javax.inject.Inject;
import java.awt.*;

public class NpcOverlay extends Overlay
{
    private final Client client;
    private final ExamplePlugin plugin;

    @Inject
    public NpcOverlay(Client client, ExamplePlugin plugin)
    {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        }

    @Override
    public Dimension render(Graphics2D g)
    {
        int mode = plugin.getNpcMode();
        if (mode == 0)  {
        	if (plugin.isShowReleaseDates()) {
                g.setFont(new Font("SansSerif", Font.TRUETYPE_FONT, 9));
                FontMetrics fm = g.getFontMetrics();

                for (NPC npc : client.getNpcs()) {
                    if (npc == null) continue;
                    
                    String date = plugin.getReleaseDates().get(npc.getId());
                    if (date != null) {
                    	String text = date.replace("\\n", "").trim();
                    	
                        // 1. PARSE YEAR
                        int year = 0;
                        // Find first 4-digit number in the date string
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d{4}").matcher(date);
                        if (m.find()) {
                            year = Integer.parseInt(m.group());
                        }

                        // 2. DETERMINE COLOR & STYLE
                        Color textColor = new Color(100, 150, 255); // Default Blue (< 2008)
                        boolean strikeThrough = false;

                        if (plugin.getFinishedNpcs().contains(npc)) {
                            textColor = Color.GREEN; // Finished Override
                        } else if (year >= 2008) {
                            textColor = Color.RED;   // Post-2008
                            strikeThrough = true;
                        }

                        // 3. RENDER
                        Point textLoc = npc.getCanvasTextLocation(g, text, npc.getLogicalHeight() + 60);
                        if (textLoc != null) {
                            int x = textLoc.getX();
                            int y = textLoc.getY();
                            int w = fm.stringWidth(text);
                            int h = fm.getAscent(); 

                            // Background (Black Transparent)
                            g.setColor(new Color(0, 0, 0, 180)); 
                            g.fillRect(x - 3, y - h - 3, w + 6, h + 6);
                            
                            // Border (Matches Text Color)
                            g.setColor(textColor); 
                            g.drawRect(x - 3, y - h - 3, w + 6, h + 6);

                            // Text
                            g.drawString(text, x, y);
                            
                            // Strikethrough Effect
                            if (strikeThrough) {
                                g.setStroke(new BasicStroke(2));
                                g.setColor(Color.RED);
                                int middle = y - (h / 3);
                                g.drawLine(x, middle, x + w, middle);
                            }
                        }
                    }
                }
            }
        	return null; // Disabled
        }
        for (NPC npc : plugin.getFinishedNpcs())
        {
            if (npc == null) continue;

            // MODE 1: MARK FINISHED (Black Box on Model)
            if (mode == 1) {
                Point mini = Perspective.localToMinimap(client, npc.getLocalLocation());
                if (mini != null) {
                    // Draw a circle matching the map background/black over the dot
                    g.setColor(Color.BLACK); 
                    g.fillOval(mini.getX()-2 , mini.getY()-2, 5, 5); 
                }
                 if (npc.getConvexHull() != null) {
                     g.setColor(Color.BLACK);
                     g.fill(npc.getConvexHull());
                     g.setColor(new Color(50, 50, 50));
                     g.draw(npc.getConvexHull());
                 }
                 npc.setAnimation(804);//838 714
            }

            // MODE 2: HIDE FINISHED (Cover Minimap Dot)
            if (mode == 2) {
                 // Remove from Minimap (Visual Cover)
                 Point mini = Perspective.localToMinimap(client, npc.getLocalLocation());
                 if (mini != null) {
                     // Draw a circle matching the map background/black over the dot
                     g.setColor(Color.BLACK); 
                     g.fillOval(mini.getX()-2 , mini.getY()-2, 5, 5); 
                 }
                 npc.setAnimation(804);
                 // Note: True "Invisibility" of the 3D model requires advanced hooks not available 
                 // in the standard API. However, the Menu Hider (already implemented) 
                 // prevents you from clicking them.
            }
            String date = plugin.getReleaseDates().get(npc.getId());
            if (date != null) {
                String text = "Released: " + date;
                
                // Get text location above head
                Point textLoc = npc.getCanvasTextLocation(g, text, npc.getLogicalHeight() + 40);
                
                if (textLoc != null) {
                    // Draw Text with Shadow for readability
                    g.setColor(Color.BLACK);
                    g.drawString(text, textLoc.getX() + 1, textLoc.getY() + 1);
                    
                    g.setColor(Color.YELLOW);
                    g.drawString(text, textLoc.getX(), textLoc.getY());
                }
            }
        }
        return null;
    }
    
}