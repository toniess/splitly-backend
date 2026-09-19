package dev.toniess.splitly.common.dto;

import java.time.Instant;

public record VersionResponse(String name, String version, Instant buildTime) {}
