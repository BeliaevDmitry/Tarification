package org.school.personalLoad.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping("/")
    @PreAuthorize("isAuthenticated()")
    public String home() {
        return "forward:/index.html";
    }

    @GetMapping("/ui/buildings")
    @PreAuthorize("isAuthenticated()")
    public String buildings() { return "forward:/buildings.html"; }

    @GetMapping("/ui/classes")
    @PreAuthorize("isAuthenticated()")
    public String classes() { return "forward:/classes.html"; }

    @GetMapping("/ui/curriculum")
    @PreAuthorize("isAuthenticated()")
    public String curriculum() { return "forward:/curriculum.html"; }

    @GetMapping("/ui/load")
    @PreAuthorize("isAuthenticated()")
    public String load() { return "forward:/load.html"; }

    @GetMapping("/ui/subjects")
    @PreAuthorize("isAuthenticated()")
    public String subjects() { return "forward:/subjects.html"; }

    @GetMapping("/ui/teachers")
    @PreAuthorize("isAuthenticated()")
    public String teachers() { return "forward:/teachers.html"; }


    @GetMapping("/ui/users")
    @PreAuthorize("hasRole('ADMIN')")
    public String users() { return "forward:/users.html"; }

    @GetMapping("/ui/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public String audit() { return "forward:/audit.html"; }

    @GetMapping("/ui/profile")
    @PreAuthorize("isAuthenticated()")
    public String profile() { return "forward:/profile.html"; }
}
