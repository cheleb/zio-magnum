CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "users"(
    id serial PRIMARY KEY,
    name text NOT NULL,
    photo bytea,
    myuuid uuid NOT NULL
);

INSERT INTO "users"(name, myuuid)
VALUES
    ('Alice', 'c0ce7a15-c0c2-4a32-8462-33f82764f2f2'),
('Bob', uuid_generate_v4()),
('Charlie', uuid_generate_v4()),
('Diana', uuid_generate_v4()),
('Eve', uuid_generate_v4());

