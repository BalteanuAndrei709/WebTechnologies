package org.gait.controller;

import lombok.RequiredArgsConstructor;
import org.gait.database.service.EndpointCallService;
import org.gait.dto.EndpointCall;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EndpointCallService endpointCallService;

    @GetMapping("/call-stats")
    public List<EndpointCall> getCallStats() {
        return endpointCallService.getCallStats();
    }
}
