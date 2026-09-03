package com.adp.gateway.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ApiDocumentationController {

    private static final String SWAGGER_UI = "redirect:/swagger-ui/index.html";

    @GetMapping({"/", "/docs"})
    String documentation() {
        return SWAGGER_UI;
    }
}
