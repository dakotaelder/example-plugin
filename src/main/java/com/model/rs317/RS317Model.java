package com.model.rs317;

public class RS317Model {

    public int vertexCount;
    public int[] verticesX;
    public int[] verticesY;
    public int[] verticesZ;
    public int faceCount;
    public int[] faceIndices1;
    public int[] faceIndices2;
    public int[] faceIndices3;
    public int[] faceColors;
    public int[] faceRenderType;
    public int[] facePriorities;
    public int[] faceAlpha;
    public int[] faceBones;
    public int[] vertexBones;
    public int priority;
    public int minY = 0;
    public int maxY = 0;
    
    // [NEW] Backups for Animation Reset
    private int[] origVerticesX;
    private int[] origVerticesY;
    private int[] origVerticesZ;
    
    private static int[] SINE = new int[2048];
    private static int[] COSINE = new int[2048];
    
    static {
        for (int i = 0; i < 2048; i++) {
            SINE[i] = (int) (Math.sin(i * 0.0030679615) * 65536.0);
            COSINE[i] = (int) (Math.cos(i * 0.0030679615) * 65536.0);
        }
    }

    public RS317Model(byte[] data) {
        boolean success = false;
        try {
            decodeOld(data);
            success = validate();
        } catch (Exception e) { success = false; }

        if (!success) {
            try { decodeNew(data); } catch (Exception e) { vertexCount = 0; faceCount = 0; }
        }
        calculateBounds();
        saveInitialState(); // [IMPORTANT] Save T-Pose
    }

    public RS317Model(RS317Model[] models) {
        // ... (Keep existing merge logic from previous snippet) ...
        boolean hasType = false, hasPriorities = false, hasAlpha = false;
        boolean hasFaceBones = false, hasVertexBones = false;
        vertexCount = 0; faceCount = 0; priority = -1;

        for (RS317Model m : models) {
            if (m != null) {
                vertexCount += m.vertexCount;
                faceCount += m.faceCount;
                if (m.faceRenderType != null) hasType = true;
                if (m.facePriorities != null) hasPriorities = true;
                if (m.faceAlpha != null) hasAlpha = true;
                if (m.faceBones != null) hasFaceBones = true;
                if (m.vertexBones != null) hasVertexBones = true;
                if (m.priority != -1) priority = m.priority;
            }
        }

        verticesX = new int[vertexCount]; verticesY = new int[vertexCount]; verticesZ = new int[vertexCount];
        faceIndices1 = new int[faceCount]; faceIndices2 = new int[faceCount]; faceIndices3 = new int[faceCount];
        faceColors = new int[faceCount];

        if (hasType) faceRenderType = new int[faceCount];
        if (hasPriorities) facePriorities = new int[faceCount];
        if (hasAlpha) faceAlpha = new int[faceCount];
        if (hasFaceBones) faceBones = new int[faceCount];
        if (hasVertexBones) vertexBones = new int[vertexCount];

        int vPtr = 0, fPtr = 0;
        for (RS317Model m : models) {
            if (m != null) {
                for (int f = 0; f < m.faceCount; f++) {
                    faceIndices1[fPtr] = m.faceIndices1[f] + vPtr;
                    faceIndices2[fPtr] = m.faceIndices2[f] + vPtr;
                    faceIndices3[fPtr] = m.faceIndices3[f] + vPtr;
                    faceColors[fPtr] = m.faceColors[f];
                    if (hasType && m.faceRenderType != null) faceRenderType[fPtr] = m.faceRenderType[f];
                    if (hasPriorities && m.facePriorities != null) facePriorities[fPtr] = m.facePriorities[f];
                    if (hasAlpha && m.faceAlpha != null) faceAlpha[fPtr] = m.faceAlpha[f];
                    if (hasFaceBones && m.faceBones != null) faceBones[fPtr] = m.faceBones[f];
                    fPtr++;
                }
                for (int v = 0; v < m.vertexCount; v++) {
                    verticesX[vPtr] = m.verticesX[v];
                    verticesY[vPtr] = m.verticesY[v];
                    verticesZ[vPtr] = m.verticesZ[v];
                    if (hasVertexBones && m.vertexBones != null) vertexBones[vPtr] = m.vertexBones[v];
                    vPtr++;
                }
            }
        }
        calculateBounds();
        saveInitialState();
    }
    
