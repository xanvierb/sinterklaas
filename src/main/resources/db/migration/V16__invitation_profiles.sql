ALTER TABLE invitation ADD COLUMN display_name varchar(80);
ALTER TABLE invitation ADD COLUMN bio varchar(500);
ALTER TABLE invitation ADD COLUMN avatar uuid;
ALTER TABLE invitation ADD COLUMN chocolate_flavor varchar(12)
    CHECK (chocolate_flavor IN ('PUUR', 'MELK', 'HAZELNOOT', 'WIT'));
