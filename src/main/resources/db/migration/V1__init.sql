CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE cars (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    make            text          NOT NULL,
    model           text          NOT NULL,
    model_year      int           NOT NULL,
    price_per_hour  numeric(10,2) NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT cars_make_not_blank CHECK (length(btrim(make)) > 0),
    CONSTRAINT cars_model_not_blank CHECK (length(btrim(model)) > 0),
    CONSTRAINT cars_model_year_valid CHECK (model_year BETWEEN 1900 AND 2100),
    CONSTRAINT cars_price_per_hour_valid CHECK (price_per_hour > 0 AND price_per_hour <= 10000)
);

CREATE TABLE reservations (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    car_id       uuid          NOT NULL REFERENCES cars (id),
    user_id      text          NOT NULL,
    start_ts     timestamptz   NOT NULL,
    end_ts       timestamptz   NOT NULL,
    status       text          NOT NULL,
    price        numeric(13,2),
    rented_at    timestamptz,
    returned_at  timestamptz,
    version      bigint        NOT NULL DEFAULT 0,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT reservations_status_valid
        CHECK (status IN ('RESERVED', 'RENTED', 'RETURNED', 'CANCELLED')),
    CONSTRAINT reservations_time_window_valid
        CHECK (end_ts > start_ts),
    CONSTRAINT reservations_price_positive
        CHECK (price IS NULL OR price > 0),
    CONSTRAINT reservations_no_overlap_active
        EXCLUDE USING gist (
            car_id WITH =,
            tstzrange(start_ts, end_ts, '[)') WITH &&
        ) WHERE (status IN ('RESERVED', 'RENTED'))
);

CREATE INDEX idx_reservations_car_id ON reservations (car_id);
CREATE INDEX idx_reservations_user_window ON reservations (user_id, start_ts DESC, id);
CREATE INDEX idx_reservations_active_window ON reservations (start_ts, end_ts)
    WHERE status IN ('RESERVED', 'RENTED');
