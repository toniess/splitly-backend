package dev.toniess.splitly.settlement.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record Transfer(UUID from, UUID to, BigDecimal amount) {}
