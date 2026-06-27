package vn.conganh.commercial.feature.home;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Tag(name = "Home", description = "Home and Swagger UI entry points")
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "redirect:http://localhost:3000";
    }
}
