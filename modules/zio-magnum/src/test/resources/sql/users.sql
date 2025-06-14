CREATE TABLE "users"(
    id serial PRIMARY KEY,
    name text NOT NULL
);

INSERT INTO "users"(name)
    VALUES ('Alice'),
('Bob'),
('Charlie'),
('Diana'),
('Eve');

