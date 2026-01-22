package com.model.rs459;

public class RS459Model {
    public int[] verticesX;
    public int[] verticesY;
    public int[] verticesZ;
    public int[] indices1;
    public int[] indices2;
    public int[] indices3;
    public int[] faceColors;
    public int vertexCount;
    public int faceCount;

    public RS459Model(byte[] data) {
        if (data != null) {
            decode(data);
        }
    }

    private void decode(byte[] data) {
        // [Safety 1] Basic Size Check
        if (data.length < 18) {
            System.err.println("Model data too short: " + data.length);
            return;
        }

        Buffer bFooter = new Buffer(data);
        bFooter.offset = data.length - 18;
        
        vertexCount = bFooter.readUnsignedShort();
        faceCount = bFooter.readUnsignedShort();
        int textureCount = bFooter.readUnsignedByte();
        
        int useTextures = bFooter.readUnsignedByte();
        int usePriorities = bFooter.readUnsignedByte();
        int useAlpha = bFooter.readUnsignedByte();
        int useSkins = bFooter.readUnsignedByte();
        int useLabels = bFooter.readUnsignedByte();
        
        int dataXLen = bFooter.readUnsignedShort();
        int dataYLen = bFooter.readUnsignedShort();
        int dataZLen = bFooter.readUnsignedShort();
        int faceDataLen = bFooter.readUnsignedShort();

        // [Safety 2] Header Integrity Check
        // If BZip2 fails and returns garbage, these values will be huge.
        int totalRequired = dataXLen + dataYLen + dataZLen + faceDataLen;
        if (totalRequired > data.length) {
             System.err.println("\n[Model Error] Corrupt Header / Bad Decompression");
             System.err.println("File Size: " + data.length);
             System.err.println("Header Claims: " + totalRequired + " bytes needed.");
             System.err.println("Vertices: " + vertexCount + ", Faces: " + faceCount);
             return; // Stop before crashing
        }

        verticesX = new int[vertexCount];
        verticesY = new int[vertexCount];
        verticesZ = new int[vertexCount];
        indices1 = new int[faceCount];
        indices2 = new int[faceCount];
        indices3 = new int[faceCount];
        faceColors = new int[faceCount];

        // 1. Calculate Offsets
        int offset = 0;
        int flagOffset = offset; offset += vertexCount;
        int triFlagOffset = offset; offset += faceCount;
        if(usePriorities == 255) offset += faceCount;
        if(useSkins == 1) offset += faceCount;
        if(useTextures == 1) offset += faceCount;
        if(useLabels == 1) offset += vertexCount;
        if(useAlpha == 1) offset += faceCount;
        int faceDataOffset = offset; offset += faceDataLen; 
        int faceIndOffset = offset; offset += faceCount * 2; 
        int texMapOffset = offset; offset += textureCount * 6;
        int xOffset = offset; offset += dataXLen;
        int yOffset = offset; offset += dataYLen;
        int zOffset = offset; offset += dataZLen;

        // [Safety 3] Offset Bounds Check
        if (offset > data.length) {
             System.err.println("[Model Error] Calculated offsets exceed file size.");
             return;
        }

        try {
            // 2. Read Vertices
            Buffer b1 = new Buffer(data); b1.offset = xOffset;
            Buffer b2 = new Buffer(data); b2.offset = yOffset;
            Buffer b3 = new Buffer(data); b3.offset = zOffset;
            Buffer b4 = new Buffer(data); b4.offset = flagOffset;

            int vx = 0, vy = 0, vz = 0;
            for (int i = 0; i < vertexCount; i++) {
                int flag = b4.readUnsignedByte();
                int dx = 0, dy = 0, dz = 0;
                
                if ((flag & 1) != 0) dx = b1.readSignedSmart();
                if ((flag & 2) != 0) dy = b2.readSignedSmart();
                if ((flag & 4) != 0) dz = b3.readSignedSmart();
                
                vx += dx; vy += dy; vz += dz;
                verticesX[i] = vx; verticesY[i] = vy; verticesZ[i] = vz;
            }

            // 3. Read Faces
            Buffer bIndices = new Buffer(data); bIndices.offset = faceDataOffset;
            Buffer bColors = new Buffer(data); bColors.offset = faceIndOffset;
            Buffer bFlags = new Buffer(data); bFlags.offset = triFlagOffset;

            int i1 = 0, i2 = 0, i3 = 0;
            int lastIndex = 0;

            for (int i = 0; i < faceCount; i++) {
                faceColors[i] = bColors.readUnsignedShort();
                int type = bFlags.readUnsignedByte();
                
                if (type == 1) {
                    i1 = bIndices.readSignedSmart() + lastIndex; lastIndex = i1;
                    i2 = bIndices.readSignedSmart() + lastIndex; lastIndex = i2;
                    i3 = bIndices.readSignedSmart() + lastIndex; lastIndex = i3;
                    indices1[i] = i1; indices2[i] = i2; indices3[i] = i3;
                } else if (type == 2) {
                    i2 = i3;
                    i3 = bIndices.readSignedSmart() + lastIndex; lastIndex = i3;
                    indices1[i] = i1; indices2[i] = i2; indices3[i] = i3;
                } else if (type == 3) {
                    i1 = i3;
                    i3 = bIndices.readSignedSmart() + lastIndex; lastIndex = i3;
                    indices1[i] = i1; indices2[i] = i2; indices3[i] = i3;
                } else if (type == 4) {
                    int temp = i1; i1 = i2; i2 = temp;
                    i3 = bIndices.readSignedSmart() + lastIndex; lastIndex = i3;
                    indices1[i] = i1; indices2[i] = i2; indices3[i] = i3;
                }
            }
        } catch (Exception e) {
            System.err.println("Error decoding model: " + e.getMessage());
            vertexCount = 0; 
            faceCount = 0;
        }
    }
}