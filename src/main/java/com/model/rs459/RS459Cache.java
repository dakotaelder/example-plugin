package com.model.rs459;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class RS459Cache {
    private RandomAccessFile dataFile;
    private Map<Integer, CacheIndex> indices = new HashMap<>();

    public RS459Cache(String path) {
        try {
            File data = new File(path, "main_file_cache.dat2");
            if (!data.exists()) data = new File(path, "main_file_cache.dat");
            
            if (!data.exists()) {
                System.err.println("ERROR: main_file_cache.dat2 not found at " + path);
                return;
            }
            dataFile = new RandomAccessFile(data, "r");
            
            for (int i = 0; i < 255; i++) {
                File idx = new File(path, "main_file_cache.idx" + i);
                if (idx.exists()) {
                    indices.put(i, new CacheIndex(dataFile, new RandomAccessFile(idx, "r"), i));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public byte[] readArchive(int indexId, int archiveId) {
        CacheIndex idx = indices.get(indexId);
        if (idx == null) return null;
        
        byte[] raw = idx.readFile(archiveId);
        return unpackJS5(raw, indexId, archiveId);
    }
    
    private byte[] unpackJS5(byte[] data, int indexId, int archiveId) {
        if (data == null || data.length < 5) return null;

        try {
            // Raw GZIP Check
            if (data[0] == 0x1F && data[1] == (byte)0x8B) {
                try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(data))) {
                    return gzis.readAllBytes();
                }
            }

            int compression = data[0] & 0xFF;
            long compressedLen = ((data[1] & 0xFF) << 24) | ((data[2] & 0xFF) << 16) | ((data[3] & 0xFF) << 8) | (data[4] & 0xFF);
            
            // [STRICT] CORRUPTION FILTER
            // If header claims > 100 bytes more than actual file size, IT IS CORRUPT.
            if (compressedLen > data.length + 100 || compressedLen < 0) {
                 return null; // Ignore this file completely
            }

            if (compression == 0) { // Uncompressed
                byte[] out = new byte[(int)compressedLen];
                System.arraycopy(data, 5, out, 0, (int)compressedLen);
                return out;
            }
            
            if (compression == 1) { // BZIP2
                if (data.length < 9) return null;
                int uncompressedLen = ((data[5] & 0xFF) << 24) | ((data[6] & 0xFF) << 16) | ((data[7] & 0xFF) << 8) | (data[8] & 0xFF);
                
                byte[] output = new byte[uncompressedLen];
                try {
                    BZip2.decompress(output, uncompressedLen, data, (int)compressedLen, 9);
                } catch (Exception e) {
                    return null; // Ignore BZip2 failures
                }
                return output;
            }

            if (compression == 2) { // GZIP
                int offset = 9;
                if (data.length > 10 && data[9] == 0x1F && data[10] == (byte)0x8B) offset = 9; 
                else if (data.length > 6 && data[5] == 0x1F && data[6] == (byte)0x8B) offset = 5;
                
                try (ByteArrayInputStream bais = new ByteArrayInputStream(data, offset, data.length - offset);
                     GZIPInputStream gzis = new GZIPInputStream(bais)) {
                    return gzis.readAllBytes();
                }
            }
            
        } catch (Exception e) { }
        return null;
    }
}