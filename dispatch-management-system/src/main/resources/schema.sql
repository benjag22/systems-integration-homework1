CREATE TABLE IF NOT EXISTS shipment_idempotency
(
    idempotency_key varchar(255) primary key,
    request_hash    varchar(64)  not null,
    shipment_id     int
);
