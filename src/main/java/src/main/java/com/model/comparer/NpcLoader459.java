package src.main.java.com.model.comparer;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.List;

public class NpcLoader459 {
    private static final String PATH = "C:\\Users\\dakot\\Desktop\\runescapeserverdata\\data\\cache_459\\npc_dump.json";

    public static List<NpcDef459> load() {
        File file = new File(PATH);
        if (!file.exists()) return Collections.emptyList();
        
        try (FileReader reader = new FileReader(file)) {
            return new Gson().fromJson(reader, new TypeToken<List<NpcDef459>>(){}.getType());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
}