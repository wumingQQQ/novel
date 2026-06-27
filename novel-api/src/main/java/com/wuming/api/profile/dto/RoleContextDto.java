package com.wuming.api.profile.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class RoleContextDto implements Serializable {
    private Long jobId;
    private Long novelId;
    private String novelName;
    private String protagonistName;
    private CharacterProfileDto characterProfile;
    private InteractionProfileDto interactionProfile;

    @Serial
    private static final long serialVersionUID = 1L;
}
