package src.main.java.com.model.comparer;

import java.util.List;

public class NpcDefFullOsrs {
    public int id;
    public String name;
    public int combatLevel;
    public String[] actions;
    public int[] models;
    public List<ColourReplacement> colourReplacements;

    public static class ColourReplacement {
        public int original;
        public int replacement;
    }
}