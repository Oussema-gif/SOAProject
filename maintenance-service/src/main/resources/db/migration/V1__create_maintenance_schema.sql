-- Ownership: this schema belongs to the Maintenance REST service only.
-- No foreign keys to the inventory schema; inventory ids are plain strings.

CREATE OR REPLACE FUNCTION maintenance.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------- users
CREATE TABLE maintenance.users (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    username      varchar(50)  NOT NULL,
    password_hash varchar(100) NOT NULL,
    role          varchar(30)  NOT NULL,
    enabled       boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_users_role CHECK (role IN
        ('ADMIN', 'MAINTENANCE_OPERATOR', 'TECHNICIAN', 'INVENTORY_OPERATOR'))
);
CREATE UNIQUE INDEX uq_users_username_lower ON maintenance.users (lower(username));

-- ------------------------------------------------------------- machines
CREATE TABLE maintenance.machines (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    code       varchar(50)  NOT NULL,           -- public machineId, e.g. MACHINE-001
    name       varchar(150) NOT NULL,
    location   varchar(150),
    model      varchar(100),
    status     varchar(20)  NOT NULL DEFAULT 'RUNNING',
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_machines_code UNIQUE (code),
    CONSTRAINT ck_machines_status CHECK (status IN
        ('RUNNING', 'WARNING', 'CRITICAL', 'OFFLINE', 'MAINTENANCE'))
);

-- ---------------------------------------------------------- technicians
CREATE TABLE maintenance.technicians (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid         REFERENCES maintenance.users (id) ON DELETE SET NULL,
    full_name  varchar(150) NOT NULL,
    email      varchar(150) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT uq_technicians_user UNIQUE (user_id)
);
CREATE UNIQUE INDEX uq_technicians_email_lower ON maintenance.technicians (lower(email));

-- --------------------------------------------------------------- alerts
CREATE TABLE maintenance.alerts (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id       uuid          NOT NULL REFERENCES maintenance.machines (id) ON DELETE RESTRICT,
    metric_type      varchar(20)   NOT NULL,
    severity         varchar(20)   NOT NULL,
    observed_value   numeric(12,3) NOT NULL,
    threshold_value  numeric(12,3) NOT NULL,
    message          varchar(500)  NOT NULL,
    status           varchar(20)   NOT NULL DEFAULT 'ACTIVE',
    source_event_id  varchar(100),
    occurred_at      timestamptz   NOT NULL,
    acknowledged_at  timestamptz,
    resolved_at      timestamptz,
    version          bigint        NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT ck_alerts_metric   CHECK (metric_type IN ('TEMPERATURE', 'VIBRATION', 'PRESSURE', 'ENERGY')),
    CONSTRAINT ck_alerts_severity CHECK (severity IN ('WARNING', 'CRITICAL')),
    CONSTRAINT ck_alerts_status   CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED')),
    CONSTRAINT ck_alerts_resolved_at CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE INDEX idx_alerts_status_occurred ON maintenance.alerts (status, occurred_at DESC);
CREATE INDEX idx_alerts_machine         ON maintenance.alerts (machine_id);

-- Deduplication rule D6: at most one OPEN alert per machine + metric.
CREATE UNIQUE INDEX uq_alerts_one_open_per_machine_metric
    ON maintenance.alerts (machine_id, metric_type)
    WHERE status IN ('ACTIVE', 'ACKNOWLEDGED');

-- Idempotency by telemetry event id.
CREATE UNIQUE INDEX uq_alerts_source_event
    ON maintenance.alerts (source_event_id)
    WHERE source_event_id IS NOT NULL;

-- ----------------------------------------------------------- work_orders
CREATE TABLE maintenance.work_orders (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id    uuid         NOT NULL REFERENCES maintenance.machines (id) ON DELETE RESTRICT,
    alert_id      uuid         REFERENCES maintenance.alerts (id) ON DELETE SET NULL,
    technician_id uuid         REFERENCES maintenance.technicians (id) ON DELETE SET NULL,
    title         varchar(200) NOT NULL,
    description   varchar(2000),
    priority      varchar(20)  NOT NULL DEFAULT 'MEDIUM',
    status        varchar(30)  NOT NULL DEFAULT 'OPEN',
    completed_at  timestamptz,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_work_orders_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_work_orders_status CHECK (status IN
        ('OPEN', 'CHECKING_INVENTORY', 'READY_FOR_MAINTENANCE', 'WAITING_FOR_PARTS',
         'PENDING_RESERVATION', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_work_orders_completed_at CHECK ((status = 'COMPLETED') = (completed_at IS NOT NULL))
);
CREATE INDEX idx_work_orders_status     ON maintenance.work_orders (status);
CREATE INDEX idx_work_orders_technician ON maintenance.work_orders (technician_id);
CREATE INDEX idx_work_orders_machine    ON maintenance.work_orders (machine_id);

-- A repeated "create work order" for the same alert must not create a second live one.
CREATE UNIQUE INDEX uq_work_orders_one_live_per_alert
    ON maintenance.work_orders (alert_id)
    WHERE alert_id IS NOT NULL AND status NOT IN ('COMPLETED', 'CANCELLED');

-- ------------------------------------------------ work_order_reservations
-- reservation_id is an EXTERNAL reference to the SOAP inventory service.
CREATE TABLE maintenance.work_order_reservations (
    id             uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    work_order_id  uuid        NOT NULL REFERENCES maintenance.work_orders (id) ON DELETE CASCADE,
    reservation_id varchar(64) NOT NULL,
    part_id        varchar(32) NOT NULL,
    quantity       integer     NOT NULL,
    status         varchar(20) NOT NULL DEFAULT 'ACTIVE',
    released_at    timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_wor_reservation_id UNIQUE (reservation_id),
    CONSTRAINT ck_wor_quantity CHECK (quantity > 0),
    CONSTRAINT ck_wor_status   CHECK (status IN ('ACTIVE', 'RELEASED')),
    CONSTRAINT ck_wor_released_at CHECK ((status = 'RELEASED') = (released_at IS NOT NULL))
);
CREATE INDEX idx_wor_work_order ON maintenance.work_order_reservations (work_order_id);

-- At most one ACTIVE reservation per work order + part (safe retries).
CREATE UNIQUE INDEX uq_wor_one_active_per_part
    ON maintenance.work_order_reservations (work_order_id, part_id)
    WHERE status = 'ACTIVE';

-- ------------------------------------------------------ updated_at triggers
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON maintenance.users
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();
CREATE TRIGGER trg_machines_updated_at BEFORE UPDATE ON maintenance.machines
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();
CREATE TRIGGER trg_technicians_updated_at BEFORE UPDATE ON maintenance.technicians
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();
CREATE TRIGGER trg_alerts_updated_at BEFORE UPDATE ON maintenance.alerts
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();
CREATE TRIGGER trg_work_orders_updated_at BEFORE UPDATE ON maintenance.work_orders
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();
CREATE TRIGGER trg_wor_updated_at BEFORE UPDATE ON maintenance.work_order_reservations
    FOR EACH ROW EXECUTE FUNCTION maintenance.set_updated_at();