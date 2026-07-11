package com.wuming.novel.domain.dto;

import lombok.Data;

/**
 * 一个角色在指定评测数据集版本下的汇总报告。
 */
@Data
public class RoleEvaluationReportResponse {
    private String datasetVersion;
    private int caseCount;
    private int approvedCaseCount;
    private int succeededRunCount;
    private int invalidRunCount;
    private int failedRunCount;
    private Double averageTotalScore;
}
