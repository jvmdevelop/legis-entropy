package com.jvmd.graphsservice.dto;

import lombok.Data;

@Data
public class CreateUserGraphRequest {
    private String workspaceId;
    private String name;
    private String description;
}
