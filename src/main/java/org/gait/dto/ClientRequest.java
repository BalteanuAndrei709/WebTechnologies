package org.gait.dto;

import lombok.Data;

@Data
public class ClientRequest {
    private String prompt;
    private Api api;
}