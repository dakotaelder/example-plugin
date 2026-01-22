package com.model.rs459;

/**
 * 1:1 Implementation based on client317/Class13 and Class32.
 */
public class BZip2 {

    private static final class State {
        byte[] input;
        int nextIn;
        byte[] output;
        int nextOut;
        int outputLen;
        int bsLive;
        int bsBuff;
        int[] unzftab = new int[256];
        int[] cftab = new int[257];
        int[][] perm = new int[6][258];
        int[][] base = new int[6][258];
        int[][] limit = new int[6][258];
        byte[][] len = new byte[6][258];
        int[] minLens = new int[6];
        byte[] selector = new byte[18002];
        byte[] selectorMtf = new byte[18002];
        boolean[] inUse = new boolean[256];
        boolean[] inUse16 = new boolean[16];
        byte[] seqToUnseq = new byte[256];
        byte[] mtfa = new byte[4096];
        int[] mtfbase = new int[16];
        int[] tt; 
    }

    public static void decompress(byte[] output, int outputLen, byte[] input, int compressedLen, int start) {
        State s = new State();
        s.input = input;
        s.nextIn = start;
        s.output = output;
        s.nextOut = 0;
        s.outputLen = outputLen;
        s.bsLive = 0;
        s.bsBuff = 0;
        
        // Strict 1:1 match with Class32.anIntArray587 initialization
        // The client uses blockSize100k * 100,000. For safety with large NPC files, we use max block size (9).
        s.tt = new int[900000]; 
        
        decompress(s);
    }

    private static void decompress(State s) {
        int gMinLen = 0;
        int[] gLimit = null;
        int[] gBase = null;
        int[] gPerm = null;

        while (true) {
            byte type = getByte(s);
            if (type == 23) return; 
            
            // PI Header skip (exactly 10 bytes including type)
            for (int i = 0; i < 9; i++) getByte(s);
            getBit(s); 
            
            int origPtr = 0;
            origPtr = (origPtr << 8) | (getByte(s) & 0xFF);
            origPtr = (origPtr << 8) | (getByte(s) & 0xFF);
            origPtr = (origPtr << 8) | (getByte(s) & 0xFF);

            for (int i = 0; i < 16; i++) s.inUse16[i] = (getBit(s) == 1);
            for (int i = 0; i < 256; i++) s.inUse[i] = false;
            for (int i = 0; i < 16; i++) {
                if (s.inUse16[i]) {
                    for (int j = 0; j < 16; j++) {
                        if (getBit(s) == 1) s.inUse[i * 16 + j] = true;
                    }
                }
            }
            
            int inUseCount = 0;
            for (int i = 0; i < 256; i++) {
                if (s.inUse[i]) s.seqToUnseq[inUseCount++] = (byte) i;
            }
            
            int alphaSize = inUseCount + 2;
            int nGroups = getBits(3, s);
            int nSelectors = getBits(15, s);
            
            for (int i = 0; i < nSelectors; i++) {
                int j = 0;
                while (getBit(s) == 1) j++;
                s.selectorMtf[i] = (byte) j;
            }
            
            byte[] pos = new byte[6];
            for (byte v = 0; v < nGroups; v++) pos[v] = v;
            for (int i = 0; i < nSelectors; i++) {
                int v = s.selectorMtf[i] & 0xff;
                byte tmp = pos[v];
                for (; v > 0; v--) pos[v] = pos[v - 1];
                pos[0] = tmp;
                s.selector[i] = tmp;
            }

            for (int t = 0; t < nGroups; t++) {
                int curr = getBits(5, s);
                for (int i = 0; i < alphaSize; i++) {
                    while (getBit(s) == 1) {
                        if (getBit(s) == 0) curr++; else curr--;
                    }
                    s.len[t][i] = (byte) curr;
                }
            }

            for (int t = 0; t < nGroups; t++) {
                byte minLen = 32;
                byte maxLen = 0;
                for (int i = 0; i < alphaSize; i++) {
                    if (s.len[t][i] > maxLen) maxLen = s.len[t][i];
                    if (s.len[t][i] < minLen) minLen = s.len[t][i];
                }
                createDecodeTables(s.limit[t], s.base[t], s.perm[t], s.len[t], minLen, maxLen, alphaSize);
                s.minLens[t] = minLen;
            }

            int EOB = inUseCount + 1;
            int groupPos = 0;
            int groupNo = -1;
            for (int i = 0; i <= 255; i++) s.unzftab[i] = 0;

            int kk = 4095;
            for (int i = 15; i >= 0; i--) {
                for (int j = 15; j >= 0; j--) {
                    s.mtfa[kk] = (byte) (i * 16 + j);
                    kk--;
                }
                s.mtfbase[i] = kk + 1;
            }

            int nblock = 0; 
            
            // Standard Jagex Block Initialization
            if (groupPos == 0) {
                groupNo++;
                groupPos = 50;
                byte gSel = s.selector[groupNo];
                gMinLen = s.minLens[gSel];
                gLimit = s.limit[gSel];
                gPerm = s.perm[gSel];
                gBase = s.base[gSel];
            }
            groupPos--;
            int zn = gMinLen;
            int zvec = getBits(zn, s);
            while (zvec > gLimit[zn]) {
                zn++;
                zvec = (zvec << 1) | getBit(s);
            }
            int nextSym = gPerm[zvec - gBase[zn]];

            while (nextSym != EOB) {
                if (nextSym == 0 || nextSym == 1) {
                    int es = -1;
                    int N = 1;
                    do {
                        if (nextSym == 0) es += N;
                        else if (nextSym == 1) es += 2 * N;
                        N *= 2;
                        if (groupPos == 0) {
                            groupNo++;
                            groupPos = 50;
                            byte gSel = s.selector[groupNo];
                            gMinLen = s.minLens[gSel];
                            gLimit = s.limit[gSel];
                            gPerm = s.perm[gSel];
                            gBase = s.base[gSel];
                        }
                        groupPos--;
                        zn = gMinLen;
                        zvec = getBits(zn, s);
                        while (zvec > gLimit[zn]) {
                            zn++;
                            zvec = (zvec << 1) | getBit(s);
                        }
                        nextSym = gPerm[zvec - gBase[zn]];
                    } while (nextSym == 0 || nextSym == 1);
                    
                    es++;
                    byte decoded = s.seqToUnseq[s.mtfa[s.mtfbase[0]] & 0xff];
                    s.unzftab[decoded & 0xff] += es;
                    while (es > 0) {
                        s.tt[nblock++] = decoded & 0xff;
                        es--;
                    }
                } else {
                    int nn = nextSym - 1;
                    byte decoded;
                    if (nn < 16) {
                        int pp = s.mtfbase[0];
                        decoded = s.mtfa[pp + nn];
                        for (; nn > 0; nn--) s.mtfa[pp + nn] = s.mtfa[pp + nn - 1];
                        s.mtfa[pp] = decoded;
                    } else {
                        int lno = nn / 16;
                        int off = nn % 16;
                        int pp = s.mtfbase[lno] + off;
                        decoded = s.mtfa[pp];
                        for (; pp > s.mtfbase[lno]; pp--) s.mtfa[pp] = s.mtfa[pp - 1];
                        s.mtfbase[lno]++;
                        for (; lno > 0; lno--) {
                            s.mtfbase[lno]--;
                            s.mtfa[s.mtfbase[lno]] = s.mtfa[s.mtfbase[lno - 1] + 16 - 1];
                        }
                        s.mtfbase[0]--;
                        s.mtfa[s.mtfbase[0]] = decoded;
                        if (s.mtfbase[0] == 0) {
                            int k_local = 4095;
                            for (int i_local = 15; i_local >= 0; i_local--) {
                                for (int j_local = 15; j_local >= 0; j_local--) {
                                    s.mtfa[k_local] = s.mtfa[s.mtfbase[i_local] + j_local];
                                    k_local--;
                                }
                                s.mtfbase[i_local] = k_local + 1;
                            }
                        }
                    }
                    s.unzftab[s.seqToUnseq[decoded & 0xff] & 0xff]++;
                    s.tt[nblock++] = s.seqToUnseq[decoded & 0xff] & 0xff;
                    
                    if (groupPos == 0) {
                        groupNo++;
                        groupPos = 50;
                        byte gSel = s.selector[groupNo];
                        gMinLen = s.minLens[gSel];
                        gLimit = s.limit[gSel];
                        gPerm = s.perm[gSel];
                        gBase = s.base[gSel];
                    }
                    groupPos--;
                    zn = gMinLen;
                    zvec = getBits(zn, s);
                    while (zvec > gLimit[zn]) {
                        zn++;
                        zvec = (zvec << 1) | getBit(s);
                    }
                    nextSym = gPerm[zvec - gBase[zn]];
                }
            }

            s.cftab[0] = 0;
            System.arraycopy(s.unzftab, 0, s.cftab, 1, 256);
            for (int i = 1; i <= 256; i++) s.cftab[i] += s.cftab[i - 1];
            
            for (int i = 0; i < nblock; i++) {
                byte c = (byte) (s.tt[i] & 0xff);
                s.tt[s.cftab[c & 0xff]] |= (i << 8);
                s.cftab[c & 0xff]++;
            }
            
            int ptr = s.tt[origPtr] >> 8;
            // The client does a recursive loop in method226 to handle RLE
            // We use the same ptr traversal logic here
            for (int n = 0; n < nblock; n++) {
                int val = s.tt[ptr];
                ptr = val >> 8;
                if (s.nextOut < s.outputLen) s.output[s.nextOut++] = (byte) val;
            }
            return;
        }
    }
    
