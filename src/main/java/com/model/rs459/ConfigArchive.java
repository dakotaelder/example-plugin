package com.model.rs459;

public class ConfigArchive {
    private byte[][] entries;

    public ConfigArchive(byte[] archive) {
        if (archive != null) {
            decode(archive);
        }
    }

    public byte[] getFile(int id) {
        if (entries == null || id < 0 || id >= entries.length) return null;
        return entries[id];
    }
    
    public int getEntryCount() {
        return entries == null ? 0 : entries.length;
    }

    private void decode(byte[] data) {
        try {
            Buffer stream = new Buffer(data);
            int len = data.length;
            
            // The last byte is ALWAYS the number of chunks.
            stream.offset = len - 1;
            int chunks = stream.readUnsignedByte();
            
            // CALCULATE FILE COUNT STRICTLY
            // FooterSize = FileCount * Chunks * 4
            // We iterate to find the 'FileCount' where the math balances perfectly.
            // This is not guessing; it is structural validation.
            int detectedCount = 0;
            int payloadSize = 0;
            int footerStart = 0;
            
            // We scan for the footer boundary
            for (int i = 1; i < 20000; i++) { // Max reasonable NPCs
                int testFooterSize = i * chunks * 4;
                if (testFooterSize >= len) break;
                
                int testFooterStart = len - 1 - testFooterSize;
                
                // Verify this hypothetical footer structure
                stream.offset = testFooterStart;
                int testPayload = 0;
                boolean valid = true;
                
                try {
                    for (int c = 0; c < chunks; c++) {
                        for (int f = 0; f < i; f++) {
                            testPayload += stream.readInt();
                        }
                    }
                } catch(Exception e) { valid = false; }
                
                // If the sum of the file sizes equals the start of the footer, 
                // WE FOUND THE EXACT COUNT.
                if (valid && testPayload == testFooterStart) {
                    detectedCount = i;
                    payloadSize = testPayload;
                    footerStart = testFooterStart;
                    break;
                }
            }
            
            if (detectedCount == 0) {
                System.err.println("Failed to detect Config Archive structure.");
                return;
            }

            // EXTRACT
            entries = new byte[detectedCount][];
            int[] sizes = new int[detectedCount];
            stream.offset = footerStart;
            
            // Read sizes
            for (int c = 0; c < chunks; c++) {
                int delta = 0;
                for (int f = 0; f < detectedCount; f++) {
                    delta += stream.readInt();
                    sizes[f] += delta;
                }
            }
            
            // Allocate
            for (int f = 0; f < detectedCount; f++) {
                entries[f] = new byte[sizes[f]];
            }
            
            // Copy Payload
            stream.offset = footerStart;
            int readPos = 0;
            int[] writePos = new int[detectedCount];
            
            for (int c = 0; c < chunks; c++) {
                int delta = 0;
                for (int f = 0; f < detectedCount; f++) {
                    delta += stream.readInt();
                    System.arraycopy(data, readPos, entries[f], writePos[f], delta);
                    readPos += delta;
                    writePos[f] += delta;
                }
            }
            
            System.out.println("ConfigArchive loaded 1:1. Found " + detectedCount + " files.");
            
        } catch (Exception e) { e.printStackTrace(); }
    }
}