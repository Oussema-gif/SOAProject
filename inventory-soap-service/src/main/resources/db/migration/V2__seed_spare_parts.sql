INSERT INTO inventory.spare_parts
    (part_id, sku, name, description, unit, quantity_on_hand, reorder_level) VALUES
    ('PART-001', 'SKU-BRG-001', 'Industrial Bearing', 'Heavy-duty bearing for rotating machinery', 'PCS', 50, 10),
    ('PART-002', 'SKU-FAN-001', 'Cooling Fan',        'Axial cooling fan for machine enclosures',  'PCS', 20,  5),
    ('PART-003', 'SKU-PRS-001', 'Pressure Sensor',    'Industrial pressure sensor 0-10 bar',        'PCS', 30,  8),
    ('PART-004', 'SKU-BLT-001', 'Motor Belt',         'Reinforced drive belt for electric motors',  'PCS', 15,  5),
    ('PART-005', 'SKU-TMP-001', 'Temperature Sensor', 'PT100 temperature sensor',                   'PCS', 25,  8);

-- Audit trail starts consistent: every stock level is explained by a movement.
INSERT INTO inventory.stock_movements
    (part_id, movement_type, quantity_delta, quantity_after, reference_id)
SELECT part_id, 'MANUAL_ADJUSTMENT', quantity_on_hand, quantity_on_hand, 'INITIAL-SEED'
FROM inventory.spare_parts;