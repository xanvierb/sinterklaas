CREATE TABLE reservation_message (
    id uuid PRIMARY KEY,
    wish_id uuid NOT NULL REFERENCES gift_reservation(wish_id) ON DELETE CASCADE,
    sender_id uuid NOT NULL REFERENCES app_user(id),
    body varchar(2000) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (length(trim(body)) > 0)
);

CREATE INDEX reservation_message_thread_idx ON reservation_message (wish_id, created_at, id);
