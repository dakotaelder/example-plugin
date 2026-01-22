package src.main.java.com.model.comparer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

public class NpcLoaderFullOsrs {
    // [SETTINGS] Path to your custom JSON
    private static final String PATH = "C:\\Users\\dakot\\Desktop\\runescapeserverdata\\data\\npc\\fullOsrs.json";

    public static List<NpcDefFullOsrs> load() {
        File file = new File(PATH);
        if (!file.exists()) {
            System.out.println("Could not find file: " + PATH);
            return Collections.emptyList();
        }
        
        try (FileReader reader = new FileReader(file)) {
            return new Gson().fromJson(reader, new TypeToken<List<NpcDefFullOsrs>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}