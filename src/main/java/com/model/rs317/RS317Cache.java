package com.model.rs317;

import java.io.*;
import com.model.rs459.BZip2; // Using the BZip2 we fixed earlier

public class RS317Cache {

    private RandomAccessFile dataFile;
    private RandomAccessFile indexFile;
    private int storeId;

    public RS317Cache(String path, int storeId) throws Exception {
        this.storeId = storeId;
        File data = new File(path + "main_file_cache.dat");
        File index = new File(path + "main_file_cache.idx" + storeId);
        
        if (!data.exists() || !index.exists()) {
            throw new FileNotFoundException("Missing cache files at " + path);
        }
        
        this.dataFile = new RandomAccessFile(data, "r");
        this.indexFile = new RandomAccessFile(index, "r");
    }

    public byte[] readEntry(int id) {
        try {
            // Read Index
            if (id * 6 + 6 > indexFile.length()) return null;
            indexFile.seek(id * 6);
            byte[] buf = new byte[6];
            indexFile.readFully(buf);
            
            int size = ((buf[0] & 0xff) << 16) | ((buf[1] & 0xff) << 8) | (buf[2] & 0xff);
            int sector = ((buf[3] & 0xff) << 16) | ((buf[4] & 0xff) << 8) | (buf[5] & 0xff);
            
            if (size <= 0) return null;
            
            byte[] data = new byte[size];
            int read = 0;
            int part = 0;
            
            while (read < size) {
                if (sector == 0) return null;
                dataFile.seek(sector * 520);
                
                byte[] header = new byte[8];
                dataFile.readFully(header);
                
                int nextSector = ((header[4] & 0xff) << 16) | ((header[5] & 0xff) << 8) | (header[6] & 0xff);
                int currentFile = ((header[0] & 0xff) << 8) | (header[1] & 0xff);
                int currentPart = ((header[2] & 0xff) << 8) | (header[3] & 0xff);
                int currentCache = header[7] & 0xff;
                
                if (currentFile != id || currentPart != part || currentCache != storeId) return null;
                
                int len = size - read;
                if (len > 512) len = 512;
                
                dataFile.readFully(data, read, len);
                read += len;
                sector = nextSector;
                part++;
            }
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}