package com.model.rs459;

import java.io.IOException;
import java.io.RandomAccessFile;

public class CacheIndex {
    private static final int INDEX_SIZE = 6;
    private final RandomAccessFile dataFile;
    private final RandomAccessFile indexFile;
    private final int indexId;

    public CacheIndex(RandomAccessFile dataFile, RandomAccessFile indexFile, int indexId) {
        this.dataFile = dataFile;
        this.indexFile = indexFile;
        this.indexId = indexId;
    }
    
    // [ADDED] Get total number of files in this index
    public int getCount() {
        try {
            return (int) (indexFile.length() / INDEX_SIZE);
        } catch (IOException e) {
            return 0;
        }
    }

    public byte[] readFile(int fileId) {
        try {
            long idxLen = indexFile.length();
            long pos = (long) fileId * INDEX_SIZE;
            
            if (pos + INDEX_SIZE > idxLen) return null;
            
            indexFile.seek(pos);
            byte[] buf = new byte[INDEX_SIZE];
            indexFile.readFully(buf);
            
            int size = ((buf[0] & 0xff) << 16) | ((buf[1] & 0xff) << 8) | (buf[2] & 0xff);
            int sector = ((buf[3] & 0xff) << 16) | ((buf[4] & 0xff) << 8) | (buf[5] & 0xff);
            
            if (size <= 0 || sector <= 0) return null;
            
            byte[] data = new byte[size];
            int read = 0;
            int part = 0;
            int nextSector = sector;
            
            while (read < size) {
                if (nextSector == 0) break;
                long sectorPos = (long) nextSector * 520;
                if (sectorPos + 520 > dataFile.length()) break;

                dataFile.seek(sectorPos);
                byte[] header = new byte[8];
                dataFile.readFully(header);
                
                int currentFileId = ((header[0] & 0xff) << 8) | (header[1] & 0xff);
                int currentPart = ((header[2] & 0xff) << 8) | (header[3] & 0xff);
                int nextSectorId = ((header[4] & 0xff) << 16) | ((header[5] & 0xff) << 8) | (header[6] & 0xff);
                int currentIndex = header[7] & 0xff;
                
                int toRead = Math.min(512, size - read);
                dataFile.readFully(data, read, toRead);
                read += toRead;
                nextSector = nextSectorId;
                part++;
            }
            return data;
        } catch (IOException e) {
            return null;
        }
    }
}