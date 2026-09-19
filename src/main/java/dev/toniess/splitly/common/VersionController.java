package dev.toniess.splitly.common;

import dev.toniess.splitly.common.dto.VersionResponse;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1")
public class VersionController {
    private final BuildProperties buildProperties;

    public VersionController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/version")
    public VersionResponse getVersion() {
        return new VersionResponse(buildProperties.getName(), buildProperties.getVersion(), buildProperties.getTime());
    }
}
