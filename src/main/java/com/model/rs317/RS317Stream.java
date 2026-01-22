package com.model.rs317;

public class RS317Stream {
    public byte[] payload;
    public int offset;

    public RS317Stream(byte[] payload) {
        this.payload = payload;
        this.offset = 0;
    }

    public int readUnsignedByte() {
        return payload[offset++] & 0xff;
    }

    public int readUnsignedWord() {
        offset += 2;
        return ((payload[offset - 2] & 0xff) << 8) + (payload[offset - 1] & 0xff);
    }

    public int read3Bytes() {
        offset += 3;
        return ((payload[offset - 3] & 0xff) << 16) + ((payload[offset - 2] & 0xff) << 8) + (payload[offset - 1] & 0xff);
    }

    public int readDWord() {
        offset += 4;
        return ((payload[offset - 4] & 0xff) << 24) + ((payload[offset - 3] & 0xff) << 16) + ((payload[offset - 2] & 0xff) << 8) + (payload[offset - 1] & 0xff);
    }

    public int readSignedSmart() {
        int value = payload[offset] & 0xff;
        if (value < 128) return readUnsignedByte() - 64;
        return readUnsignedWord() - 49152;
    }

    public String readString() {
        int start = offset;
        while (payload[offset++] != 10) ;
        return new String(payload, start, offset - start - 1);
    }
}