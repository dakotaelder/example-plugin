package com.dakota;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

public class DarkUI
{
    public static final Color BACKGROUND = new Color(30, 30, 30);
    public static final Color PANEL_BG = new Color(45, 45, 45);
    public static final Color TEXT_COLOR = new Color(220, 220, 220);
    public static final Color ACCENT = new Color(0, 120, 215); // RuneLite Blue-ish

    public static JFrame createDarkFrame(String title)
    {
        JFrame frame = new JFrame(title);
        frame.getContentPane().setBackground(BACKGROUND);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(600, 400);
        return frame;
    }

    // Custom "Graphics2D" Button
    public static class DarkButton extends JButton
    {
        private Color currentColor = PANEL_BG;

        public DarkButton(String text, Consumer<MouseEvent> onClick)
        {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorder(new EmptyBorder(10, 20, 10, 20));
            setForeground(TEXT_COLOR);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent e)
                {
                    currentColor = PANEL_BG.brighter();
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    currentColor = PANEL_BG;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e)
                {
                    currentColor = ACCENT;
                    repaint();
                    onClick.accept(e);
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    currentColor = PANEL_BG.brighter();
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g)
        {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Draw rounded background
            g2.setColor(currentColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

            // Draw Border
            g2.setColor(new Color(60, 60, 60));
            g2.setStroke(new BasicStroke(1));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

            super.paintComponent(g);
        }
    }
}