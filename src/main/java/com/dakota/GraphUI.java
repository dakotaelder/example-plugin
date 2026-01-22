package com.dakota;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Map;

public class GraphUI extends JPanel
{
    private final List<Node> nodes;
    private Node activeNode; // The node the player is currently "in"
    private double scale = 1.0;
    private int offsetX = 0, offsetY = 0;

    public GraphUI(List<Node> nodes)
    {
        this.nodes = nodes;
        setBackground(new Color(30, 30, 30)); // CEOTheme.BG_CANVAS
        
        // Basic Pan/Zoom support could go here, omitting for brevity
    }

    public void setActiveNode(Node n) {
        this.activeNode = n;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        AffineTransform at = new AffineTransform();
        at.translate(offsetX, offsetY);
        at.scale(scale, scale);
        g2.transform(at);

        // Grid
        g2.setColor(new Color(36, 36, 36));
        for(int i=0; i<2000; i+=40) {
            g2.drawLine(i, 0, i, 2000);
            g2.drawLine(0, i, 2000, i);
        }

        // Draw Connections
        for(Node n : nodes) {
            for(Map.Entry<Integer, Integer> entry : n.connections.entrySet()) {
                Node t = nodes.stream().filter(x -> x.id == entry.getValue()).findFirst().orElse(null);
                if(t != null) {
                    drawCurve(g2, getSocketRect(n, entry.getKey()), new Rectangle(t.x, t.y+20, 0, 0), Color.GRAY);
                }
            }
        }

        // Draw Nodes
        for(Node n : nodes) {
            n.height = 60 + (n.getOutputCount() * 25);

            // Highlight Active Node
            if (n == activeNode) {
                g2.setColor(new Color(255, 165, 0, 100)); // Orange Glow
                g2.fillRoundRect(n.x - 5, n.y - 5, n.width + 10, n.height + 10, 15, 15);
            }

            // Body
            g2.setColor(new Color(40, 40, 43)); // CEOTheme.BG_PANEL
            g2.fillRoundRect(n.x, n.y, n.width, n.height, 10, 10);

            // Header
            Color headerCol = new Color(50, 80, 55);
            if (n.type == Node.NodeType.OPTION) headerCol = new Color(85, 50, 30);
            if (n.mode == Node.DialogueMode.PLAYER_TALK) headerCol = new Color(40, 60, 80);
            
            g2.setColor(headerCol);
            g2.fillRoundRect(n.x, n.y, n.width, 30, 10, 10);
            g2.fillRect(n.x, n.y+15, n.width, 15);

            // Text
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            String title = n.type == Node.NodeType.DIALOGUE ? n.mode.toString() : "OPTIONS";
            g2.drawString(title, n.x+10, n.y+20);

            // Lines Preview
            g2.setColor(Color.LIGHT_GRAY);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            int textY = n.y + 50;
            if (n.type == Node.NodeType.DIALOGUE) {
                for(String line : n.lines) {
                    if (textY < n.y + n.height - 10) g2.drawString(line, n.x+10, textY);
                    textY += 15;
                }
            } else {
                 for(String opt : n.options) {
                    g2.drawString("- " + opt, n.x+10, textY);
                    textY += 15;
                 }
            }
        }
    }

    private Rectangle getSocketRect(Node n, int i) {
        return new Rectangle(n.x + n.width - 12, n.y + 50 + (i*25), 12, 12);
    }

    private void drawCurve(Graphics2D g2, Rectangle r1, Rectangle r2, Color color) {
        Path2D p = new Path2D.Float();
        p.moveTo(r1.x+6, r1.y+6);
        p.curveTo(r1.x+50, r1.y, r2.x-50, r2.y, r2.x, r2.y);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(2f));
        g2.draw(p);
    }
}