package com.model.rs459;

public class Buffer {
    public byte[] payload;
    public int offset;

    public Buffer(byte[] payload) {
        this.payload = payload;
        this.offset = 0;
    }

    public int readUnsignedByte() {
        return payload[offset++] & 0xff;
    }

    public int readUnsignedShort() {
        offset += 2;
        return ((payload[offset - 2] & 0xff) << 8) + (payload[offset - 1] & 0xff);
    }

    public int readInt() {
        offset += 4;
        return ((payload[offset - 4] & 0xff) << 24) + ((payload[offset - 3] & 0xff) << 16) +
               ((payload[offset - 2] & 0xff) << 8) + (payload[offset - 1] & 0xff);
    }
    
    public int readUnsignedSmart() {
        int peek = payload[offset] & 0xff;
        return peek < 128 ? readUnsignedByte() : readUnsignedShort() - 32768;
    }
    
    public String readString() {
        int start = offset;
        while (payload[offset++] != 0) {}
        return new String(payload, start, offset - start - 1);
    }
    public int readSignedSmart() {
        int peek = payload[offset] & 0xff;
        if (peek < 128) {
            return readUnsignedByte() - 64;
        } else {
            return readUnsignedShort() - 49152;
        }
    }
}