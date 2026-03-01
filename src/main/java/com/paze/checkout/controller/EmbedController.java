package com.paze.checkout.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/checkout/embed")
public class EmbedController {

    @GetMapping
    public String embed(HttpServletResponse response) {
        response.setHeader("Content-Security-Policy",
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; " +
                "img-src 'self' data:; connect-src 'self'; frame-ancestors *");
        return "forward:/checkout/embed/index.html";
    }
}
