package com.wuming.chat.rag.retrieve;


import java.util.List;

public record RagRetrieveResult(
        String query,
        List<RagContext> contexts
){
    public static RagRetrieveResult empty(String query){
        return new RagRetrieveResult(query, List.of());
    }

    public boolean used(){
        return !contexts.isEmpty();
    }
}
