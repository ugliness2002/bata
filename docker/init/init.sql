CREATE USER bata WITH PASSWORD 'bata';
CREATE DATABASE bata WITH OWNER bata;

\connect bata bata

CREATE TABLE rooms (
    id TEXT PRIMARY KEY,
    area TEXT,
    name TEXT,
    description TEXT,
    exits TEXT,
    last_move_dir TEXT,
    is_indoor BOOLEAN,
    continent TEXT,
    x INTEGER DEFAULT 0,
    y INTEGER DEFAULT 0
);

CREATE TABLE mobs (
    id TEXT,
    long_name TEXT,
    is_aggro INTEGER,
    PRIMARY KEY (id, long_name)
);
