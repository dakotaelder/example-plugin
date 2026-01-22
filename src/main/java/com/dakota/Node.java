package com.dakota;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Node implements Serializable
{
	public enum ConditionType {
        NONE("None", ""),
        REQUIRE_ALL("Group Description", ""),
        HAS_ITEM("Item ID", "Amount"),
        MISSING_ITEM("Item ID", "Amount"),
        WEARING_ITEM("Item ID", "Slot ID"),
        STAT_LEVEL("Skill ID", "Level Req"),
        QUEST_STAGE_EQUALS("Quest ID", "State"),
        QUEST_STAGE_EQUALS_GREATER("Quest ID", "State"),
        FREE_INVENTORY_SLOTS("Amount Needed", ""),
        DATE_RANGE("Start Date", "End Date");

        final String label1, label2;
        ConditionType(String l1, String l2) { this.label1 = l1; this.label2 = l2; }
    }

    public static class Condition implements Serializable {
        public ConditionType type = ConditionType.HAS_ITEM;
        public int val1 = -1;
        public int val2 = 1;
        public List<Condition> subConditions = new ArrayList<>(); 
    }

    public enum ActionType {
        GIVE_ITEM("Item ID", "Amount", ""), 
        REMOVE_ITEM("Item ID", "Amount", ""), 
        TELEPORT("X Coord", "Y Coord", "Height"), 
        ANIMATION("Anim ID", "", ""), 
        GRAPHICS("Gfx ID", "", ""), 
        SET_QUEST_STAGE("Quest ID", "Stage", "");
        
        final String l1, l2, l3;
        ActionType(String l1, String l2, String l3) { this.l1=l1; this.l2=l2; this.l3=l3; }
    }

    public static class Action implements Serializable {
        public ActionType type = ActionType.GIVE_ITEM;
        public int val1, val2, val3;
    }

    // --- NEW: VARIANT CLASS FOR DATE LOGIC ---
    public static class Variant implements Serializable {
        public int startDate = 20010101;
        public int endDate = 20010101;
        
        // Visual Overrides
        public int npcId = -1;
        public int animationId = -1;
        public String param = ""; 
        
        // Content
        public List<String> lines = new ArrayList<>();
        public List<String> options = new ArrayList<>();
        
        // Logic
        public List<Condition> conditions = new ArrayList<>();
        public List<Action> actions = new ArrayList<>();
    }
    
    public List<Variant> variants = new ArrayList<>();
    // -----------------------------------------

    // Global / Default properties (The "Else" block)
    public List<Condition> conditions = new ArrayList<>();
    public List<Action> actions = new ArrayList<>();

    public int getOutputCount() {
        if (type == NodeType.OPTION) return options.size();
        if (type == NodeType.LOGIC_BRANCH) return conditions.size() + 1; 
        return 1;
    }
    
    public enum NodeType { DIALOGUE, OPTION, LOGIC_BRANCH, SHOP, INTERFACE }
    public enum DialogueMode { NPC_TALK, PLAYER_TALK, INFO_BOX }

    public int id;
    public NodeType type = NodeType.DIALOGUE;
    public DialogueMode mode = DialogueMode.NPC_TALK;
    
    public int x = 100, y = 100, width = 200, height = 100;
    public int npcId = -1;
    public int animationId = -1;
    public String param = ""; 
    public int releaseDate = 20010101; // Default Date

    public List<String> lines = new ArrayList<>();
    public List<String> options = new ArrayList<>();
    
    public Map<Integer, Integer> connections = new HashMap<>();

    public Node() {}
}