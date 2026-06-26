package com.wuming.novel.llm.parser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
class LlmJsonResponseRepairer {
    private final RecordListJsonRepairer recordListJsonRepairer;

    List<JsonRepairCandidate> repairCandidates(
            String json,
            Class<?> targetType
    ) {
        String repairedJson = recordListJsonRepairer.repair(json, targetType);
        if (repairedJson == null || repairedJson.isBlank()) {
            return List.of();
        }

        JsonRepairCandidate repairCandidate = new JsonRepairCandidate(
                repairedJson,
                "对象数组格式修复"
        );
        return List.of(repairCandidate).stream()
                .filter(candidate -> !candidate.json().equals(json))
                .toList();
    }
}
