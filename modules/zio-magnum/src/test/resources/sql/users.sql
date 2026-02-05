CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE "users"(
    id serial PRIMARY KEY,
    name text NOT NULL,
    photo bytea,
    myuuid uuid NOT NULL
);

CREATE TABLE projects(
    id serial PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE project_x_user(
    project_id int NOT NULL REFERENCES projects(id),
    user_id int NOT NULL REFERENCES "users"(id),
    PRIMARY KEY (project_id, user_id)
);

INSERT INTO "users"(name, myuuid)
VALUES
    ('Alice', 'c0ce7a15-c0c2-4a32-8462-33f82764f2f2'),
('Bob', uuid_generate_v4()),
('Charlie', uuid_generate_v4()),
('Diana', uuid_generate_v4()),
('Eve', uuid_generate_v4());

INSERT INTO projects(name)
VALUES
    ('Scala'),
('ZIO'),
('Magnum');

INSERT INTO project_x_user(project_id, user_id)
VALUES
    (2, 2),
(2, 3),
(3, 3),
(3, 4),
(2, 5),
(3, 5);