    private void saveInitialState() {
        origVerticesX = new int[vertexCount];
        origVerticesY = new int[vertexCount];
        origVerticesZ = new int[vertexCount];
        System.arraycopy(verticesX, 0, origVerticesX, 0, vertexCount);
        System.arraycopy(verticesY, 0, origVerticesY, 0, vertexCount);
        System.arraycopy(verticesZ, 0, origVerticesZ, 0, vertexCount);
    }
    
    public void reset() {
        if (origVerticesX == null) return;
        System.arraycopy(origVerticesX, 0, verticesX, 0, vertexCount);
        System.arraycopy(origVerticesY, 0, verticesY, 0, vertexCount);
        System.arraycopy(origVerticesZ, 0, verticesZ, 0, vertexCount);
    }
    
    public void animate(int frameId) {
        if (vertexBones == null || frameId == -1 || RS317Frame.frameList == null) return;
        if (frameId >= RS317Frame.frameList.length) return;

        RS317Frame frame = RS317Frame.frameList[frameId];
        if (frame == null || frame.skin == null) return;

        // [FIX] Reset before applying next frame to avoid accumulation
        reset(); 

        for (int i = 0; i < frame.transformCount; i++) {
            int type = frame.transformTypes[i];
            int dx = frame.translator_x[i];
            int dy = frame.translator_y[i];
            int dz = frame.translator_z[i];
            
            int[] labels = frame.skin.skinList[type];
            
            // [FIX] Iterate all vertices and check BONE ID
            for (int v = 0; v < vertexCount; v++) {
                if (vertexBones[v] != -1) {
                    for (int label : labels) {
                        if (vertexBones[v] == label) {
                            applyTransform(v, type, dx, dy, dz, frame.skin.opcodes[type]);
                            break;
                        }
                    }
                }
            }
        }
        calculateBounds();
    }

    private void applyTransform(int v, int type, int x, int y, int z, int opcode) {
        if (opcode == 0) { // Rotation
            int sinX = SINE[x], cosX = COSINE[x];
            int sinY = SINE[y], cosY = COSINE[y];
            int sinZ = SINE[z], cosZ = COSINE[z];
            int vx = verticesX[v], vy = verticesY[v], vz = verticesZ[v];
            
            // Standard 317 Rotation Order: Z -> X -> Y
            if (z != 0) {
                int t = (vy * cosZ - vx * sinZ) >> 16;
                vx = (vy * sinZ + vx * cosZ) >> 16;
                vy = t;
            }
            if (x != 0) {
                int t = (vy * cosX - vz * sinX) >> 16;
                vz = (vy * sinX + vz * cosX) >> 16;
                vy = t;
            }
            if (y != 0) {
                int t = (vz * cosY - vx * sinY) >> 16;
                vx = (vz * sinY + vx * cosY) >> 16;
                vz = t;
            }
            verticesX[v] = vx; verticesY[v] = vy; verticesZ[v] = vz;
        } 
        else if (opcode == 1) { // Translation
            verticesX[v] += x;
            verticesY[v] += y;
            verticesZ[v] += z;
        }
        else if (opcode == 2) { // Scale
            verticesX[v] = (verticesX[v] * x + 64) >> 7;
            verticesY[v] = (verticesY[v] * y + 64) >> 7;
            verticesZ[v] = (verticesZ[v] * z + 64) >> 7;
        }
    }

    private boolean validate() {
        if (vertexCount > 65000 || faceCount > 65000) return false;
        for (int i = 0; i < faceCount; i++) {
            if (faceIndices1[i] < 0 || faceIndices1[i] >= vertexCount) return false;
        }
        return true;
    }

