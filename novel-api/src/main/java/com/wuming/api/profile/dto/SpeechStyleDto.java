package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class SpeechStyleDto implements Serializable {
    private String tone;
    private String wordsHabit;
    private List<String> representativeLines;

    @Serial
    private static final long serialVersionUID = 1L;
}
