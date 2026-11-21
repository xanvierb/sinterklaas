ALTER TABLE app_user ADD COLUMN chocolate_flavor varchar(12)
    CHECK (chocolate_flavor IN ('PUUR', 'MELK', 'HAZELNOOT', 'WIT'));
