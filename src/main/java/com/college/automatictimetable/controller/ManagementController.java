package com.college.automatictimetable.controller;

import com.college.automatictimetable.service.ManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ManagementController {
    private final ManagementService management;

    public ManagementController(ManagementService management) {
        this.management = management;
    }

    @GetMapping("/{resource}")
    public List<Map<String, Object>> list(@PathVariable String resource) {
        return management.list(resource);
    }

    @PostMapping("/{resource}")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@PathVariable String resource, @RequestBody Map<String, Object> body) {
        return management.save(resource, null, body);
    }

    @PutMapping("/{resource}/{id}")
    public Map<String, Object> update(@PathVariable String resource, @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return management.save(resource, id, body);
    }

    @DeleteMapping("/{resource}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String resource, @PathVariable Long id) {
        management.delete(resource, id);
    }
}
