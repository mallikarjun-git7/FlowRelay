
package com.queueforge.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping({
        "/jobs",
        "/jobs/{id}",
        "/queues",
        "/workers",
        "/dead-letter",
        "/settings"
    })
    public String forwardToReact() {
        return "forward:/index.html";
    }
}
