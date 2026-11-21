CREATE TABLE gift_reservation (
    wish_id uuid PRIMARY KEY REFERENCES wish(id) ON DELETE CASCADE,
    buyer_id uuid NOT NULL REFERENCES app_user(id),
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX gift_reservation_buyer_idx ON gift_reservation (buyer_id);
