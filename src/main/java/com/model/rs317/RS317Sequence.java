package com.model.rs317;

import java.util.HashMap;
import java.util.Map;

public class RS317Sequence {

    public static Map<Integer, RS317Sequence> sequences = new HashMap<>();

    public int[] frameIDs;
    public int[] frameDurations;
    public int[] frameSound;
    public int frameCount;
    public int padding = -1;
    public int forcedPriority = 5;
    public int leftHandItem = -1;
    public int rightHandItem = -1;
    public int maxLoops = 99;
    public int precedenceAnimating = -1;
    public int priority = -1;
    public int replyMode = 2;

    public static void load(RS317Archive archive) {
        try {
            byte[] data = archive.getFile("seq.dat");
            if (data == null) {
                System.err.println("seq.dat not found in archive!");
                return;
            }
            RS317Stream stream = new RS317Stream(data);
            int count = stream.readUnsignedWord();
            for (int i = 0; i < count; i++) {
                RS317Sequence seq = new RS317Sequence();
                seq.decode(stream);
                sequences.put(i, seq);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void decode(RS317Stream stream) {
        while (true) {
            int opcode = stream.readUnsignedByte();
            if (opcode == 0) break;

            if (opcode == 1) {
                frameCount = stream.readUnsignedByte();
                frameIDs = new int[frameCount];
                frameDurations = new int[frameCount];
                for (int i = 0; i < frameCount; i++) {
                    frameIDs[i] = stream.readUnsignedWord();
                    frameDurations[i] = stream.readUnsignedWord();
                    if (frameDurations[i] == 0) frameDurations[i] = 1; // Prevent inf loop
                }
            } else if (opcode == 2) padding = stream.readUnsignedWord();
            else if (opcode == 3) {
                int count = stream.readUnsignedByte();
                // Flow control points, often unused in 317 viewer
                for (int i = 0; i < count; i++) stream.readUnsignedByte();
            } else if (opcode == 4) padding = 1; // boolean
            else if (opcode == 5) priority = stream.readUnsignedByte();
            else if (opcode == 6) leftHandItem = stream.readUnsignedWord();
            else if (opcode == 7) rightHandItem = stream.readUnsignedWord();
            else if (opcode == 8) maxLoops = stream.readUnsignedByte();
            else if (opcode == 9) precedenceAnimating = stream.readUnsignedByte();
            else if (opcode == 10) replyMode = stream.readUnsignedByte(); // Walk priority
            else if (opcode == 11) replyMode = stream.readUnsignedByte();
            else if (opcode == 12) stream.readDWord(); // Unused
            else if (opcode == 13) {
                // Sound data
                int count = stream.readUnsignedByte();
                for (int i = 0; i < count; i++) stream.read3Bytes(); // Skip
            }
        }
    }
    
    public int getFrame(int index) {
        if (frameIDs == null || index < 0 || index >= frameIDs.length) return -1;
        return frameIDs[index];
    }
}