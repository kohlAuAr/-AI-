package com.campus.business.activity;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityRepository activities;

    public ActivityController(ActivityRepository activities) {
        this.activities = activities;
    }

    @GetMapping
    public List<Activity> list() {
        return activities.findAll();
    }
}
