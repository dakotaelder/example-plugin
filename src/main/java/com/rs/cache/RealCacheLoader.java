package com.rs.cache;

import com.model.rs459.ConfigArchive;
import com.model.rs459.NpcDefinition;
import com.model.rs459.RS459Cache;

import java.io.File;
import java.util.logging.Logger;

/**
 * 459 Cache Loader - Implementation
 * uses the com.model.rs459 library to extract and decode NPCs.
 */
public class RealCacheLoader {

    private static final Logger logger = Logger.getLogger(RealCacheLoader.class.getName());

    // PATH verified from your screenshot:
    private static final String CACHE_PATH = "\cache_459\\.jagex_cache_32\\runescape\\";

    // 459 Constants
    private static final int CONFIG_INDEX = 2;   // The Index for Configs (Items, NPCs, Objects)
    private static final int NPC_ARCHIVE_ID = 9; // The specific archive ID inside Index 2 for NPCs

    public static void main(String[] args) {
        try {
            logger.info("Initializing Cache from: " + CACHE_PATH);
            
            // 1. Initialize the Cache Library
            // This handles opening main_file_cache.dat2 and the .idx files
            RS459Cache cache = new RS459Cache(CACHE_PATH);

            // 2. Fetch the Raw Compressed Data for NPCs (Index 2, Archive 9)
            logger.info("Attempting to fetch Index " + CONFIG_INDEX + ", Archive " + NPC_ARCHIVE_ID + "...");
            byte[] archiveData = cache.readArchive(CONFIG_INDEX, NPC_ARCHIVE_ID);

            if (archiveData == null) {
                logger.severe("Failed to read NPC archive. Check if the path is correct and files exist.");
                return;
            }
            logger.info("Successfully read " + archiveData.length + " bytes of compressed data.");

            // 3. Split the Archive into Individual Files
            // In 459, all NPCs are packed into one archive. ConfigArchive splits them.
            // (It uses the brute-force footer check from your ConfigArchive.java)
            ConfigArchive config = new ConfigArchive(archiveData);
            int count = config.getEntryCount();

            logger.info("Archive Split Successful! Found " + count + " NPC definitions.");

            // 4. Decode and Verify
            // Let's print the first few to prove it works "actually"
            int printLimit = 10; 
            for (int i = 0; i < count; i++) {
                byte[] npcData = config.getFile(i);
                if (npcData != null) {
                    NpcDefinition def = new NpcDefinition(i, npcData);
                    
                    // Print the first few, or specific ones like Hans (0) or Man (1)
                    if (i < printLimit || def.name.equalsIgnoreCase("Hans") || def.name.equalsIgnoreCase("Kbd")) {
                        System.out.println("ID: " + i + " | Name: " + def.name + " | Level: " + def.combatLevel + " | Actions: " + java.util.Arrays.toString(def.actions));
                    }
                }
            }
            
            logger.info("Done. Cache loaded successfully.");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
