package com.model.rs317;

public class RS317Npc {
    
    public String name;
    public byte[] description;
    public int combatLevel = -1;
    public int[] models;
    public int size = 1;
    public int id;
    public String[] actions = new String[5];
    
    // [ADDED] Animation IDs
    public int standAnim = -1;
    public int walkAnim = -1;
    
    public int[] originalColors;
    public int[] modifiedColors;

    public static RS317Npc decode(int id, RS317Stream data) {
        RS317Npc def = new RS317Npc();
        def.id = id;
        
        while(true) {
            int opcode = data.readUnsignedByte();
            if(opcode == 0) break;
            
            if(opcode == 1) {
                int len = data.readUnsignedByte();
                def.models = new int[len];
                for(int i=0; i<len; i++) def.models[i] = data.readUnsignedWord();
            } else if(opcode == 2) {
                def.name = data.readString();
            } else if(opcode == 3) {
                def.description = data.readString().getBytes();
            } else if(opcode == 12) {
                def.size = data.readUnsignedByte();
            } else if(opcode == 13) {
                // [FIXED] Store Standing Animation
                def.standAnim = data.readUnsignedWord(); 
            } else if(opcode == 14) {
                // [FIXED] Store Walking Animation
                def.walkAnim = data.readUnsignedWord(); 
            } else if(opcode == 17) {
                def.walkAnim = data.readUnsignedWord(); 
                data.readUnsignedWord(); // Turn 180
                data.readUnsignedWord(); // Turn 90 CW
                data.readUnsignedWord(); // Turn 90 CCW
            } else if(opcode >= 30 && opcode < 35) {
                def.actions[opcode - 30] = data.readString();
                if (def.actions[opcode - 30].equalsIgnoreCase("hidden")) {
                    def.actions[opcode - 30] = null;
                }
            } else if(opcode == 40) {
                int len = data.readUnsignedByte();
                def.originalColors = new int[len];
                def.modifiedColors = new int[len];
                for(int i=0; i<len; i++) {
                    def.originalColors[i] = data.readUnsignedWord();
                    def.modifiedColors[i] = data.readUnsignedWord();
                }
            } else if(opcode == 60) {
                int len = data.readUnsignedByte();
                for(int i=0; i<len; i++) data.readUnsignedWord();
            } else if(opcode == 93) {
               // minimap dot
            } else if(opcode == 95) {
                def.combatLevel = data.readUnsignedWord();
            } else if(opcode == 97) {
                data.readUnsignedWord(); 
            } else if(opcode == 98) {
                data.readUnsignedWord(); 
            } else if(opcode == 102) {
                data.readUnsignedWord(); 
            } else if(opcode == 103) {
                data.readUnsignedWord(); 
            }
        }
        return def;
    }
}