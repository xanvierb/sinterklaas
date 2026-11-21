CREATE TABLE celebration_settings (
    id integer PRIMARY KEY CHECK (id = 1),
    celebration_date date
);

INSERT INTO celebration_settings (id) VALUES (1);