    private static byte getByte(State s) { return (byte) getBits(8, s); }
    private static byte getBit(State s) { return (byte) getBits(1, s); }
    private static int getBits(int n, State s) {
        while (s.bsLive < n) {
            int b = (s.nextIn < s.input.length) ? s.input[s.nextIn++] & 0xff : 0;
            s.bsBuff = (s.bsBuff << 8) | b;
            s.bsLive += 8;
        }
        int v = (s.bsBuff >> (s.bsLive - n)) & ((1 << n) - 1);
        s.bsLive -= n;
        return v;
    }
    
    private static void createDecodeTables(int[] limit, int[] base, int[] perm, byte[] length, int minLen, int maxLen, int alphaSize) {
        int pp = 0;
        for (int i = minLen; i <= maxLen; i++) {
            for (int j = 0; j < alphaSize; j++) {
                if (length[j] == i) perm[pp++] = j;
            }
        }
        for (int i = 0; i < 23; i++) base[i] = 0;
        for (int i = 0; i < alphaSize; i++) base[length[i] + 1]++;
        for (int i = 1; i < 23; i++) base[i] += base[i - 1];
        for (int i = 0; i < 23; i++) limit[i] = 0;
        int vec = 0;
        for (int i = minLen; i <= maxLen; i++) {
            vec += base[i + 1] - base[i];
            limit[i] = vec - 1;
            vec <<= 1;
        }
        for (int i = minLen + 1; i <= maxLen; i++) base[i] = (limit[i - 1] + 1) << 1;
    }
}