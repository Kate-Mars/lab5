CREATE EXTENSION IF NOT EXISTS dblink;

--------------------------------------------------
-- SYSTEM ROLES
--------------------------------------------------

DO $$
    BEGIN
        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'admin_role') THEN
            CREATE ROLE admin_role;
        END IF;

        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'guest_role') THEN
            CREATE ROLE guest_role;
        END IF;

        IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'db_admin') THEN
            CREATE ROLE db_admin LOGIN PASSWORD 'admin' CREATEDB CREATEROLE;
        END IF;

        BEGIN
            GRANT admin_role TO db_admin;
        EXCEPTION
            WHEN duplicate_object THEN NULL;
        END;
    END
$$;

--------------------------------------------------
-- CREATE DATABASE
--------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_create_gym_database(p_db_name VARCHAR)
    LANGUAGE plpgsql
AS $$
BEGIN
    IF EXISTS (SELECT FROM pg_database WHERE datname = p_db_name) THEN
        RAISE EXCEPTION 'Database % already exists', p_db_name;
    END IF;

    PERFORM dblink_exec(
            'dbname=postgres user=postgres password=000',
            format('CREATE DATABASE %I', p_db_name)
            );
END
$$;

--------------------------------------------------
-- DROP DATABASE
--------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_drop_gym_database(p_db_name VARCHAR)
    LANGUAGE plpgsql
AS $$
DECLARE
    u RECORD;
    conn TEXT;
