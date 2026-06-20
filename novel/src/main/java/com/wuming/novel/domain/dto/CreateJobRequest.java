package com.wuming.novel.domain.dto;

import lombok.Data;

@Data
public class CreateJobRequest {
    private Long novelId;
    private String protagonistName;
    private String targetName;
}
