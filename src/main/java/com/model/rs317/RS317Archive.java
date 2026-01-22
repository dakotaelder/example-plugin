package com.model.rs317;

/**
 * 1:1 Port of client317/StreamLoader.java
 */
public class RS317Archive {

    private final byte[] aByteArray726;
    private final int dataSize;
    private final int[] anIntArray728;
    private final int[] anIntArray729;
    private final int[] anIntArray730;
    private final int[] anIntArray731;
    private final boolean aBoolean732;

    public RS317Archive(byte[] abyte0) {
        RS317Stream stream = new RS317Stream(abyte0);
        int i = stream.read3Bytes();
        int j = stream.read3Bytes();
        
        if(j != i) {
            byte abyte1[] = new byte[i];
            // [NATIVE 317 CALL]
            Class13.method225(abyte1, i, abyte0, j, 6);
            aByteArray726 = abyte1;
            stream = new RS317Stream(aByteArray726);
            aBoolean732 = true;
        } else {
            aByteArray726 = abyte0;
            aBoolean732 = false;
        }
        
        dataSize = stream.readUnsignedWord();
        anIntArray728 = new int[dataSize];
        anIntArray729 = new int[dataSize];
        anIntArray730 = new int[dataSize];
        anIntArray731 = new int[dataSize];
        
        int k = stream.offset + dataSize * 10;
        for(int l = 0; l < dataSize; l++) {
            anIntArray728[l] = stream.readDWord();
            anIntArray729[l] = stream.read3Bytes();
            anIntArray730[l] = stream.read3Bytes();
            anIntArray731[l] = k;
            k += anIntArray730[l];
        }
    }

    public byte[] getFile(String s) {
        int i = 0;
        s = s.toUpperCase();
        for(int j = 0; j < s.length(); j++)
            i = (i * 61 + s.charAt(j)) - 32;

        for(int k = 0; k < dataSize; k++) {
            if(anIntArray728[k] == i) {
                byte[] abyte0 = new byte[anIntArray729[k]];
                if(!aBoolean732) {
                    // [NATIVE 317 CALL] Individual file decompression
                    Class13.method225(abyte0, anIntArray729[k], aByteArray726, anIntArray730[k], anIntArray731[k]);
                } else {
                    System.arraycopy(aByteArray726, anIntArray731[k], abyte0, 0, anIntArray729[k]);
                }
                return abyte0;
            }
        }
        return null;
    }
    public String debugContents() {
        StringBuilder sb = new StringBuilder();
        return sb.toString();
    }

}