    private void calculateBounds() {
        if (vertexCount == 0) return;
        minY = verticesY[0]; maxY = verticesY[0];
        for (int i = 1; i < vertexCount; i++) {
            if (verticesY[i] < minY) minY = verticesY[i];
            if (verticesY[i] > maxY) maxY = verticesY[i];
        }
    }

    private void decodeOld(byte[] data) {
        if (data.length < 18) return;
        RS317Stream footer = new RS317Stream(data);
        footer.offset = data.length - 18;
        vertexCount = footer.readUnsignedWord();
        faceCount = footer.readUnsignedWord();
        int texCount = footer.readUnsignedByte();

        int useTextures = footer.readUnsignedByte();
        int usePriorities = footer.readUnsignedByte();
        int useAlpha = footer.readUnsignedByte();
        int useFaceBones = footer.readUnsignedByte();
        int useVertexBones = footer.readUnsignedByte();

        int xLen = footer.readUnsignedWord();
        int yLen = footer.readUnsignedWord();
        int zLen = footer.readUnsignedWord();
        int fLen = footer.readUnsignedWord();

        int offset = 0;
        int vertexFlagOffset = offset; offset += vertexCount;
        int faceTypeOffset = offset; offset += faceCount;
        int priorityOffset = offset; if (usePriorities == 255) offset += faceCount;
        int faceBoneOffset = offset; if (useFaceBones == 1) offset += faceCount;
        int faceInfoOffset = offset; if (useVertexBones == 1) offset += vertexCount;
        int alphaOffset = offset; if (useAlpha == 1) offset += faceCount;
        int faceDataOffset = offset; offset += fLen;
        int faceColorOffset = offset; offset += faceCount * 2;
        int vertexXOffset = offset; offset += xLen;
        int vertexYOffset = offset; offset += yLen;
        int vertexZOffset = offset; offset += zLen;

        verticesX = new int[vertexCount]; verticesY = new int[vertexCount]; verticesZ = new int[vertexCount];
        if (useVertexBones == 1) vertexBones = new int[vertexCount];
        faceIndices1 = new int[faceCount]; faceIndices2 = new int[faceCount]; faceIndices3 = new int[faceCount];
        faceColors = new int[faceCount];
        if (usePriorities == 255) facePriorities = new int[faceCount];
        if (useAlpha == 1) faceAlpha = new int[faceCount];
        if (useFaceBones == 1) faceBones = new int[faceCount];
        faceRenderType = new int[faceCount];

        RS317Stream vFlags = new RS317Stream(data); vFlags.offset = vertexFlagOffset;
        RS317Stream vX = new RS317Stream(data); vX.offset = vertexXOffset;
        RS317Stream vY = new RS317Stream(data); vY.offset = vertexYOffset;
        RS317Stream vZ = new RS317Stream(data); vZ.offset = vertexZOffset;
        RS317Stream vBone = new RS317Stream(data); vBone.offset = faceInfoOffset;

        int baseX = 0, baseY = 0, baseZ = 0;
        for (int v = 0; v < vertexCount; v++) {
            int flag = vFlags.readUnsignedByte();
            int dx = 0, dy = 0, dz = 0;
            if ((flag & 1) != 0) dx = vX.readSignedSmart();
            if ((flag & 2) != 0) dy = vY.readSignedSmart();
            if ((flag & 4) != 0) dz = vZ.readSignedSmart();
            verticesX[v] = baseX += dx; verticesY[v] = baseY += dy; verticesZ[v] = baseZ += dz;
            if (useVertexBones == 1) vertexBones[v] = vBone.readUnsignedByte();
        }

        RS317Stream fColors = new RS317Stream(data); fColors.offset = faceColorOffset;
        RS317Stream fTypes = new RS317Stream(data); fTypes.offset = faceTypeOffset;
        RS317Stream fPrio = new RS317Stream(data); fPrio.offset = priorityOffset;
        RS317Stream fAlpha = new RS317Stream(data); fAlpha.offset = alphaOffset;
        RS317Stream fBones = new RS317Stream(data); fBones.offset = faceBoneOffset;
        
        for (int f = 0; f < faceCount; f++) {
            faceColors[f] = fColors.readUnsignedWord();
            int type = fTypes.readUnsignedByte();
            if (usePriorities == 255) facePriorities[f] = fPrio.readUnsignedByte();
            if (useAlpha == 1) faceAlpha[f] = fAlpha.readUnsignedByte();
            if (useFaceBones == 1) faceBones[f] = fBones.readUnsignedByte();
            faceRenderType[f] = type;
        }

        RS317Stream fData = new RS317Stream(data); fData.offset = faceDataOffset;
        int i1 = 0, i2 = 0, i3 = 0, last = 0;
        
        for (int f = 0; f < faceCount; f++) {
            int type = faceRenderType[f];
            if (type == 1) {
                i1 = fData.readSignedSmart() + last; last = i1;
                i2 = fData.readSignedSmart() + last; last = i2;
                i3 = fData.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 2) {
                i2 = i3; i3 = fData.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 3) {
                i1 = i3; i3 = fData.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 4) {
                int temp = i1; i1 = i2; i2 = temp;
                i3 = fData.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else {
                i1 = fData.readSignedSmart() + last; last = i1;
                i2 = fData.readSignedSmart() + last; last = i2;
                i3 = fData.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            }
        }
    }

    private void decodeNew(byte[] data) {
        RS317Stream footer = new RS317Stream(data);
        footer.offset = data.length - 26;
        vertexCount = footer.readUnsignedWord();
        faceCount = footer.readUnsignedWord();
        
        int texCount = footer.readUnsignedByte();
        int flags = footer.readUnsignedByte();
        int priority = footer.readUnsignedByte();
        int transparency = footer.readUnsignedByte();
        int priorityDepth = footer.readUnsignedByte();
        int skin = footer.readUnsignedByte();
        int dataX = footer.readUnsignedWord();
        int dataY = footer.readUnsignedWord();
        int dataZ = footer.readUnsignedWord();
        int faceData = footer.readUnsignedWord();
        int texData = footer.readUnsignedWord();

        verticesX = new int[vertexCount]; verticesY = new int[vertexCount]; verticesZ = new int[vertexCount];
        faceIndices1 = new int[faceCount]; faceIndices2 = new int[faceCount]; faceIndices3 = new int[faceCount];
        faceColors = new int[faceCount]; faceRenderType = new int[faceCount];
        
        if (priority == 255) facePriorities = new int[faceCount];
        if (transparency == 1) faceAlpha = new int[faceCount];
        if (skin == 1) { faceBones = new int[faceCount]; vertexBones = new int[vertexCount]; }

        int offset = 0;
        int vertexFlagOffset = offset; offset += vertexCount;
        int faceTypeOffset = offset; offset += faceCount;
        int facePrioOffset = offset; if (priority == 255) offset += faceCount;
        int faceSkinOffset = offset; if (skin == 1) offset += faceCount;
        int faceAlphaOffset = offset; if (transparency == 1) offset += faceCount;
        int vertexSkinOffset = offset; if (skin == 1) offset += vertexCount;
        int faceDataOffset = offset; offset += faceData;
        int faceColorOffset = offset; offset += faceCount * 2;
        int texInfoOffset = offset; offset += texCount * 6;
        int xOffset = offset; offset += dataX;
        int yOffset = offset; offset += dataY;
        int zOffset = offset; offset += dataZ;

        RS317Stream vFlags = new RS317Stream(data); vFlags.offset = vertexFlagOffset;
        RS317Stream vX = new RS317Stream(data); vX.offset = xOffset;
        RS317Stream vY = new RS317Stream(data); vY.offset = yOffset;
        RS317Stream vZ = new RS317Stream(data); vZ.offset = zOffset;
        
        // [FIX] Separate Stream for Skin Indices
        RS317Stream vSkin = new RS317Stream(data); vSkin.offset = vertexSkinOffset;

        int baseX = 0, baseY = 0, baseZ = 0;
        for (int v = 0; v < vertexCount; v++) {
            int flag = vFlags.readUnsignedByte();
            int dx = 0, dy = 0, dz = 0;
            if ((flag & 1) != 0) dx = vX.readSignedSmart();
            if ((flag & 2) != 0) dy = vY.readSignedSmart();
            if ((flag & 4) != 0) dz = vZ.readSignedSmart();
            verticesX[v] = baseX += dx; verticesY[v] = baseY += dy; verticesZ[v] = baseZ += dz;
            if (skin == 1) vertexBones[v] = vSkin.readUnsignedByte(); // Read from correct stream
        }

        RS317Stream fColors = new RS317Stream(data); fColors.offset = faceColorOffset;
        RS317Stream fTypes = new RS317Stream(data); fTypes.offset = faceTypeOffset;
        for (int f = 0; f < faceCount; f++) {
            faceColors[f] = fColors.readUnsignedWord();
            faceRenderType[f] = fTypes.readUnsignedByte();
        }

        RS317Stream fDataStream = new RS317Stream(data); fDataStream.offset = faceDataOffset;
        int i1 = 0, i2 = 0, i3 = 0, last = 0;
        for (int f = 0; f < faceCount; f++) {
            int type = faceRenderType[f];
            if (type == 1) {
                i1 = fDataStream.readSignedSmart() + last; last = i1;
                i2 = fDataStream.readSignedSmart() + last; last = i2;
                i3 = fDataStream.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 2) {
                i2 = i3; i3 = fDataStream.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 3) {
                i1 = i3; i3 = fDataStream.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            } else if (type == 4) {
                int temp = i1; i1 = i2; i2 = temp;
                i3 = fDataStream.readSignedSmart() + last; last = i3;
                faceIndices1[f] = i1; faceIndices2[f] = i2; faceIndices3[f] = i3;
            }
        }
    }
    public void applyLighting(int lightAmbient, int lightContrast, int lightX, int lightY, int lightZ) {
        for (int f = 0; f < faceCount; f++) {
            int i1 = faceIndices1[f];
            int i2 = faceIndices2[f];
            int i3 = faceIndices3[f];
            if (i1 >= vertexCount || i2 >= vertexCount || i3 >= vertexCount) continue;

            int x1 = verticesX[i1]; int y1 = verticesY[i1]; int z1 = verticesZ[i1];
            int x2 = verticesX[i2]; int y2 = verticesY[i2]; int z2 = verticesZ[i2];
            int x3 = verticesX[i3]; int y3 = verticesY[i3]; int z3 = verticesZ[i3];

            int dx1 = x2 - x1; int dy1 = y2 - y1; int dz1 = z2 - z1;
            int dx2 = x3 - x1; int dy2 = y3 - y1; int dz2 = z3 - z1;

            int nx = dy1 * dz2 - dy2 * dz1;
            int ny = dz1 * dx2 - dz2 * dx1;
            int nz = dx1 * dy2 - dx2 * dy1;

            int len = (int) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len == 0) len = 1;
            nx /= len; ny /= len; nz /= len;

            int dot = (nx * lightX + ny * lightY + nz * lightZ) / 256;
            int brightness = lightAmbient + (dot * lightContrast) / 256;

            if (brightness < 0) brightness = 0;
            else if (brightness > 127) brightness = 127;

            int hsl = faceColors[f];
            int h = hsl >> 10 & 0x3f;
            int s = hsl >> 7 & 0x7;
            int l = hsl & 0x7f;

            l = (l * brightness) / 128;
            if (l < 2) l = 2;
            if (l > 126) l = 126;

            faceColors[f] = (h << 10) | (s << 7) | l;
        }
    }
    public void recolor(int oldColor, int newColor) {
        for (int i = 0; i < faceCount; i++) {
            if (faceColors[i] == oldColor) faceColors[i] = newColor;
        }
    }

}