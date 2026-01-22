package com.model.rs459;

public class NpcDefinition {
    public int id;
    public String name = "null";
    public int size = 1;
    public int[] modelIds;
    public int standAnim = -1;
    public int walkAnim = -1;
    public String[] actions = new String[5];
    public int combatLevel = -1;
    public int headIcon = -1;
    public short[] originalColors;
    public short[] modifiedColors;
    
    // Additional Anims
    public int turn180 = -1;
    public int turn90CW = -1;
    public int turn90CCW = -1;

    public NpcDefinition(int id, byte[] data) {
        this.id = id;
        if (data != null) decode(new Buffer(data));
    }

    private void decode(Buffer buffer) {
        while (true) {
            int opcode = buffer.readUnsignedByte();
            if (opcode == 0) break;

            if (opcode == 1) {
                int count = buffer.readUnsignedByte();
                modelIds = new int[count];
                for (int i = 0; i < count; i++) {
                    modelIds[i] = buffer.readUnsignedShort();
                }
            } else if (opcode == 2) {
                name = buffer.readString();
            } else if (opcode == 12) {
                size = buffer.readUnsignedByte();
            } else if (opcode == 13) {
                standAnim = buffer.readUnsignedShort();
            } else if (opcode == 14) {
                walkAnim = buffer.readUnsignedShort();
            } else if (opcode == 17) {
                walkAnim = buffer.readUnsignedShort();
                turn180 = buffer.readUnsignedShort();
                turn90CW = buffer.readUnsignedShort();
                turn90CCW = buffer.readUnsignedShort();
            } else if (opcode >= 30 && opcode < 35) {
                actions[opcode - 30] = buffer.readString();
                if (actions[opcode - 30].equalsIgnoreCase("hidden")) actions[opcode - 30] = null;
            } else if (opcode == 40) {
                int count = buffer.readUnsignedByte();
                originalColors = new short[count];
                modifiedColors = new short[count];
                for (int i = 0; i < count; i++) {
                    originalColors[i] = (short) buffer.readUnsignedShort();
                    modifiedColors[i] = (short) buffer.readUnsignedShort();
                }
            } else if (opcode == 60) {
                int count = buffer.readUnsignedByte();
                int[] headModels = new int[count];
                for(int i=0; i<count; i++) headModels[i] = buffer.readUnsignedShort();
            } else if (opcode == 97) {
                headIcon = buffer.readUnsignedShort();
            } else if (opcode == 98) {
                combatLevel = buffer.readUnsignedShort();
            } else {
                // System.out.println("Unknown NPC Opcode: " + opcode);
            }
        }
    }
}