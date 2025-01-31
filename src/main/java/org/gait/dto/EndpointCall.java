package org.gait.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EndpointCall {
    private String userEmail;
    private String endpointName;
    private Long callCount;
}