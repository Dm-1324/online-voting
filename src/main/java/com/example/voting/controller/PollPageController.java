package com.example.voting.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class PollPageController {
    @GetMapping("/p/{shareCode}")
    public String pollPage(@PathVariable String shareCode) {
        return "forward:/index.html";
    }
}
