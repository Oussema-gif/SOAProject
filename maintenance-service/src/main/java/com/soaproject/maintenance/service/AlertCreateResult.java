package com.soaproject.maintenance.service;

import com.soaproject.maintenance.dto.AlertResponse;

public record AlertCreateResult(AlertResponse alert, boolean created) {}