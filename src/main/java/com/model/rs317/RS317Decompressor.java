package com.model.rs317;

import java.io.*;

public class RS317Decompressor {

    private RandomAccessFile dataFile;
    private RandomAccessFile indexFile;
    private int storeId;
    private byte[] buffer = new byte[520];

    public RS317Decompressor(RandomAccessFile dataFile, RandomAccessFile indexFile, int storeId) {
        this.storeId = storeId;
        this.dataFile = dataFile;
        this.indexFile = indexFile;
    }

    public synchronized byte[] decompress(int i) {
        try {
            // 1. Read Index Entry (6 bytes)
            if (i * 6 + 6 > indexFile.length()) {
                System.out.println("[Decompressor] Index " + i + " is out of bounds (File len: " + indexFile.length() + ")");
                return null;
            }
            seekTo(indexFile, i * 6);
            int l;
            for (int j = 0; j < 6; j += l) {
                l = indexFile.read(buffer, j, 6 - j);
                if (l == -1) return null;
            }

            int size = ((buffer[0] & 0xff) << 16) + ((buffer[1] & 0xff) << 8) + (buffer[2] & 0xff);
            int sector = ((buffer[3] & 0xff) << 16) + ((buffer[4] & 0xff) << 8) + (buffer[5] & 0xff);

            if (size <= 0) {
                // System.out.println("[Decompressor] File " + i + " has size " + size);
                return null;
            }
            if (sector <= 0 || (long) sector > dataFile.length() / 520L) {
                System.out.println("[Decompressor] File " + i + " has invalid start sector: " + sector);
                return null;
            }

            byte[] result = new byte[size];
            int readBytes = 0;
            int part = 0;

            // 2. Read Sectors from Data File
            while (readBytes < size) {
                if (sector == 0) return null;
                
                seekTo(dataFile, sector * 520);
                
                int bytesToRead = size - readBytes;
                if (bytesToRead > 512) bytesToRead = 512;
                
                int j2;
                for (int k = 0; k < bytesToRead + 8; k += j2) {
                    j2 = dataFile.read(buffer, k, (bytesToRead + 8) - k);
                    if (j2 == -1) return null;
                }

                int currentFile = ((buffer[0] & 0xff) << 8) + (buffer[1] & 0xff);
                int currentPart = ((buffer[2] & 0xff) << 8) + (buffer[3] & 0xff);
                int nextSector = ((buffer[4] & 0xff) << 16) + ((buffer[5] & 0xff) << 8) + (buffer[6] & 0xff);
                int currentStore = buffer[7] & 0xff;

                // [PERMISSIVE FIX] Check validity but do not fail hard on StoreID mismatch
                if (currentFile != i || currentPart != part) {
                    System.out.println("[Decompressor] Sector Header Mismatch! Expected File: " + i + " Part: " + part + 
                                       " | Got File: " + currentFile + " Part: " + currentPart);
                    return null;
                }
                
                // Warn but continue if StoreID is wrong (Common in RSPS caches)
                if (currentStore != storeId) {
                    // System.out.println("[Warning] StoreID Mismatch. Expected " + storeId + " Got " + currentStore + ". Continuing...");
                }

                if (nextSector < 0 || (long) nextSector > dataFile.length() / 520L) {
                    return null;
                }

                for (int k = 0; k < bytesToRead; k++) {
                    result[readBytes++] = buffer[k + 8];
                }

                sector = nextSector;
                part++;
            }

            return result;

        } catch (IOException _ex) {
            _ex.printStackTrace();
            return null;
        }
    }

    private synchronized void seekTo(RandomAccessFile file, int pos) throws IOException {
        if (pos < 0 || pos > 0x3c00000) {
            pos = 0x3c00000;
            try { Thread.sleep(1000L); } catch (Exception _ex) { }
        }
        file.seek(pos);
    }
}