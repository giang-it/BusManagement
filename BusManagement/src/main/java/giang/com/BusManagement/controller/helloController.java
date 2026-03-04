package giang.com.BusManagement.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class helloController {

    @RequestMapping("/")
    public String getHomePage() {
        return "hello";
    }
}
