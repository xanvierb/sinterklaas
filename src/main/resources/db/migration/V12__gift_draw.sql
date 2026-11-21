CREATE TABLE gift_draw_assignment (
    draw_year integer NOT NULL,
    giver_id uuid NOT NULL REFERENCES app_user(id),
    recipient_id uuid NOT NULL REFERENCES app_user(id),
    PRIMARY KEY (draw_year, giver_id),
    UNIQUE (draw_year, recipient_id),
    CHECK (giver_id <> recipient_id)
);