BEGIN
    conn := 'dbname=' || p_db_name || ' user=postgres password=000';

    PERFORM dblink_exec(conn, 'REVOKE CREATE ON SCHEMA public FROM PUBLIC');

    BEGIN
        FOR u IN
            SELECT username
            FROM dblink(conn, 'SELECT username FROM managed_db_users')
                     AS t(username TEXT)
            LOOP
                BEGIN
                    EXECUTE format('REVOKE admin_role FROM %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format('REVOKE guest_role FROM %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format('DROP ROLE IF EXISTS %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;
            END LOOP;
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    PERFORM pg_terminate_backend(pid)
    FROM pg_stat_activity
    WHERE datname = p_db_name
      AND pid <> pg_backend_pid();

    PERFORM dblink_exec(
            'dbname=postgres user=postgres password=000',
            format('DROP DATABASE %I', p_db_name)
            );
END
$$;

--------------------------------------------------
-- INIT DATABASE
--------------------------------------------------

CREATE OR REPLACE PROCEDURE sp_init_gym_database(p_db_name VARCHAR)
    LANGUAGE plpgsql
AS $$
DECLARE
    conn TEXT;
    u RECORD;
BEGIN
    conn := 'dbname=' || p_db_name || ' user=postgres password=000';

    PERFORM dblink_exec(conn, 'REVOKE CREATE ON SCHEMA public FROM PUBLIC');

    --------------------------------------------------
    -- CLIENT TABLE
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE TABLE IF NOT EXISTS gym_clients (
                                               client_id SERIAL PRIMARY KEY,
                                               last_name VARCHAR(100),
                                               first_name VARCHAR(100),
                                               phone VARCHAR(30),
                                               email VARCHAR(150),
                                               membership_type VARCHAR(50),
                                               status VARCHAR(20),
                                               registration_date DATE
    )
    ');

    --------------------------------------------------
    -- USERS TABLE
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE TABLE IF NOT EXISTS managed_db_users (
                                                    username VARCHAR PRIMARY KEY,
                                                    role_name VARCHAR,
                                                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    )
    ');

    --------------------------------------------------
    -- CLEAN OLD USERS FOR REINITIALIZED DB
    --------------------------------------------------

    BEGIN
        FOR u IN
            SELECT username
            FROM dblink(conn, 'SELECT username FROM managed_db_users')
                     AS t(username TEXT)
            LOOP
                BEGIN
                    EXECUTE format('REVOKE admin_role FROM %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format('REVOKE guest_role FROM %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format('DROP ROLE IF EXISTS %I', u.username);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;
            END LOOP;
    EXCEPTION
        WHEN OTHERS THEN NULL;
    END;

    PERFORM dblink_exec(conn, 'TRUNCATE TABLE managed_db_users');

    --------------------------------------------------
    -- GRANT PERMISSIONS ON TABLES
    --------------------------------------------------

    -- Сначала убираем широкие права по умолчанию у PUBLIC
    PERFORM dblink_exec(conn, 'REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC');
    PERFORM dblink_exec(conn, 'REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC');

    -- Даем все права администраторам
    PERFORM dblink_exec(conn, 'GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO admin_role');
    PERFORM dblink_exec(conn, 'GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO admin_role');

    -- Гостям даем ТОЛЬКО SELECT (чтение) на таблицу clients
    PERFORM dblink_exec(conn, 'GRANT SELECT ON gym_clients TO guest_role');
    -- НЕ даем гостям права на последовательность, так как они не могут добавлять записи
    -- PERFORM dblink_exec(conn, 'GRANT USAGE ON SEQUENCE gym_clients_client_id_seq TO guest_role'); -- УБРАНО!

    --------------------------------------------------
    -- ADD CLIENT - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE add_client(
        p_last_name VARCHAR,
        p_first_name VARCHAR,
        p_phone VARCHAR,
        p_email VARCHAR,
        p_membership VARCHAR,
        p_status VARCHAR,
        p_date DATE
    )
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    BEGIN
        INSERT INTO gym_clients(
            last_name,
            first_name,
            phone,
            email,
            membership_type,
            status,
            registration_date
        )
        VALUES(
                  p_last_name,
                  p_first_name,
                  p_phone,
                  p_email,
                  p_membership,
                  p_status,
                  p_date
              );
    END
    $b$
    ');

    -- ТОЛЬКО АДМИНЫ могут выполнять add_client
    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE add_client(VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, DATE) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE add_client(VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, DATE) TO admin_role');
    -- Гости НЕ могут выполнять add_client (нет GRANT для guest_role)

    --------------------------------------------------
    -- UPDATE CLIENT - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE update_client(
        p_id INT,
        p_last_name VARCHAR,
        p_first_name VARCHAR,
        p_phone VARCHAR,
        p_email VARCHAR,
        p_membership VARCHAR,
        p_status VARCHAR,
        p_date DATE
    )
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    BEGIN
        UPDATE gym_clients
        SET
            last_name = p_last_name,
            first_name = p_first_name,
            phone = p_phone,
            email = p_email,
            membership_type = p_membership,
            status = p_status,
            registration_date = p_date
        WHERE client_id = p_id;
    END
    $b$
    ');

    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE update_client(INT, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, DATE) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE update_client(INT, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, VARCHAR, DATE) TO admin_role');
    -- Гости НЕ могут выполнять update_client

    --------------------------------------------------
    -- DELETE BY LAST NAME - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE delete_clients_by_last_name(p_last VARCHAR)
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    BEGIN
        DELETE FROM gym_clients
        WHERE LOWER(last_name) LIKE LOWER(''%'' || p_last || ''%'');
    END
    $b$
    ');

    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE delete_clients_by_last_name(VARCHAR) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE delete_clients_by_last_name(VARCHAR) TO admin_role');
    -- Гости НЕ могут выполнять delete_clients_by_last_name

    --------------------------------------------------
    -- CLEAR TABLE - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE clear_clients_table()
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    BEGIN
        DELETE FROM gym_clients;
    END
    $b$
    ');

    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE clear_clients_table() FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE clear_clients_table() TO admin_role');
    -- Гости НЕ могут выполнять clear_clients_table

    --------------------------------------------------
    -- GET ALL CLIENTS - ДЛЯ ВСЕХ (ТОЛЬКО ЧТЕНИЕ)
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE FUNCTION get_all_clients()
        RETURNS TABLE(
                         client_id INT,
                         last_name VARCHAR,
                         first_name VARCHAR,
                         phone VARCHAR,
                         email VARCHAR,
                         membership_type VARCHAR,
                         status VARCHAR,
                         registration_date DATE
                     )
        LANGUAGE SQL
        SECURITY DEFINER
    AS $b$
    SELECT *
    FROM gym_clients
    ORDER BY client_id;
    $b$
    ');

    -- ВСЕ (и админы, и гости) могут просматривать данные
    PERFORM dblink_exec(conn, 'REVOKE ALL ON FUNCTION get_all_clients() FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION get_all_clients() TO admin_role');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION get_all_clients() TO guest_role');

    --------------------------------------------------
    -- SEARCH CLIENT - ДЛЯ ВСЕХ (ТОЛЬКО ЧТЕНИЕ)
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE FUNCTION find_clients_by_last_name(p_last VARCHAR)
        RETURNS TABLE(
                         client_id INT,
                         last_name VARCHAR,
                         first_name VARCHAR,
                         phone VARCHAR,
                         email VARCHAR,
                         membership_type VARCHAR,
                         status VARCHAR,
                         registration_date DATE
                     )
        LANGUAGE SQL
        SECURITY DEFINER
    AS $b$
    SELECT *
    FROM gym_clients
    WHERE LOWER(last_name) LIKE LOWER(''%'' || p_last || ''%'')
    ORDER BY client_id;
    $b$
    ');

    -- ВСЕ (и админы, и гости) могут искать данные
    PERFORM dblink_exec(conn, 'REVOKE ALL ON FUNCTION find_clients_by_last_name(VARCHAR) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION find_clients_by_last_name(VARCHAR) TO admin_role');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION find_clients_by_last_name(VARCHAR) TO guest_role');

    --------------------------------------------------
    -- ADMIN CHECK - ДЛЯ ВСЕХ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE FUNCTION is_admin_user()
        RETURNS BOOLEAN
        LANGUAGE SQL
    AS $b$
    SELECT pg_has_role(session_user, ''admin_role'', ''member'')
               OR session_user IN (''postgres'', ''db_admin'');
    $b$
    ');

    PERFORM dblink_exec(conn, 'REVOKE ALL ON FUNCTION is_admin_user() FROM PUBLIC');
    -- ВСЕ могут проверять, являются ли они админами
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION is_admin_user() TO admin_role');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION is_admin_user() TO guest_role');

    --------------------------------------------------
    -- CREATE USER - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE create_db_user(
        p_username VARCHAR,
        p_password VARCHAR,
        p_role VARCHAR
    )
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    BEGIN
        IF p_role NOT IN (''admin_role'', ''guest_role'') THEN
            RAISE EXCEPTION ''Unsupported role: %'', p_role;
        END IF;

        IF EXISTS (SELECT FROM pg_roles WHERE rolname = p_username) THEN
            BEGIN
                EXECUTE format(''REVOKE admin_role FROM %I'', p_username);
            EXCEPTION
                WHEN OTHERS THEN NULL;
            END;

            BEGIN
                EXECUTE format(''REVOKE guest_role FROM %I'', p_username);
            EXCEPTION
                WHEN OTHERS THEN NULL;
            END;

            BEGIN
                EXECUTE format(''DROP ROLE IF EXISTS %I'', p_username);
            EXCEPTION
                WHEN OTHERS THEN NULL;
            END;
        END IF;

        EXECUTE format(
                ''CREATE USER %I WITH PASSWORD %L'',
                p_username,
                p_password
                );

        EXECUTE format(
                ''GRANT %I TO %I'',
                p_role,
                p_username
                );

        INSERT INTO managed_db_users(username, role_name)
        VALUES(p_username, p_role)
        ON CONFLICT(username)
            DO UPDATE SET role_name = EXCLUDED.role_name;
    END
    $b$
    ');

    -- ТОЛЬКО АДМИНЫ могут создавать пользователей
    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE create_db_user(VARCHAR, VARCHAR, VARCHAR) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE create_db_user(VARCHAR, VARCHAR, VARCHAR) TO admin_role');

    --------------------------------------------------
    -- GET USERS - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE FUNCTION get_managed_db_users()
        RETURNS TABLE(
                         username VARCHAR,
                         role_name VARCHAR,
                         created_at TIMESTAMP
                     )
        LANGUAGE SQL
        SECURITY DEFINER
    AS $b$
    SELECT *
    FROM managed_db_users
    ORDER BY created_at DESC;
    $b$
    ');

    -- ТОЛЬКО АДМИНЫ могут просматривать список пользователей
    PERFORM dblink_exec(conn, 'REVOKE ALL ON FUNCTION get_managed_db_users() FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON FUNCTION get_managed_db_users() TO admin_role');

    --------------------------------------------------
    -- DELETE USERS - ТОЛЬКО ДЛЯ АДМИНОВ
    --------------------------------------------------

    PERFORM dblink_exec(conn, '
    CREATE OR REPLACE PROCEDURE delete_db_users(p_users VARCHAR[])
        LANGUAGE plpgsql
        SECURITY DEFINER
    AS $b$
    DECLARE
        u VARCHAR;
    BEGIN
        FOREACH u IN ARRAY p_users
            LOOP
                BEGIN
                    EXECUTE format(''REVOKE admin_role FROM %I'', u);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format(''REVOKE guest_role FROM %I'', u);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                BEGIN
                    EXECUTE format(''DROP ROLE IF EXISTS %I'', u);
                EXCEPTION
                    WHEN OTHERS THEN NULL;
                END;

                DELETE FROM managed_db_users
                WHERE username = u;
            END LOOP;
    END
    $b$
    ');

    -- ТОЛЬКО АДМИНЫ могут удалять пользователей
    PERFORM dblink_exec(conn, 'REVOKE ALL ON PROCEDURE delete_db_users(VARCHAR[]) FROM PUBLIC');
    PERFORM dblink_exec(conn, 'GRANT EXECUTE ON PROCEDURE delete_db_users(VARCHAR[]) TO admin_role');

    --------------------------------------------------
    -- DEFAULT PRIVILEGES FOR FUTURE OBJECTS
    --------------------------------------------------

    -- Права по умолчанию для таблиц
    PERFORM dblink_exec(conn, 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO admin_role');
    PERFORM dblink_exec(conn, 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO guest_role');

    -- Права по умолчанию для функций (только SELECT-функции для гостей)
    PERFORM dblink_exec(conn, 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO admin_role');
    -- НЕ даем гостям execute на новые функции по умолчанию (только явно разрешенные)
    -- PERFORM dblink_exec(conn, 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT EXECUTE ON FUNCTIONS TO guest_role');

    -- Права по умолчанию для последовательностей
    PERFORM dblink_exec(conn, 'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO admin_role');
    -- Гостям не нужны права на последовательности

END
$$;

--------------------------------------------------
-- GRANTS
--------------------------------------------------

GRANT EXECUTE ON PROCEDURE sp_create_gym_database(VARCHAR) TO db_admin;
GRANT EXECUTE ON PROCEDURE sp_drop_gym_database(VARCHAR) TO db_admin;
GRANT EXECUTE ON PROCEDURE sp_init_gym_database(VARCHAR) TO db_admin;