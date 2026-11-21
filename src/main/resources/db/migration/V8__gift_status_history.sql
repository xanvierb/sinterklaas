CREATE TABLE gift_status_change (
    id uuid PRIMARY KEY,
    wish_id uuid NOT NULL REFERENCES gift_reservation(wish_id) ON DELETE CASCADE,
    status varchar(16) NOT NULL CHECK (status IN ('RESERVED', 'ORDERED', 'RECEIVED', 'WRAPPED', 'GIVEN')),
    changed_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX gift_status_change_wish_idx ON gift_status_change (wish_id, status, changed_at);
