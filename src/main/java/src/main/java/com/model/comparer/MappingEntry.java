package src.main.java.com.model.comparer;

public class MappingEntry {
    public int osrsId;
    public String osrsName;
    public int rs317Id;
    public String rs317Name;
    public int combatLevel;
    public double score; // [NEW] Store match percentage

    public MappingEntry(int osrsId, String osrsName, int rs317Id, String rs317Name, int combatLevel, double score) {
        this.osrsId = osrsId;
        this.osrsName = osrsName;
        this.rs317Id = rs317Id;
        this.rs317Name = rs317Name;
        this.combatLevel = combatLevel;
        this.score = score;
    }
    
    @Override
    public String toString() {
        return String.format("OSRS: %s (%d) <-> 317: %s (%d) [%.0f%%]", osrsName, osrsId, rs317Name, rs317Id, score * 100);
    }
}