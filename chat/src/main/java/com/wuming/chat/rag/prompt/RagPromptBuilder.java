package com.wuming.chat.rag.prompt;

import com.wuming.chat.rag.retrieve.RagContext;
import com.wuming.chat.rag.retrieve.RagRetrieveResult;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagPromptBuilder {

    /**
     * 将rag召回结果拼接到提示词中
     */
    public String buildContextPrompt(RagRetrieveResult result){
        if(result == null || !result.used()){
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【可参考的原作片段】\n");
        sb.append("以下片段可能与用户当前问题相关。");
        sb.append("只能用于补充角色记忆与说话依据，不能直接复述，不能在回答出出现类似根据xxx片段，我了解到...类似语句。");

        List<RagContext> contexts = result.contexts();
        for(int i = 0; i < contexts.size(); i++){
            RagContext context = contexts.get(i);
            sb.append(i + 1).append(".");
            if (context.chapterSequence() != null) {
                sb.append("第").append(context.chapterSequence()).append("章");
            }
            if(context.sceneSequence() != null){
                sb.append("场景").append(context.sceneSequence());
            }
            if(context.score() > 0){
                sb.append(", 相关度").append(String.format("%.2f", context.score()));
            }
            sb.append(": ").append(context.content());
            sb.append("\n");
        }

        return sb.toString();
    }
}
