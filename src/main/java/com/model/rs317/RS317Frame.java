package com.model.rs317;

public class RS317Frame {

    public static RS317Frame[] frameList;
    
    public int duration;
    public RS317Skin skin; // "Class18"
    public int transformCount;
    public int[] transformTypes;
    public int[] translator_x;
    public int[] translator_y;
    public int[] translator_z;

    public static void load(byte[] data) {
        try {
            RS317Stream stream = new RS317Stream(data);
            stream.offset = data.length - 8;
            
            int attrOffset = stream.readUnsignedWord();
            int skinOffset = stream.readUnsignedWord();
            int count = stream.readUnsignedWord();
            int e = stream.readUnsignedWord(); // Total length?

            RS317Stream skinStream = new RS317Stream(data);
            skinStream.offset = skinOffset;
            
            RS317Stream attrStream = new RS317Stream(data);
            attrStream.offset = attrOffset;
            
            RS317Stream mainStream = new RS317Stream(data);
            mainStream.offset = 0;

            frameList = new RS317Frame[count + 1]; // +1 safety
            
            // Temporary skin storage
            int[] skinIds = new int[500]; // Arbitrary max
            RS317Skin[] skins = new RS317Skin[500];

            for (int i = 0; i < count; i++) {
                int id = attrStream.readUnsignedWord(); // Frame ID
                RS317Frame frame = new RS317Frame();
                frameList[id] = frame;

                int skinId = attrStream.readUnsignedByte();
                RS317Skin currentSkin = skins[skinId];
                
                // If skin not loaded yet, read it
                if (currentSkin == null) {
                    skinStream.offset = skinOffset + (skinId * 0); // Logic varies, usually sequential
                    // Actually standard 317 reads skins sequentially from the skinStream
                    // We need to just read the next one from skinStream? 
                    // No, the format is [id][skin_id][...].
                    
                    // Let's implement the standard loop logic:
                    // The skinStream is a continuous list of Base Definitions (Class18)
                    // But we don't know WHERE to jump. 
                    // Standard 317: skinStream is just sequential Class18s.
                    // We rely on the fact that skinId increments or is reused.
                    // Simpler approach:
                    
                    // Re-instantiate skin stream for safety? No.
                    // Correct 317 logic:
                    // The skin definitions are just packed at the end.
                    // Let's assume we parse the skin ONCE when we encounter a new ID.
                    currentSkin = new RS317Skin(skinStream);
                    skins[skinId] = currentSkin;
                }
                frame.skin = currentSkin;

                int transforms = attrStream.readUnsignedByte();
                frame.transformCount = transforms;
                frame.transformTypes = new int[transforms];
                frame.translator_x = new int[transforms];
                frame.translator_y = new int[transforms];
                frame.translator_z = new int[transforms];

                for (int t = 0; t < transforms; t++) {
                    int type = attrStream.readUnsignedByte();
                    if (type > 0) {
                        if (frame.skin.opcodes[type - 1] != 0) {
                            for (int k = t - 1; k >= 0; k--) {
                                if (frame.transformTypes[k] == type) {
                                    // Overwrite previous transform of same type?
                                    break;
                                }
                            }
                        }
                        frame.transformTypes[t] = type;
                        
                        // Read delta
                        int base = 0;
                        if (frame.skin.opcodes[type - 1] == 3) base = 128;
                        
                        if ((type & 1) != 0) frame.translator_x[t] = mainStream.readSignedSmart();
                        else frame.translator_x[t] = base;
                        
                        if ((type & 2) != 0) frame.translator_y[t] = mainStream.readSignedSmart();
                        else frame.translator_y[t] = base;
                        
                        if ((type & 4) != 0) frame.translator_z[t] = mainStream.readSignedSmart();
                        else frame.translator_z[t] = base;
                    } else {
                        frame.transformTypes[t] = 0;
                        frame.translator_x[t] = 0; 
                        frame.translator_y[t] = 0; 
                        frame.translator_z[t] = 0;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // Inner class for the "Base" (Class18)
    public static class RS317Skin {
        public int count;
        public int[] opcodes;
        public int[][] skinList;

        public RS317Skin(RS317Stream stream) {
            count = stream.readUnsignedByte();
            opcodes = new int[count];
            skinList = new int[count][];
            for (int i = 0; i < count; i++) {
                opcodes[i] = stream.readUnsignedByte();
            }
            for (int i = 0; i < count; i++) {
                int len = stream.readUnsignedByte();
                skinList[i] = new int[len];
                for (int j = 0; j < len; j++) {
                    skinList[i][j] = stream.readUnsignedByte();
                }
            }
        }
    }
}