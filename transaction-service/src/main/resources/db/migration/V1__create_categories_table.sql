CREATE TABLE categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    system_default BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uk_categories_name UNIQUE (name)
);
