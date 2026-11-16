-- Ownership: this schema belongs to the Inventory SOAP service only.
-- work_order_id is an EXTERNAL reference (string) to the maintenance service.

CREATE OR REPLACE FUNCTION inventory.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE SEQUENCE inventory.reservation_seq    START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE inventory.purchase_order_seq START WITH 1 INCREMENT BY 1;

-- ---------------------------------------------------------- spare_parts
CREATE TABLE inventory.spare_parts (
    part_id          varchar(32)  PRIMARY KEY,          -- e.g. PART-001
    sku              varchar(64)  NOT NULL,
    name             varchar(150) NOT NULL,
    description      varchar(500),
    unit             varchar(20)  NOT NULL,
    quantity_on_hand integer      NOT NULL,
    reorder_level    integer      NOT NULL DEFAULT 0,   -- SOAP "minimumStockLevel"
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_spare_parts_sku UNIQUE (sku),
    CONSTRAINT ck_spare_parts_qty_non_negative CHECK (quantity_on_hand >= 0),
    CONSTRAINT ck_spare_parts_reorder_non_negative CHECK (reorder_level >= 0)
);

-- ------------------------------------------------------ part_reservations
CREATE TABLE inventory.part_reservations (
    reservation_id varchar(32) PRIMARY KEY
        DEFAULT ('RES-' || lpad(nextval('inventory.reservation_seq')::text, 6, '0')),
    part_id        varchar(32) NOT NULL REFERENCES inventory.spare_parts (part_id),
    quantity       integer     NOT NULL,
    request_id     varchar(64) NOT NULL,                -- idempotency key
    work_order_id  varchar(64) NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'CONFIRMED',
    release_reason varchar(300),
    released_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_part_reservations_request UNIQUE (request_id),
    CONSTRAINT ck_part_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT ck_part_reservations_status CHECK (status IN
        ('PENDING', 'CONFIRMED', 'RELEASED', 'REJECTED')),
    CONSTRAINT ck_part_reservations_released_at CHECK ((status = 'RELEASED') = (released_at IS NOT NULL))
);
CREATE INDEX idx_part_reservations_work_order ON inventory.part_reservations (work_order_id);
CREATE INDEX idx_part_reservations_part       ON inventory.part_reservations (part_id);

-- ------------------------------------------------------- stock_movements
CREATE TABLE inventory.stock_movements (
    id             bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    part_id        varchar(32) NOT NULL REFERENCES inventory.spare_parts (part_id),
    movement_type  varchar(30) NOT NULL,
    quantity_delta integer     NOT NULL,                -- negative = stock leaves
    quantity_after integer     NOT NULL,
    reference_id   varchar(64),                         -- reservation / PO / seed marker
    created_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_movements_type CHECK (movement_type IN
        ('RESERVATION', 'RELEASE', 'PURCHASE_RECEIPT', 'MANUAL_ADJUSTMENT')),
    CONSTRAINT ck_stock_movements_delta CHECK (quantity_delta <> 0),
    CONSTRAINT ck_stock_movements_after CHECK (quantity_after >= 0)
);
CREATE INDEX idx_stock_movements_part_created ON inventory.stock_movements (part_id, created_at);
CREATE INDEX idx_stock_movements_reference    ON inventory.stock_movements (reference_id);

-- -------------------------------------------------------- purchase_orders
CREATE TABLE inventory.purchase_orders (
    purchase_order_id  varchar(32) PRIMARY KEY
        DEFAULT ('PO-' || lpad(nextval('inventory.purchase_order_seq')::text, 6, '0')),
    part_id            varchar(32) NOT NULL REFERENCES inventory.spare_parts (part_id),
    quantity           integer     NOT NULL,
    work_order_id      varchar(64),
    reason             varchar(300),
    supplier_reference varchar(100),
    status             varchar(20) NOT NULL DEFAULT 'CREATED',
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_purchase_orders_quantity CHECK (quantity > 0),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN
        ('CREATED', 'ORDERED', 'RECEIVED', 'CANCELLED'))
);
CREATE INDEX idx_purchase_orders_part ON inventory.purchase_orders (part_id);

-- ------------------------------------------------------ updated_at triggers
CREATE TRIGGER trg_spare_parts_updated_at BEFORE UPDATE ON inventory.spare_parts
    FOR EACH ROW EXECUTE FUNCTION inventory.set_updated_at();
CREATE TRIGGER trg_part_reservations_updated_at BEFORE UPDATE ON inventory.part_reservations
    FOR EACH ROW EXECUTE FUNCTION inventory.set_updated_at();
CREATE TRIGGER trg_purchase_orders_updated_at BEFORE UPDATE ON inventory.purchase_orders
    FOR EACH ROW EXECUTE FUNCTION inventory.set_updated_at();