package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Data
public class InteractionProfileDto implements Serializable {
    private String tone;
    private List<String> keyEvents;
    private List<String> conversationSamples;

    @Serial
    private static final long serialVersionUID = 1L;
}
