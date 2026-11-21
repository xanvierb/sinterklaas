CREATE TABLE wish (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES app_user(id),
    title varchar(120) NOT NULL,
    description varchar(2000) NOT NULL,
    notes varchar(1000) NOT NULL,
    product_url varchar(2048),
    estimated_price numeric(10, 2),
    currency char(3) NOT NULL DEFAULT 'EUR' CHECK (currency = 'EUR'),
    priority varchar(10) NOT NULL CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    variants varchar(500) NOT NULL,
    image_id uuid,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CHECK (estimated_price IS NULL OR estimated_price >= 0)
);

CREATE INDEX wish_owner_updated_idx ON wish (owner_id, updated_at DESC);
