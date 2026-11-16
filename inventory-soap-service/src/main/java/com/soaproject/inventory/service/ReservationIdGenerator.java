package com.soaproject.inventory.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReservationIdGenerator {

    private final JdbcTemplate jdbc;

    public ReservationIdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long seq = jdbc.queryForObject(
                "SELECT nextval('inventory.reservation_seq')", Long.class);
        return "RES-" + String.format("%06d", seq);
    }
}