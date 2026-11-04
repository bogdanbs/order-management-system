package com.orders.query.rebuild;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class ProjectionRebuildController {
    private final ProjectionRebuilderService rebuilder;

    public ProjectionRebuildController(ProjectionRebuilderService rebuilder) {
        this.rebuilder = rebuilder;
    }

    @PostMapping("/projections/rebuild")
    public ResponseEntity<String> rebuild() {
        rebuilder.rebuild();
        return ResponseEntity.ok("Projection rebuild complete");
    }
}
