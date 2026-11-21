ALTER TABLE app_user ADD COLUMN child_profile boolean NOT NULL DEFAULT FALSE;
CREATE TABLE child_parent (
    child_id uuid NOT NULL REFERENCES app_user(id),
    parent_id uuid NOT NULL REFERENCES app_user(id),
    accepted boolean NOT NULL DEFAULT FALSE,
    PRIMARY KEY (child_id, parent_id),
    CHECK (child_id <> parent_id)
);
CREATE INDEX child_parent_parent_idx ON child_parent (parent_id);
CREATE TABLE reservation_message_read (
    message_id uuid NOT NULL REFERENCES reservation_message(id) ON DELETE CASCADE,
    reader_id uuid NOT NULL REFERENCES app_user(id),
    PRIMARY KEY (message_id, reader_id)
);
INSERT INTO reservation_message_read (message_id, reader_id)
SELECT m.id, CASE WHEN m.sender_id = r.buyer_id THEN w.owner_id ELSE r.buyer_id END
FROM reservation_message m JOIN gift_reservation r ON r.wish_id = m.wish_id
JOIN wish w ON w.id = r.wish_id WHERE m.is_read = TRUE;
