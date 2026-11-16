package com.soaproject.inventory.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PurchaseOrderIdGenerator {

    private final JdbcTemplate jdbc;

    public PurchaseOrderIdGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long seq = jdbc.queryForObject(
                "SELECT nextval('inventory.purchase_order_seq')", Long.class);
        return "PO-" + String.format("%06d", seq);
    }
}