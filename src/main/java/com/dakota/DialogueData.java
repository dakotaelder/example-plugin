package com.dakota;

import lombok.Data;
import java.util.List;

@Data
public class DialogueData
{
    private int npcId;
    private String npcName;
    private String text;
    private List<String> options;
    private int headModelId;
    private int animationId;
    private String type; // "NPC", "PLAYER", "OPTION"
}