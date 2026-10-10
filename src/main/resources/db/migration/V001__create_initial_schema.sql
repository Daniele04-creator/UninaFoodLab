SET check_function_bodies = false;

CREATE FUNCTION app.add_course_session(p_session_token uuid, p_course_id uuid, p_scheduled_at timestamp without time zone, p_mode text, p_duration_minutes integer, p_online_url text, p_venue text, p_capacity integer, p_attendance_closes_at timestamp without time zone, p_notification_title text, p_notification_message text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE
    chef_id UUID;
    course_status TEXT;
    sequence_value INTEGER;
    session_id UUID;
    venue_value TEXT := btrim(p_venue);
    cutoff TIMESTAMP;
    operation_time TIMESTAMP;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    IF p_scheduled_at IS NULL OR p_duration_minutes NOT BETWEEN 1 AND 720 THEN
        RAISE EXCEPTION 'Session schedule is invalid' USING ERRCODE = '22023';
    END IF;
    SELECT status INTO course_status
      FROM app.course
     WHERE id = p_course_id AND chef_profile_id = chef_id
     FOR UPDATE;
    IF course_status IS NULL THEN
        RAISE EXCEPTION 'Course is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;
    IF course_status <> 'PUBLISHED' THEN
        RAISE EXCEPTION 'Sessions can only be added to published courses' USING ERRCODE = '55000';
    END IF;

    operation_time := clock_timestamp() AT TIME ZONE 'Europe/Rome';
    IF p_scheduled_at <= operation_time THEN
        RAISE EXCEPTION 'Session schedule is invalid' USING ERRCODE = '22023';
    END IF;
    IF p_mode = 'ONLINE' THEN
        IF p_online_url IS NULL OR p_online_url !~* '^https://[^[:space:]]+$'
                OR p_venue IS NOT NULL OR p_capacity IS NOT NULL
                OR p_attendance_closes_at IS NOT NULL THEN
            RAISE EXCEPTION 'Online session details are invalid' USING ERRCODE = '22023';
        END IF;
        cutoff := NULL;
    ELSIF p_mode = 'PRACTICAL' THEN
        cutoff := coalesce(p_attendance_closes_at, p_scheduled_at - INTERVAL '1 minute');
        IF p_online_url IS NOT NULL OR venue_value IS NULL OR venue_value = ''
                OR p_capacity IS NULL OR p_capacity < 1
                OR cutoff <= operation_time OR cutoff >= p_scheduled_at THEN
            RAISE EXCEPTION 'Practical session details are invalid' USING ERRCODE = '22023';
        END IF;
    ELSE
        RAISE EXCEPTION 'Session mode is invalid' USING ERRCODE = '22023';
    END IF;

    SELECT coalesce(max(sequence_number), 0) + 1
      INTO sequence_value
      FROM app.course_session
     WHERE course_id = p_course_id;
    INSERT INTO app.course_session(
        course_id, sequence_number, scheduled_at, mode, status, duration_minutes,
        online_url, venue, capacity, attendance_closes_at)
    VALUES (
        p_course_id, sequence_value, p_scheduled_at, p_mode, 'SCHEDULED',
        p_duration_minutes, p_online_url, venue_value, p_capacity, cutoff)
    RETURNING id INTO session_id;
    UPDATE app.course
       SET session_count = session_count + 1,
           start_date = least(start_date, p_scheduled_at::DATE),
           start_time = CASE
               WHEN p_scheduled_at::DATE < start_date THEN p_scheduled_at::TIME
               WHEN p_scheduled_at::DATE = start_date
                   THEN least(start_time, p_scheduled_at::TIME)
               ELSE start_time
           END
     WHERE id = p_course_id;
    PERFORM app.create_notification_for_enrollees(
        chef_id, p_course_id, 'SESSION_ADDED',
        p_notification_title, p_notification_message);
    RETURN session_id;
END;
$_$;

CREATE FUNCTION app.assign_recipe_to_session(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    PERFORM app.assign_recipe_to_session_for_chef(chef_id, p_session_id, p_recipe_id);
END;
$$;

CREATE FUNCTION app.assign_recipe_to_session_for_chef(p_chef_profile_id uuid, p_session_id uuid, p_recipe_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE lesson app.course_session%ROWTYPE;
BEGIN

    PERFORM id FROM app.recipe WHERE id = p_recipe_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Recipe does not exist' USING ERRCODE = '23503';
    END IF;

    PERFORM i.id FROM app.ingredient i
      JOIN app.recipe_ingredient ri ON ri.ingredient_id = i.id
     WHERE ri.recipe_id = p_recipe_id ORDER BY i.id FOR UPDATE OF i;
    SELECT s.* INTO lesson FROM app.course_session s JOIN app.course c ON c.id = s.course_id
     WHERE s.id = p_session_id AND c.chef_profile_id = p_chef_profile_id FOR UPDATE OF s;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Session is unavailable' USING ERRCODE = '42501';
    END IF;
    IF lesson.mode <> 'PRACTICAL' THEN
        RAISE EXCEPTION 'Recipes require a practical session' USING ERRCODE = '23514';
    END IF;
    IF lesson.status <> 'SCHEDULED'
       OR least(lesson.scheduled_at, lesson.attendance_closes_at)
           <= clock_timestamp() AT TIME ZONE 'Europe/Rome' THEN
        RAISE EXCEPTION 'Menu is closed for this session' USING ERRCODE = '55000';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM app.recipe_ingredient WHERE recipe_id = p_recipe_id) THEN
        RAISE EXCEPTION 'Recipe must contain ingredients' USING ERRCODE = '23514';
    END IF;
    INSERT INTO app.session_recipe(session_id, recipe_id) VALUES (p_session_id, p_recipe_id)
    ON CONFLICT (session_id, recipe_id) DO NOTHING;
END;
$$;

CREATE FUNCTION app.authenticate_user(p_username text, p_derived_key bytea) RETURNS TABLE(session_token uuid, account_id uuid, profile_id uuid, username text, display_name text, role text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE
    matched_account_id UUID;
    matched_username TEXT;
    matched_role TEXT;
    matched_profile_id UUID;
    matched_display_name TEXT;
    expected_key BYTEA;
    generated_token UUID;
BEGIN
    SELECT account.id,
           account.username::TEXT,
           account.role::TEXT,
           COALESCE(chef_profile.id, participant_profile.id),
           COALESCE(chef_profile.display_name, participant_profile.display_name),
           decode(
               split_part(account.password_hash, '$', 5)
                   || repeat(
                       '=',
                       (4 - length(split_part(account.password_hash, '$', 5)) % 4) % 4
                   ),
               'base64'
           )
      INTO matched_account_id, matched_username, matched_role,
           matched_profile_id, matched_display_name, expected_key
      FROM app.account
      LEFT JOIN app.chef_profile
        ON chef_profile.account_id = account.id AND account.role = 'CHEF'
      LEFT JOIN app.participant_profile
        ON participant_profile.account_id = account.id AND account.role = 'PARTICIPANT'
     WHERE account.username = lower(btrim(p_username))
       AND account.active;

    IF matched_account_id IS NULL
            OR matched_profile_id IS NULL
            OR p_derived_key IS NULL
            OR expected_key <> p_derived_key THEN
        RETURN;
    END IF;

    generated_token := gen_random_uuid();
    DELETE FROM app.auth_session
     WHERE auth_session.account_id = matched_account_id
       AND auth_session.expires_at <= clock_timestamp();
    INSERT INTO app.auth_session(token_hash, account_id, expires_at)
    VALUES (
        sha256(convert_to(generated_token::TEXT, 'UTF8')),
        matched_account_id,
        clock_timestamp() + INTERVAL '12 hours'
    );

    RETURN QUERY SELECT
        generated_token,
        matched_account_id,
        matched_profile_id,
        matched_username,
        matched_display_name,
        matched_role;
END;
$_$;

CREATE FUNCTION app.authenticated_profile_id(p_session_token uuid, p_required_role text) RETURNS uuid
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    SELECT CASE account.role
               WHEN 'CHEF' THEN chef_profile.id
               WHEN 'PARTICIPANT' THEN participant_profile.id
           END
      FROM app.auth_session
      JOIN app.account ON account.id = auth_session.account_id
      LEFT JOIN app.chef_profile ON chef_profile.account_id = account.id
      LEFT JOIN app.participant_profile ON participant_profile.account_id = account.id
     WHERE auth_session.token_hash = sha256(convert_to(p_session_token::TEXT, 'UTF8'))
       AND auth_session.expires_at > clock_timestamp()
       AND account.active
       AND account.role = p_required_role
$$;

CREATE FUNCTION app.cancel_course_session(p_session_token uuid, p_session_id uuid, p_notification_title text, p_notification_message text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
    owned_course_id UUID;
    course_status TEXT;
    lesson app.course_session%ROWTYPE;
    notice UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    SELECT course.id, course.status
      INTO owned_course_id, course_status
      FROM app.course_session session
      JOIN app.course course ON course.id = session.course_id
     WHERE session.id = p_session_id AND course.chef_profile_id = chef_id
     FOR UPDATE OF course;
    IF owned_course_id IS NULL THEN
        RAISE EXCEPTION 'Session is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;
    PERFORM enrollment.id
      FROM app.enrollment enrollment
     WHERE enrollment.course_id = owned_course_id
     ORDER BY enrollment.id
     FOR UPDATE;
    SELECT * INTO lesson
      FROM app.course_session
     WHERE id = p_session_id
     FOR UPDATE;

    IF course_status <> 'PUBLISHED' OR app.effective_session_status(
        lesson.status::TEXT,lesson.scheduled_at,lesson.duration_minutes,
        clock_timestamp() AT TIME ZONE 'Europe/Rome') <> 'SCHEDULED' THEN
        RAISE EXCEPTION 'Only scheduled sessions of published courses can be cancelled'
            USING ERRCODE = '55000';
    END IF;

    UPDATE app.course_session SET status = 'CANCELLED' WHERE id = p_session_id;
    UPDATE app.practical_attendance
       SET status = 'DECLINED', updated_at = clock_timestamp()
     WHERE session_id = p_session_id AND status = 'CONFIRMED';
    UPDATE app.course
       SET start_date = schedule.first_time::DATE,
           start_time = schedule.first_time::TIME
      FROM (
          SELECT min(session.scheduled_at) AS first_time
            FROM app.course_session session
           WHERE session.course_id = owned_course_id
             AND session.status <> 'CANCELLED'
      ) schedule
     WHERE id = owned_course_id AND schedule.first_time IS NOT NULL;

    notice := app.create_notification_for_enrollees(
        chef_id, owned_course_id, 'SESSION_CANCELLED',
        p_notification_title, p_notification_message);
    RETURN notice;
END;
$$;

CREATE FUNCTION app.capture_session_recipe() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    INSERT INTO app.session_menu_snapshot(session_id, recipe_id, recipe_name, description, ingredients, provenance)
    SELECT NEW.session_id, r.id, r.name, r.description,
        coalesce((SELECT jsonb_agg(jsonb_build_object('ingredient_id', i.id,
            'ingredient_name', i.name, 'unit', i.unit, 'quantity', ri.quantity) ORDER BY i.id)
            FROM app.recipe_ingredient ri JOIN app.ingredient i ON i.id=ri.ingredient_id
            WHERE ri.recipe_id=r.id), '[]'::jsonb), 'CAPTURED'
    FROM app.recipe r WHERE r.id=NEW.recipe_id;
    RETURN NEW;
END; $$;

CREATE FUNCTION app.catalog_recipe(p_token uuid, p_recipe uuid) RETURNS TABLE(recipe_id uuid, category_id uuid, category_name text, name text, description text, base_servings integer, ingredient_count bigint, locked boolean)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(app.authenticated_profile_id(p_token, 'CHEF'), app.authenticated_profile_id(p_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT r.id, r.category_id, category.name::TEXT, r.name::TEXT, r.description, r.base_servings,
           (SELECT count(*) FROM app.recipe_ingredient dose WHERE dose.recipe_id = r.id), false
      FROM app.recipe r JOIN app.category category ON category.id = r.category_id
     WHERE r.id = p_recipe;
END;
$$;

CREATE FUNCTION app.catalog_recipe_counts(p_token uuid, p_category uuid, p_search text, p_menu boolean) RETURNS TABLE(category_name text, recipe_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(app.authenticated_profile_id(p_token, 'CHEF'), app.authenticated_profile_id(p_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    IF p_search IS NULL OR p_menu IS NULL THEN
        RAISE EXCEPTION 'Search required' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY
    SELECT category.name::TEXT, count(*)
      FROM app.recipe r JOIN app.category category ON category.id = r.category_id
     WHERE (p_category IS NULL OR r.category_id = p_category)
       AND ((NOT p_menu AND strpos(lower(r.name || ' ' || category.name || ' ' || r.description), lower(btrim(p_search))) > 0)
                OR (p_menu AND (strpos(lower(r.name), lower(btrim(p_search))) > 0
                            OR strpos(lower(category.name), lower(btrim(p_search))) > 0)))
     GROUP BY category.name ORDER BY category.name;
END;
$$;

CREATE FUNCTION app.change_account_password(p_session_token uuid, p_current_proof bytea, p_new_password_hash text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE
    current_account_id UUID;
    expected_key BYTEA;
    current_token_hash BYTEA;
BEGIN
    current_token_hash := sha256(convert_to(p_session_token::TEXT, 'UTF8'));
    SELECT account.id,
           decode(split_part(account.password_hash, '$', 5)
               || repeat('=', (4 - length(split_part(account.password_hash, '$', 5)) % 4) % 4), 'base64')
      INTO current_account_id, expected_key
      FROM app.auth_session
      JOIN app.account ON account.id = auth_session.account_id
     WHERE auth_session.token_hash = current_token_hash
       AND auth_session.expires_at > clock_timestamp()
       AND account.active
     FOR UPDATE OF account;
    IF current_account_id IS NULL OR p_current_proof IS NULL OR expected_key <> p_current_proof
       OR p_new_password_hash !~ '^\$pbkdf2-sha256\$600000\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$' THEN
        RAISE EXCEPTION 'Password update unavailable' USING ERRCODE = '42501';
    END IF;

    UPDATE app.account SET password_hash = p_new_password_hash WHERE id = current_account_id;
    DELETE FROM app.auth_session WHERE account_id = current_account_id;
END;
$_$;

CREATE FUNCTION app.chef_course_sessions_batch(p_token uuid, p_ids uuid[]) RETURNS TABLE(course_id uuid, session_id uuid, sequence_number integer, scheduled_at timestamp without time zone, mode text, status text, duration_minutes integer, online_url text, venue text, capacity integer, attendance_closes_at timestamp without time zone, completed_at timestamp without time zone, recipe_ids uuid[], recipe_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF app.authenticated_profile_id(p_token, 'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT requested.id, session.*
      FROM unnest(p_ids) requested(id)
      LEFT JOIN LATERAL app.list_course_sessions(p_token, requested.id) session ON true
     ORDER BY requested.id, session.sequence_number;
END;
$$;

CREATE FUNCTION app.chef_monthly_report(p_session_token uuid, p_year integer, p_month integer) RETURNS TABLE(total_courses bigint, online_sessions bigint, practical_sessions bigint, average_recipes numeric, minimum_recipes integer, maximum_recipes integer)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    SELECT count(DISTINCT s.course_id),count(*) FILTER(WHERE d.mode='ONLINE'),count(*) FILTER(WHERE d.mode='PRACTICAL'),
        avg(d.recipes) FILTER(WHERE d.mode='PRACTICAL'),min(d.recipes) FILTER(WHERE d.mode='PRACTICAL'),max(d.recipes) FILTER(WHERE d.mode='PRACTICAL')
    FROM app.chef_monthly_report_details(p_session_token,p_year,p_month) d JOIN app.course_session s ON s.id=d.session_id
    WHERE d.state='COMPLETED';
$$;

CREATE FUNCTION app.chef_monthly_report_details(p_token uuid, p_year integer, p_month integer) RETURNS TABLE(session_id uuid, course_title text, scheduled_at timestamp without time zone, mode text, state text, recipes integer, attendees integer)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef UUID; first_day DATE;
BEGIN
    chef:=app.authenticated_profile_id(p_token,'CHEF');
    IF chef IS NULL THEN RAISE EXCEPTION 'Chef required' USING ERRCODE='42501'; END IF;
    IF p_year IS NULL OR p_month IS NULL OR p_year NOT BETWEEN 2000 AND 2200 OR p_month NOT BETWEEN 1 AND 12 THEN
        RAISE EXCEPTION 'Report month is invalid' USING ERRCODE='22023';
    END IF;
    first_day:=make_date(p_year,p_month,1);
    RETURN QUERY SELECT s.id,c.title::TEXT,s.scheduled_at,s.mode::TEXT,
        app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome'),
        CASE WHEN app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome')='COMPLETED'
            THEN CASE WHEN s.mode='PRACTICAL'
                THEN (SELECT count(*)::INTEGER FROM app.session_menu_snapshot m WHERE m.session_id=s.id)
                ELSE 0 END ELSE NULL END,
        NULL::INTEGER
    FROM app.course_session s JOIN app.course c ON c.id=s.course_id
    WHERE c.chef_profile_id=chef AND s.scheduled_at>=first_day AND s.scheduled_at<first_day+interval '1 month'
    ORDER BY s.scheduled_at,c.title,s.id;
END; $$;

CREATE FUNCTION app.count_notification_recipients(p_session_token uuid, p_course_id uuid) RETURNS bigint
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    IF p_course_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM app.course
         WHERE course.id = p_course_id AND course.chef_profile_id = chef_id
    ) THEN
        RAISE EXCEPTION 'Course is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;
    RETURN (
        SELECT count(DISTINCT enrollment.participant_profile_id)
          FROM app.enrollment
          JOIN app.course ON course.id = enrollment.course_id
         WHERE enrollment.status = 'ACTIVE'
           AND course.chef_profile_id = chef_id
           AND (p_course_id IS NULL OR course.id = p_course_id)
    );
END;
$$;

CREATE FUNCTION app.create_category(p_session_token uuid, p_name text, p_description text) RETURNS TABLE(category_id uuid, name text, description text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    normalized_name TEXT := btrim(p_name);
    normalized_description TEXT := btrim(coalesce(p_description, ''));
BEGIN
    IF app.authenticated_profile_id(p_session_token, 'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    IF normalized_name IS NULL
       OR char_length(normalized_name) NOT BETWEEN 3 AND 80
       OR char_length(normalized_description) > 500 THEN
        RAISE EXCEPTION 'Invalid category input' USING ERRCODE = '22023';
    END IF;

    RETURN QUERY
    INSERT INTO app.category(name, description)
         VALUES (normalized_name, normalized_description)
      RETURNING app.category.id, app.category.name::TEXT, app.category.description::TEXT;
END;
$$;

CREATE FUNCTION app.create_course(p_chef_profile_id uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_session_modes text[]) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    new_course_id UUID;
    session_index INTEGER;
    normalized_title TEXT;
    normalized_description TEXT;
BEGIN
    normalized_title := btrim(p_title);
    normalized_description := btrim(p_description);

    IF p_chef_profile_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM app.chef_profile WHERE id = p_chef_profile_id) THEN
        RAISE EXCEPTION 'Chef profile does not exist' USING ERRCODE = '23503';
    END IF;
    IF p_category_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM app.category WHERE id = p_category_id) THEN
        RAISE EXCEPTION 'Category does not exist' USING ERRCODE = '23503';
    END IF;
    IF normalized_title IS NULL OR char_length(normalized_title) NOT BETWEEN 3 AND 120 THEN
        RAISE EXCEPTION 'Course title is invalid' USING ERRCODE = '22023';
    END IF;
    IF normalized_description IS NULL OR normalized_description = '' THEN
        RAISE EXCEPTION 'Course description is invalid' USING ERRCODE = '22023';
    END IF;
    IF p_start_date IS NULL OR p_start_time IS NULL THEN
        RAISE EXCEPTION 'Course start date and time are required' USING ERRCODE = '22023';
    END IF;
    IF p_recurrence_days IS NULL OR p_recurrence_days NOT BETWEEN 1 AND 31 THEN
        RAISE EXCEPTION 'Course recurrence is invalid' USING ERRCODE = '22023';
    END IF;
    IF p_session_modes IS NULL OR cardinality(p_session_modes) NOT BETWEEN 1 AND 30 THEN
        RAISE EXCEPTION 'Course sessions are invalid' USING ERRCODE = '22023';
    END IF;
    IF EXISTS (
        SELECT 1
          FROM unnest(p_session_modes) AS session_mode
         WHERE session_mode IS NULL OR session_mode NOT IN ('ONLINE', 'PRACTICAL')
    ) THEN
        RAISE EXCEPTION 'Course session mode is invalid' USING ERRCODE = '22023';
    END IF;

    INSERT INTO app.course (
        chef_profile_id,
        category_id,
        title,
        description,
        start_date,
        start_time,
        recurrence_days,
        session_count,
        status
    ) VALUES (
        p_chef_profile_id,
        p_category_id,
        normalized_title,
        normalized_description,
        p_start_date,
        p_start_time,
        p_recurrence_days,
        cardinality(p_session_modes),
        'DRAFT'
    ) RETURNING id INTO new_course_id;

    FOR session_index IN 1..cardinality(p_session_modes) LOOP
        INSERT INTO app.course_session (
            course_id,
            sequence_number,
            scheduled_at,
            mode,
            status
        ) VALUES (
            new_course_id,
            session_index,
            p_start_date + p_start_time
                + ((session_index - 1) * p_recurrence_days) * INTERVAL '1 day',
            p_session_modes[session_index],
            'SCHEDULED'
        );
    END LOOP;

    RETURN new_course_id;
END;
$$;

CREATE FUNCTION app.create_course(p_session_token uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_ingredient_margin numeric, p_session_modes text[], p_duration_minutes integer[], p_online_urls text[], p_venues text[], p_capacities integer[]) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    IF p_session_modes IS NULL
       OR p_duration_minutes IS NULL
       OR p_online_urls IS NULL
       OR p_venues IS NULL
       OR p_capacities IS NULL
       OR cardinality(p_session_modes) NOT BETWEEN 2 AND 30
       OR cardinality(p_session_modes) IS DISTINCT FROM cardinality(p_duration_minutes)
       OR cardinality(p_session_modes) IS DISTINCT FROM cardinality(p_online_urls)
       OR cardinality(p_session_modes) IS DISTINCT FROM cardinality(p_venues)
       OR cardinality(p_session_modes) IS DISTINCT FROM cardinality(p_capacities) THEN
        RAISE EXCEPTION 'A new course requires between two and thirty sessions'
            USING ERRCODE = '22023';
    END IF;
    IF EXISTS (
        SELECT 1 FROM unnest(p_session_modes, p_online_urls) AS details(mode, online_url)
         WHERE details.mode = 'ONLINE'
           AND details.online_url !~* '^https://[[:alnum:]][[:alnum:].-]*(:[0-9]+)?([/?#][^[:space:]]*)?$'
    ) THEN
        RAISE EXCEPTION 'Online session URL must contain a valid HTTPS host'
            USING ERRCODE = '22023';
    END IF;
    RETURN app.create_course_for_chef(
        chef_id, p_category_id, p_title, p_description, p_start_date, p_start_time,
        p_recurrence_days, p_ingredient_margin, p_session_modes, p_duration_minutes,
        p_online_urls, p_venues, p_capacities);
END;
$_$;

CREATE FUNCTION app.create_course_for_chef(p_chef_profile_id uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_ingredient_margin numeric, p_session_modes text[], p_duration_minutes integer[], p_online_urls text[], p_venues text[], p_capacities integer[]) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE
    new_course_id UUID;
    session_index INTEGER;
    session_count INTEGER;
    normalized_title TEXT;
    normalized_description TEXT;
    normalized_venue TEXT;
BEGIN
    normalized_title := btrim(p_title);
    normalized_description := btrim(p_description);
    session_count := cardinality(p_session_modes);

    IF p_chef_profile_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM app.chef_profile WHERE id = p_chef_profile_id) THEN
        RAISE EXCEPTION 'Chef profile does not exist' USING ERRCODE = '23503';
    END IF;
    IF p_category_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM app.category WHERE id = p_category_id) THEN
        RAISE EXCEPTION 'Category does not exist' USING ERRCODE = '23503';
    END IF;
    IF normalized_title IS NULL OR char_length(normalized_title) NOT BETWEEN 3 AND 120 THEN
        RAISE EXCEPTION 'Course title is invalid' USING ERRCODE = '22023';
    END IF;
    IF normalized_description IS NULL OR normalized_description = '' THEN
        RAISE EXCEPTION 'Course description is invalid' USING ERRCODE = '22023';
    END IF;
    IF p_start_date IS NULL OR p_start_time IS NULL THEN
        RAISE EXCEPTION 'Course start date and time are required' USING ERRCODE = '22023';
    END IF;
    IF p_recurrence_days IS NULL OR p_recurrence_days NOT BETWEEN 1 AND 31 THEN
        RAISE EXCEPTION 'Course recurrence is invalid' USING ERRCODE = '22023';
    END IF;
    IF p_ingredient_margin IS NULL OR p_ingredient_margin NOT BETWEEN 0 AND 25 THEN
        RAISE EXCEPTION 'Ingredient margin is invalid' USING ERRCODE = '22023';
    END IF;
    IF session_count IS NULL OR session_count NOT BETWEEN 1 AND 30
       OR cardinality(p_duration_minutes) <> session_count
       OR cardinality(p_online_urls) <> session_count
       OR cardinality(p_venues) <> session_count
       OR cardinality(p_capacities) <> session_count THEN
        RAISE EXCEPTION 'Course session details are invalid' USING ERRCODE = '22023';
    END IF;

    FOR session_index IN 1..session_count LOOP
        normalized_venue := btrim(p_venues[session_index]);
        IF p_duration_minutes[session_index] IS NULL
           OR p_duration_minutes[session_index] NOT BETWEEN 1 AND 720 THEN
            RAISE EXCEPTION 'Session duration is invalid' USING ERRCODE = '22023';
        END IF;
        IF p_session_modes[session_index] = 'ONLINE' THEN
            IF p_online_urls[session_index] IS NULL
               OR p_online_urls[session_index] !~* '^https://[^[:space:]]+$'
               OR p_venues[session_index] IS NOT NULL
               OR p_capacities[session_index] IS NOT NULL THEN
                RAISE EXCEPTION 'Online session details are invalid' USING ERRCODE = '22023';
            END IF;
        ELSIF p_session_modes[session_index] = 'PRACTICAL' THEN
            IF p_online_urls[session_index] IS NOT NULL
               OR normalized_venue IS NULL
               OR normalized_venue = ''
               OR p_capacities[session_index] IS NULL
               OR p_capacities[session_index] < 1 THEN
                RAISE EXCEPTION 'Practical session details are invalid' USING ERRCODE = '22023';
            END IF;
        ELSE
            RAISE EXCEPTION 'Course session mode is invalid' USING ERRCODE = '22023';
        END IF;
    END LOOP;

    INSERT INTO app.course (
        chef_profile_id,
        category_id,
        title,
        description,
        start_date,
        start_time,
        recurrence_days,
        session_count,
        status,
        ingredient_margin
    ) VALUES (
        p_chef_profile_id,
        p_category_id,
        normalized_title,
        normalized_description,
        p_start_date,
        p_start_time,
        p_recurrence_days,
        session_count,
        'DRAFT',
        p_ingredient_margin
    ) RETURNING id INTO new_course_id;

    FOR session_index IN 1..session_count LOOP
        INSERT INTO app.course_session (
            course_id,
            sequence_number,
            scheduled_at,
            mode,
            status,
            duration_minutes,
            online_url,
            venue,
            capacity
        ) VALUES (
            new_course_id,
            session_index,
            p_start_date + p_start_time
                + ((session_index - 1) * p_recurrence_days) * INTERVAL '1 day',
            p_session_modes[session_index],
            'SCHEDULED',
            p_duration_minutes[session_index],
            p_online_urls[session_index],
            btrim(p_venues[session_index]),
            p_capacities[session_index]
        );
    END LOOP;

    RETURN new_course_id;
END;
$_$;

CREATE FUNCTION app.create_notification_for_enrollees(p_chef_profile_id uuid, p_course_id uuid, p_reason text, p_title text, p_message text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    notification_identifier UUID;
    normalized_title TEXT := btrim(p_title);
    normalized_message TEXT := btrim(p_message);
BEGIN
    IF p_chef_profile_id IS NULL
            OR NOT EXISTS (
                SELECT 1
                  FROM app.chef_profile
                 WHERE chef_profile.id = p_chef_profile_id
            ) THEN
        RAISE EXCEPTION 'Chef profile is unavailable' USING ERRCODE = '42501';
    END IF;
    IF p_course_id IS NOT NULL
            AND NOT EXISTS (
                SELECT 1
                  FROM app.course
                 WHERE course.id = p_course_id
                   AND course.chef_profile_id = p_chef_profile_id
            ) THEN
        RAISE EXCEPTION 'Course is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;
    IF p_reason IS NULL
            OR p_reason NOT IN ('SCHEDULE_CHANGED', 'SESSION_CANCELLED', 'SESSION_ADDED', 'GENERAL')
            OR normalized_title IS NULL
            OR char_length(normalized_title) NOT BETWEEN 3 AND 120
            OR normalized_message IS NULL
            OR char_length(normalized_message) NOT BETWEEN 3 AND 2000 THEN
        RAISE EXCEPTION 'Notification details are invalid' USING ERRCODE = '22023';
    END IF;

    INSERT INTO app.notification(chef_profile_id, course_id, title, message, reason)
    VALUES (
        p_chef_profile_id,
        p_course_id,
        normalized_title,
        normalized_message,
        p_reason
    )
    RETURNING id INTO notification_identifier;

    INSERT INTO app.notification_recipient(notification_id, participant_profile_id)
    SELECT notification_identifier,
           enrollment.participant_profile_id
      FROM app.enrollment
      JOIN app.course ON course.id = enrollment.course_id
     WHERE enrollment.status = 'ACTIVE'
       AND course.chef_profile_id = p_chef_profile_id
       AND (p_course_id IS NULL OR course.id = p_course_id)
     GROUP BY enrollment.participant_profile_id
    ON CONFLICT DO NOTHING;

    RETURN notification_identifier;
END;
$$;

CREATE FUNCTION app.default_practical_attendance_cutoff() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NEW.mode = 'PRACTICAL' AND (
            NEW.attendance_closes_at IS NULL
            OR (TG_OP = 'UPDATE'
                AND NEW.scheduled_at IS DISTINCT FROM OLD.scheduled_at
                AND NEW.attendance_closes_at IS NOT DISTINCT FROM OLD.attendance_closes_at)
        ) THEN
        NEW.attendance_closes_at := NEW.scheduled_at - INTERVAL '1 minute';
    ELSIF NEW.mode = 'ONLINE' THEN
        NEW.attendance_closes_at := NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.disable_account(p_username text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE target_account_id UUID;
DECLARE target_role TEXT;
BEGIN
    SELECT id, role INTO target_account_id, target_role FROM app.account
     WHERE username = lower(btrim(p_username)) FOR UPDATE;
    IF target_account_id IS NULL THEN
        RAISE EXCEPTION 'Account unavailable' USING ERRCODE = '42501';
    END IF;
    IF target_role = 'CHEF' AND EXISTS (
        SELECT 1 FROM app.course c JOIN app.course_session s ON s.course_id = c.id
         WHERE c.chef_profile_id = (SELECT id FROM app.chef_profile WHERE account_id = target_account_id)
           AND s.status = 'SCHEDULED'
           AND s.scheduled_at >= (clock_timestamp() AT TIME ZONE 'Europe/Rome')
    ) THEN
        RAISE EXCEPTION 'Chef has pending sessions' USING ERRCODE = '55000';
    END IF;
    UPDATE app.account SET active = false WHERE id = target_account_id;
    DELETE FROM app.auth_session WHERE account_id = target_account_id;
END;
$$;

CREATE FUNCTION app.effective_session_status(p_status text, p_scheduled_at timestamp without time zone, p_duration_minutes integer, p_as_of timestamp without time zone) RETURNS text
    LANGUAGE sql IMMUTABLE STRICT
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    SELECT CASE WHEN p_status='CANCELLED' THEN 'CANCELLED'
        WHEN p_scheduled_at+make_interval(mins=>p_duration_minutes)<=p_as_of THEN 'COMPLETED'
        ELSE 'SCHEDULED' END;
$$;

CREATE FUNCTION app.enforce_account_profile_role() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NEW.role = 'CHEF'
       AND EXISTS (SELECT 1 FROM app.participant_profile WHERE account_id = NEW.id) THEN
        RAISE EXCEPTION 'Account role is not compatible with existing profile'
            USING ERRCODE = '23514';
    END IF;
    IF NEW.role = 'PARTICIPANT'
       AND EXISTS (SELECT 1 FROM app.chef_profile WHERE account_id = NEW.id) THEN
        RAISE EXCEPTION 'Account role is not compatible with existing profile'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.enforce_practical_attendance() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    enrollment_course_id UUID;
    enrollment_status TEXT;
    session_course_id UUID;
    session_mode TEXT;
    session_status TEXT;
    session_time TIMESTAMP;
    session_capacity INTEGER;
    confirmed_count INTEGER;
BEGIN
    SELECT enrollment.course_id,
           enrollment.status,
           course_session.course_id,
           course_session.mode,
           course_session.status,
           course_session.scheduled_at,
           course_session.capacity
      INTO enrollment_course_id,
           enrollment_status,
           session_course_id,
           session_mode,
           session_status,
           session_time,
           session_capacity
      FROM app.enrollment
      JOIN app.course_session ON course_session.id = NEW.session_id
     WHERE enrollment.id = NEW.enrollment_id
     FOR UPDATE OF enrollment, course_session;

    IF enrollment_course_id IS NULL THEN
        RAISE EXCEPTION 'Enrollment or session does not exist'
            USING ERRCODE = '23503';
    END IF;
    IF enrollment_course_id <> session_course_id OR session_mode <> 'PRACTICAL' THEN
        RAISE EXCEPTION 'Attendance requires a practical session from the enrolled course'
            USING ERRCODE = '23514';
    END IF;

    IF NEW.status = 'CONFIRMED' THEN
        IF enrollment_status <> 'ACTIVE'
                OR session_status <> 'SCHEDULED'
                OR session_time <= (current_timestamp AT TIME ZONE 'Europe/Rome') THEN
            RAISE EXCEPTION 'Attendance requires an active enrollment and a future session'
                USING ERRCODE = '23514';
        END IF;

        SELECT count(*)::INTEGER
          INTO confirmed_count
          FROM app.practical_attendance
          JOIN app.enrollment current_enrollment ON current_enrollment.id = practical_attendance.enrollment_id
         WHERE practical_attendance.session_id = NEW.session_id
           AND practical_attendance.status = 'CONFIRMED'
           AND current_enrollment.status = 'ACTIVE'
           AND practical_attendance.updated_at >= current_enrollment.enrolled_at
           AND practical_attendance.enrollment_id <> NEW.enrollment_id;
        IF confirmed_count >= session_capacity THEN
            RAISE EXCEPTION 'Practical session capacity has been reached'
                USING ERRCODE = '23514';
        END IF;
    END IF;

    NEW.updated_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.enforce_practical_session_recipe() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    session_mode TEXT;
BEGIN
    SELECT course_session.mode
      INTO session_mode
      FROM app.course_session
     WHERE course_session.id = NEW.session_id;

    IF session_mode IS NOT NULL AND session_mode <> 'PRACTICAL' THEN
        RAISE EXCEPTION 'Recipes can only be assigned to practical sessions'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.enforce_profile_role() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    account_role TEXT;
    expected_role TEXT;
BEGIN
    expected_role := CASE TG_TABLE_NAME
        WHEN 'chef_profile' THEN 'CHEF'
        WHEN 'participant_profile' THEN 'PARTICIPANT'
        ELSE NULL
    END;

    SELECT account.role
      INTO account_role
      FROM app.account
     WHERE account.id = NEW.account_id;

    IF account_role IS NULL THEN
        RAISE EXCEPTION 'Referenced account does not exist'
            USING ERRCODE = '23503';
    END IF;
    IF account_role <> expected_role THEN
        RAISE EXCEPTION 'Account role is not compatible with profile type'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.enroll_in_course(p_session_token uuid, p_course_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    participant_id UUID;
    course_status TEXT;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required'
            USING ERRCODE = '42501';
    END IF;

    SELECT course.status
      INTO course_status
      FROM app.course
     WHERE course.id = p_course_id
     FOR UPDATE;
    IF course_status IS NULL THEN
        RAISE EXCEPTION 'Course is unavailable' USING ERRCODE = '42501';
    END IF;
    IF course_status <> 'PUBLISHED'
            OR NOT EXISTS (
                SELECT 1
                  FROM app.course_session
                 WHERE course_session.course_id = p_course_id
                   AND course_session.status = 'SCHEDULED'
                   AND course_session.scheduled_at
                       > (current_timestamp AT TIME ZONE 'Europe/Rome')
            ) THEN
        RAISE EXCEPTION 'Course is not open for enrollment'
            USING ERRCODE = '55000';
    END IF;

    INSERT INTO app.enrollment(
        participant_profile_id, course_id, status, enrolled_at, withdrawn_at)
    VALUES (participant_id, p_course_id, 'ACTIVE', clock_timestamp(), NULL)
    ON CONFLICT (participant_profile_id, course_id) DO UPDATE
       SET status = 'ACTIVE',
           enrolled_at = CASE
               WHEN enrollment.status = 'WITHDRAWN' THEN clock_timestamp()
               ELSE enrollment.enrolled_at
           END,
           withdrawn_at = NULL;
END;
$$;

CREATE FUNCTION app.get_password_parameters(p_username text) RETURNS TABLE(iterations integer, salt bytea)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE
    encoded_hash TEXT;
BEGIN
    SELECT account.password_hash
      INTO encoded_hash
      FROM app.account
     WHERE account.username = lower(btrim(p_username))
       AND account.active
       AND (
           (account.role = 'CHEF' AND EXISTS (
               SELECT 1 FROM app.chef_profile WHERE chef_profile.account_id = account.id
           ))
           OR
           (account.role = 'PARTICIPANT' AND EXISTS (
               SELECT 1 FROM app.participant_profile
                WHERE participant_profile.account_id = account.id
           ))
       );

    IF encoded_hash IS NULL THEN
        encoded_hash := '$pbkdf2-sha256$600000$bKWdQKkig4RrI5Bk+sXkxA$gp4k3uJpvLuxD5c2oia3mupqE/14da2J5YQLKYmDJ9A';
    END IF;

    iterations := split_part(encoded_hash, '$', 3)::INTEGER;
    salt := decode(
        split_part(encoded_hash, '$', 4)
            || repeat('=', (4 - length(split_part(encoded_hash, '$', 4)) % 4) % 4),
        'base64'
    );
    RETURN NEXT;
END;
$_$;

CREATE FUNCTION app.list_catalog_categories(p_session_token uuid) RETURNS TABLE(category_id uuid, name text, description text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(
            app.authenticated_profile_id(p_session_token, 'CHEF'),
            app.authenticated_profile_id(p_session_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT category.id, category.name::TEXT, category.description::TEXT
      FROM app.category
     ORDER BY lower(category.name), category.id;
END;
$$;

CREATE FUNCTION app.list_catalog_recipes(p_session_token uuid, p_category_id uuid) RETURNS TABLE(recipe_id uuid, category_id uuid, category_name text, name text, description text, base_servings integer, ingredient_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(
            app.authenticated_profile_id(p_session_token, 'CHEF'),
            app.authenticated_profile_id(p_session_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT recipe.id,
           recipe.category_id,
           category.name::TEXT,
           recipe.name::TEXT,
           recipe.description::TEXT,
           recipe.base_servings,
           count(recipe_ingredient.ingredient_id)
      FROM app.recipe
      JOIN app.category ON category.id = recipe.category_id
      LEFT JOIN app.recipe_ingredient ON recipe_ingredient.recipe_id = recipe.id
     WHERE p_category_id IS NULL OR recipe.category_id = p_category_id
     GROUP BY recipe.id, category.name
     ORDER BY lower(recipe.name), recipe.id;
END;
$$;

CREATE FUNCTION app.list_catalog_recipes_with_usage(p_token uuid, p_category uuid) RETURNS TABLE(recipe_id uuid, category_id uuid, category_name text, name text, description text, base_servings integer, ingredient_count bigint, locked boolean)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(
            app.authenticated_profile_id(p_token, 'CHEF'),
            app.authenticated_profile_id(p_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT r.recipe_id, r.category_id, r.category_name, r.name, r.description,
           r.base_servings, r.ingredient_count,
           false
      FROM app.list_catalog_recipes(p_token, p_category) r
     ORDER BY lower(r.name), r.recipe_id;
END;
$$;

CREATE FUNCTION app.list_chef_courses(p_session_token uuid, p_category_id uuid, p_status text) RETURNS TABLE(course_id uuid, category_id uuid, category_name text, title text, description text, start_date date, start_time time without time zone, recurrence_days integer, session_count integer, last_session_ends_at timestamp without time zone, status text, ingredient_margin numeric)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT course.id,
           course.category_id,
           category.name::TEXT,
           course.title::TEXT,
           course.description,
           course.start_date,
           course.start_time,
           course.recurrence_days,
           course.session_count,
           (
               SELECT max(
                   session.scheduled_at
                       + make_interval(mins => session.duration_minutes)
               )
                 FROM app.course_session AS session
                WHERE session.course_id = course.id
                  AND session.status <> 'CANCELLED'
           ) AS last_session_ends_at,
           course.status::TEXT,
           course.ingredient_margin
      FROM app.course
      JOIN app.category ON category.id = course.category_id
     WHERE course.chef_profile_id = chef_id
       AND (p_category_id IS NULL OR course.category_id = p_category_id)
       AND (p_status IS NULL OR course.status::TEXT = p_status)
     ORDER BY course.start_date, course.start_time, course.title;
END;
$$;

CREATE FUNCTION app.list_chef_notifications(p_session_token uuid) RETURNS TABLE(notification_id uuid, title text, message text, reason text, course_title text, created_at timestamp with time zone, recipient_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT notification.id,
           notification.title::TEXT,
           notification.message::TEXT,
           notification.reason::TEXT,
           course.title::TEXT,
           notification.created_at,
           count(notification_recipient.participant_profile_id)
      FROM app.notification
      LEFT JOIN app.course ON course.id = notification.course_id
      LEFT JOIN app.notification_recipient
        ON notification_recipient.notification_id = notification.id
     WHERE notification.chef_profile_id = chef_id
     GROUP BY notification.id, course.title
     ORDER BY notification.created_at DESC, notification.id;
END;
$$;

CREATE FUNCTION app.list_chef_notifications_with_course_context(p_session_token uuid) RETURNS TABLE(notification_id uuid, title text, message text, reason text, course_title text, created_at timestamp with time zone, recipient_count bigint, course_id uuid, course_start_date date)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT notification.id,
           notification.title::TEXT,
           notification.message::TEXT,
           notification.reason::TEXT,
           course.title::TEXT,
           notification.created_at,
           count(notification_recipient.participant_profile_id),
           course.id,
           course.start_date
      FROM app.notification
      LEFT JOIN app.course ON course.id = notification.course_id
      LEFT JOIN app.notification_recipient
        ON notification_recipient.notification_id = notification.id
     WHERE notification.chef_profile_id = chef_id
     GROUP BY notification.id, course.id, course.title, course.start_date
     ORDER BY notification.created_at DESC, notification.id;
END;
$$;

CREATE FUNCTION app.list_course_participants(p_session_token uuid, p_course_id uuid) RETURNS TABLE(participant_name text, enrollment_status text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM app.course
         WHERE course.id = p_course_id AND course.chef_profile_id = chef_id
    ) THEN
        RAISE EXCEPTION 'Course is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT participant_profile.display_name::TEXT, enrollment.status::TEXT
      FROM app.enrollment
      JOIN app.participant_profile
        ON participant_profile.id = enrollment.participant_profile_id
     WHERE enrollment.course_id = p_course_id
     ORDER BY participant_profile.display_name, enrollment.id;
END;
$$;

CREATE FUNCTION app.list_course_sessions(p_session_token uuid, p_course_id uuid) RETURNS TABLE(session_id uuid, sequence_number integer, scheduled_at timestamp without time zone, mode text, status text, duration_minutes integer, online_url text, venue text, capacity integer, attendance_closes_at timestamp without time zone, completed_at timestamp without time zone, recipe_ids uuid[], recipe_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT s.id,s.sequence_number,s.scheduled_at,s.mode::TEXT,app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome'),s.duration_minutes,
           s.online_url,s.venue::TEXT,s.capacity,s.attendance_closes_at,
           CASE WHEN app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome')='COMPLETED'
                THEN s.scheduled_at+make_interval(mins=>s.duration_minutes) ELSE NULL END,
           ARRAY(SELECT sr.recipe_id FROM app.session_recipe sr JOIN app.recipe r ON r.id=sr.recipe_id
                 WHERE sr.session_id=s.id ORDER BY r.name,r.id),
           (SELECT count(*) FROM app.session_recipe sr WHERE sr.session_id=s.id)
      FROM app.course_session s JOIN app.course c ON c.id=s.course_id
     WHERE c.id=p_course_id AND c.chef_profile_id=chef_id ORDER BY s.sequence_number;
END;
$$;

CREATE FUNCTION app.list_enrollment_practical_sessions(p_session_token uuid, p_course_id uuid) RETURNS TABLE(session_id uuid, sequence_number integer, scheduled_at timestamp without time zone, duration_minutes integer, venue text, capacity integer, confirmed_count integer, remaining_places integer, attendance_closes_at timestamp without time zone, attendance_status text, recipe_count integer)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
 participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
 IF participant_id IS NULL THEN RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE='42501'; END IF;
 RETURN QUERY SELECT s.id,s.sequence_number,s.scheduled_at,s.duration_minutes,s.venue::TEXT,s.capacity,count(confirmed.enrollment_id)::INTEGER,
 greatest(s.capacity-count(confirmed.enrollment_id)::INTEGER,0),s.attendance_closes_at,own.status::TEXT,
 (SELECT count(*)::INTEGER FROM app.session_recipe r WHERE r.session_id = s.id)
 FROM app.enrollment e JOIN app.course_session s ON s.course_id=e.course_id
 LEFT JOIN app.practical_attendance own ON own.enrollment_id=e.id AND own.session_id=s.id AND own.updated_at>=e.enrolled_at
 LEFT JOIN (app.practical_attendance confirmed JOIN app.enrollment current_enrollment
     ON current_enrollment.id=confirmed.enrollment_id AND current_enrollment.status='ACTIVE'
     AND confirmed.updated_at>=current_enrollment.enrolled_at)
     ON confirmed.session_id=s.id AND confirmed.status='CONFIRMED'
 WHERE e.participant_profile_id=participant_id AND e.course_id=p_course_id AND e.status='ACTIVE' AND s.mode='PRACTICAL' AND app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome')='SCHEDULED'
 GROUP BY s.id,own.status ORDER BY s.sequence_number;
END; $$;

CREATE FUNCTION app.list_managed_ingredients(p_token uuid) RETURNS TABLE(ingredient_id uuid, name text, unit text, locked boolean)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF app.authenticated_profile_id(p_token,'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Chef required' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT i.id,i.name::TEXT,i.unit::TEXT,FALSE FROM app.ingredient i ORDER BY i.name;
END; $$;

CREATE FUNCTION app.list_managed_ingredients_with_usage(p_token uuid) RETURNS TABLE(ingredient_id uuid, name text, unit text, locked boolean, unit_locked boolean)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    RETURN QUERY SELECT i.ingredient_id,i.name,i.unit,i.locked,
        EXISTS(SELECT 1 FROM app.recipe_ingredient ri WHERE ri.ingredient_id=i.ingredient_id)
        OR EXISTS(SELECT 1 FROM app.session_menu_snapshot m
            WHERE m.ingredients @> jsonb_build_array(jsonb_build_object('ingredient_id',i.ingredient_id)))
    FROM app.list_managed_ingredients(p_token) i ORDER BY i.name;
END; $$;

CREATE FUNCTION app.list_managed_recipe_ingredients(p_token uuid, p_recipe uuid) RETURNS TABLE(ingredient_id uuid, quantity numeric)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF app.authenticated_profile_id(p_token, 'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Chef required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT ri.ingredient_id, ri.quantity
    FROM app.recipe_ingredient ri
    JOIN app.ingredient i ON i.id = ri.ingredient_id
    WHERE ri.recipe_id = p_recipe
    ORDER BY i.name;
END;
$$;

CREATE FUNCTION app.list_participant_commitments(p_session_token uuid) RETURNS TABLE(course_id uuid, course_title text, scheduled_at timestamp without time zone, duration_minutes integer)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT c.id, c.title::TEXT, s.scheduled_at, s.duration_minutes
      FROM app.enrollment e
      JOIN app.course c ON c.id = e.course_id
      JOIN app.practical_attendance attendance ON attendance.enrollment_id = e.id
           AND attendance.status = 'CONFIRMED' AND attendance.updated_at >= e.enrolled_at
      JOIN app.course_session s ON s.id = attendance.session_id
     WHERE e.participant_profile_id = participant_id AND e.status = 'ACTIVE'
       AND s.mode = 'PRACTICAL'
       AND app.effective_session_status(s.status::TEXT, s.scheduled_at, s.duration_minutes,
           statement_timestamp() AT TIME ZONE 'Europe/Rome') = 'SCHEDULED'
     ORDER BY s.scheduled_at, c.title, c.id, s.id;
END;
$$;

CREATE FUNCTION app.list_participant_enrollments(p_session_token uuid) RETURNS TABLE(course_id uuid, title text, category_name text, chef_name text, start_date date, enrollment_status text, enrolled_at timestamp with time zone)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required'
            USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT course.id,
           course.title::TEXT,
           category.name::TEXT,
           chef_profile.display_name::TEXT,
           course.start_date,
           enrollment.status::TEXT,
           enrollment.enrolled_at
      FROM app.enrollment
      JOIN app.course ON course.id = enrollment.course_id
      JOIN app.category ON category.id = course.category_id
      JOIN app.chef_profile ON chef_profile.id = course.chef_profile_id
     WHERE enrollment.participant_profile_id = participant_id
     ORDER BY enrollment.enrolled_at DESC, course.title;
END;
$$;

CREATE FUNCTION app.list_participant_enrollments_by_ids(p_session_token uuid, p_course_ids uuid[]) RETURNS TABLE(course_id uuid, title text, category_name text, chef_name text, start_date date, enrollment_status text, enrolled_at timestamp with time zone)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    IF p_course_ids IS NULL OR cardinality(p_course_ids) > 100
       OR array_position(p_course_ids, NULL) IS NOT NULL THEN
        RAISE EXCEPTION 'Enrollment selection is invalid' USING ERRCODE = '22023';
    END IF;
    RETURN QUERY
    SELECT c.id, c.title::TEXT, category.name::TEXT, chef.display_name::TEXT,
           c.start_date, e.status::TEXT, e.enrolled_at
      FROM app.enrollment e
      JOIN app.course c ON c.id = e.course_id
      JOIN app.category category ON category.id = c.category_id
      JOIN app.chef_profile chef ON chef.id = c.chef_profile_id
     WHERE e.participant_profile_id = participant_id AND c.id = ANY(p_course_ids)
     ORDER BY e.enrolled_at DESC, c.title, c.id;
END;
$$;

CREATE FUNCTION app.list_participant_enrollments_page(p_session_token uuid, p_category_id uuid, p_search text, p_period text, p_now timestamp without time zone, p_limit integer, p_offset integer) RETURNS TABLE(course_id uuid, title text, category_name text, chef_name text, start_date date, enrollment_status text, enrolled_at timestamp with time zone, total_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    IF p_search IS NULL OR p_period IS NULL OR p_period NOT IN ('ACTIVE', 'PAST', 'ALL')
       OR p_now IS NULL OR p_limit IS NULL OR p_limit NOT BETWEEN 1 AND 100
       OR p_offset IS NULL OR p_offset < 0 THEN
        RAISE EXCEPTION 'Enrollment page parameters are invalid' USING ERRCODE = '22023';
    END IF;

    RETURN QUERY
    WITH eligible AS MATERIALIZED (
        SELECT e.id, e.course_id, e.enrolled_at, c.title
          FROM app.enrollment e
          JOIN app.course c ON c.id = e.course_id
          CROSS JOIN LATERAL (
              SELECT coalesce(
                         max(s.scheduled_at + make_interval(mins => s.duration_minutes))
                             FILTER (WHERE s.status <> 'CANCELLED'),
                         max(s.scheduled_at + make_interval(mins => s.duration_minutes)),
                         c.start_date + c.start_time) AS last_end
                FROM app.course_session s WHERE s.course_id = c.id
          ) schedule
         WHERE e.participant_profile_id = participant_id
           AND (p_category_id IS NULL OR c.category_id = p_category_id)
           AND position(lower(trim(p_search)) IN lower(c.title)) > 0
           AND (p_period = 'ALL' OR
                (p_period = 'PAST') =
                    (e.status = 'WITHDRAWN' OR
                     (c.status IN ('PUBLISHED', 'COMPLETED', 'CANCELLED') AND
                      (c.status <> 'PUBLISHED' OR schedule.last_end <= p_now))))
    ), selected AS (
        SELECT eligible.id, eligible.course_id
          FROM eligible
         ORDER BY eligible.enrolled_at DESC, eligible.title, eligible.course_id
         LIMIT p_limit OFFSET p_offset
    )
    SELECT c.id, c.title::TEXT, category.name::TEXT, chef.display_name::TEXT,
           c.start_date, e.status::TEXT, e.enrolled_at, totals.count
      FROM (SELECT count(*) FROM eligible) totals
      LEFT JOIN selected ON TRUE
      LEFT JOIN app.enrollment e ON e.id = selected.id
      LEFT JOIN app.course c ON c.id = selected.course_id
      LEFT JOIN app.category category ON category.id = c.category_id
      LEFT JOIN app.chef_profile chef ON chef.id = c.chef_profile_id
     ORDER BY e.enrolled_at DESC, c.title, c.id;
END;
$$;

CREATE FUNCTION app.list_participant_notifications(p_session_token uuid) RETURNS TABLE(notification_id uuid, title text, message text, reason text, course_title text, created_at timestamp with time zone, read_at timestamp with time zone, course_id uuid)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT notification.id,
           notification.title::TEXT,
           notification.message::TEXT,
           notification.reason::TEXT,
           course.title::TEXT,
           notification.created_at,
           notification_recipient.read_at,
           notification.course_id
      FROM app.notification_recipient
      JOIN app.notification
        ON notification.id = notification_recipient.notification_id
      LEFT JOIN app.course ON course.id = notification.course_id
     WHERE notification_recipient.participant_profile_id = participant_id
     ORDER BY notification.created_at DESC, notification.id;
END;
$$;

CREATE FUNCTION app.list_published_course_sessions(p_session_token uuid, p_course_id uuid) RETURNS TABLE(session_id uuid, sequence_number integer, scheduled_at timestamp without time zone, mode text, duration_minutes integer, online_url text, venue text, capacity integer, attendance_closes_at timestamp without time zone, status text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT s.id, s.sequence_number, s.scheduled_at, s.mode::TEXT, s.duration_minutes,
           CASE WHEN c.status = 'PUBLISHED' AND app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome') = 'SCHEDULED' AND s.mode = 'ONLINE'
                     AND EXISTS (SELECT 1 FROM app.enrollment e
                                  WHERE e.course_id = c.id
                                    AND e.participant_profile_id = participant_id AND e.status = 'ACTIVE')
                THEN s.online_url::TEXT ELSE NULL END,
           s.venue::TEXT, s.capacity, s.attendance_closes_at, app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome')
      FROM app.course_session s JOIN app.course c ON c.id = s.course_id
     WHERE c.id = p_course_id
       AND EXISTS (SELECT 1 FROM app.list_published_courses(p_session_token, NULL) eligible
                    WHERE eligible.course_id = c.id)
       AND (s.status <> 'CANCELLED'
            OR EXISTS (SELECT 1 FROM app.enrollment e
                        WHERE e.course_id = c.id AND e.participant_profile_id = participant_id))
     ORDER BY s.sequence_number;
END;
$$;

CREATE FUNCTION app.list_published_courses(p_session_token uuid, p_category_id uuid) RETURNS TABLE(course_id uuid, category_id uuid, category_name text, chef_name text, title text, description text, start_date date, start_time time without time zone, session_count integer, last_session_ends_at timestamp without time zone, status text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT c.id, c.category_id, category.name::TEXT, chef.display_name::TEXT,
           c.title::TEXT, c.description::TEXT, c.start_date, c.start_time, c.session_count,
           schedule.last_end, c.status::TEXT
      FROM app.course c
      JOIN app.category category ON category.id = c.category_id
      JOIN app.chef_profile chef ON chef.id = c.chef_profile_id
      CROSS JOIN LATERAL (
          SELECT coalesce(
                     max(s.scheduled_at + make_interval(mins => s.duration_minutes))
                         FILTER (WHERE s.status <> 'CANCELLED'),
                     max(s.scheduled_at + make_interval(mins => s.duration_minutes)),
                     c.start_date + c.start_time) AS last_end
            FROM app.course_session s WHERE s.course_id = c.id
      ) schedule
     WHERE (p_category_id IS NULL OR c.category_id = p_category_id)
       AND (
           (c.status = 'PUBLISHED'
            AND schedule.last_end > current_timestamp AT TIME ZONE 'Europe/Rome')
           OR (c.status IN ('PUBLISHED', 'COMPLETED', 'CANCELLED')
               AND EXISTS (SELECT 1 FROM app.enrollment e
                            WHERE e.course_id = c.id AND e.participant_profile_id = participant_id))
       )
     ORDER BY c.start_date, c.start_time, c.title, c.id;
END;
$$;

CREATE FUNCTION app.list_published_session_menu(p_session_token uuid, p_course_id uuid) RETURNS TABLE(session_id uuid, recipe_id uuid, name text, description text, base_servings integer, portions_per_participant numeric)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE participant UUID;
BEGIN
    participant := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant IS NULL THEN
        RAISE EXCEPTION 'Participant required' USING ERRCODE = '42501';
    END IF;
    RETURN QUERY
    SELECT s.id, m.recipe_id, m.recipe_name, m.description, 1, 1::NUMERIC
      FROM app.course_session s JOIN app.session_menu_snapshot m ON m.session_id = s.id
     WHERE s.course_id = p_course_id
       AND EXISTS (
           SELECT 1 FROM app.page_published_courses(p_session_token, NULL, '', 'ALL',
               statement_timestamp() AT TIME ZONE 'Europe/Rome', 1, 0, ARRAY[p_course_id]) c
            WHERE c.course_id = p_course_id
       )
       AND (s.status <> 'CANCELLED' OR EXISTS (
           SELECT 1 FROM app.enrollment e WHERE e.course_id = p_course_id AND e.participant_profile_id = participant
       ))
     ORDER BY s.sequence_number, lower(m.recipe_name), m.recipe_id;
END;
$$;

CREATE FUNCTION app.list_published_session_menu_doses(p_session_token uuid, p_course_id uuid) RETURNS TABLE(session_id uuid, recipe_id uuid, name text, description text, base_servings integer, portions_per_participant numeric, ingredient_name text, quantity numeric, unit text)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    WITH visible AS MATERIALIZED (
        SELECT * FROM app.list_published_session_menu(p_session_token, p_course_id)
    )
    SELECT v.session_id, v.recipe_id, v.name, v.description,
        v.base_servings, v.portions_per_participant, i.ingredient_name, i.quantity, i.unit
    FROM visible v
    JOIN app.session_menu_snapshot m ON m.session_id=v.session_id AND m.recipe_id=v.recipe_id
    JOIN app.course_session s ON s.id=v.session_id
    LEFT JOIN LATERAL jsonb_to_recordset(m.ingredients)
        AS i(ingredient_id UUID, ingredient_name TEXT, quantity NUMERIC, unit TEXT) ON TRUE
    ORDER BY s.sequence_number, lower(v.name), v.recipe_id, lower(i.ingredient_name), i.ingredient_id;
$$;

CREATE FUNCTION app.list_session_ingredient_requirements(p_session_token uuid, p_session_id uuid) RETURNS TABLE(ingredient_name text, unit text, confirmed_participants integer, required_quantity numeric)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    SELECT p.ingredient_name,p.unit,max(p.confirmed_participants),max(p.required_quantity)
    FROM app.session_preparation(p_session_token,p_session_id) p WHERE p.ingredient_id IS NOT NULL
    GROUP BY p.ingredient_id,p.ingredient_name,p.unit ORDER BY p.ingredient_name,p.unit;
$$;

CREATE FUNCTION app.list_session_participants(p_session_token uuid, p_session_id uuid) RETURNS TABLE(participant_name text, enrollment_status text, attendance_status text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
    owned_course_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;

    SELECT course.id
      INTO owned_course_id
      FROM app.course_session
      JOIN app.course ON course.id = course_session.course_id
     WHERE course_session.id = p_session_id
       AND course.chef_profile_id = chef_id;
    IF owned_course_id IS NULL THEN
        RAISE EXCEPTION 'Session is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;

    RETURN QUERY
    SELECT participant_profile.display_name::TEXT,
           enrollment.status::TEXT,
           practical_attendance.status::TEXT
      FROM app.enrollment
      JOIN app.participant_profile
        ON participant_profile.id = enrollment.participant_profile_id
      JOIN app.course_session
        ON course_session.id = p_session_id
      LEFT JOIN app.practical_attendance
        ON practical_attendance.enrollment_id = enrollment.id
       AND practical_attendance.session_id = p_session_id
       AND practical_attendance.updated_at >= enrollment.enrolled_at
     WHERE enrollment.course_id = owned_course_id
       AND (
           course_session.mode <> 'PRACTICAL'
           OR course_session.attendance_closes_at IS NULL
           OR (current_timestamp AT TIME ZONE 'Europe/Rome')
                < course_session.attendance_closes_at
           OR practical_attendance.status = 'CONFIRMED'
       )
     ORDER BY participant_profile.display_name, enrollment.id;
END;
$$;

CREATE FUNCTION app.lock_assigned_recipe_catalog() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    PERFORM 1 FROM app.recipe WHERE id = NEW.recipe_id FOR UPDATE;
    PERFORM 1 FROM app.ingredient i
    JOIN app.recipe_ingredient ri ON ri.ingredient_id = i.id
    WHERE ri.recipe_id = NEW.recipe_id
    ORDER BY i.id FOR UPDATE OF i;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.mark_notification_read(p_session_token uuid, p_notification_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    participant_id UUID;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;

    UPDATE app.notification_recipient
       SET read_at = coalesce(read_at, clock_timestamp())
     WHERE notification_recipient.notification_id = p_notification_id
       AND notification_recipient.participant_profile_id = participant_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Notification is unavailable for this Participant'
            USING ERRCODE = '42501';
    END IF;
END;
$$;

CREATE FUNCTION app.open_auth_session(p_account_id uuid) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE generated_token UUID;
BEGIN
    generated_token := gen_random_uuid();
    DELETE FROM app.auth_session
     WHERE account_id = p_account_id AND expires_at <= clock_timestamp();
    INSERT INTO app.auth_session(token_hash, account_id, expires_at)
    VALUES (sha256(convert_to(generated_token::TEXT, 'UTF8')), p_account_id,
            clock_timestamp() + INTERVAL '12 hours');
    RETURN generated_token;
END;
$$;

CREATE FUNCTION app.page_catalog_recipes(p_token uuid, p_category uuid, p_search text, p_menu boolean, p_size integer, p_offset integer) RETURNS TABLE(recipe_id uuid, category_id uuid, category_name text, name text, description text, base_servings integer, ingredient_count bigint, locked boolean, total_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF coalesce(app.authenticated_profile_id(p_token, 'CHEF'), app.authenticated_profile_id(p_token, 'PARTICIPANT')) IS NULL THEN
        RAISE EXCEPTION 'Authenticated session required' USING ERRCODE = '42501';
    END IF;
    IF p_size IS NULL OR p_size < 1 OR p_size > 100 OR p_offset IS NULL OR p_offset < 0 OR p_search IS NULL OR p_menu IS NULL THEN
        RAISE EXCEPTION 'Invalid recipe page' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY
    WITH filtered AS NOT MATERIALIZED (
        SELECT r.id AS recipe_id, r.category_id, category.name::TEXT AS category_name,
               r.name::TEXT AS name, r.description, r.base_servings
          FROM app.recipe r JOIN app.category category ON category.id = r.category_id
         WHERE (p_category IS NULL OR r.category_id = p_category)
           AND ((NOT p_menu AND strpos(lower(r.name || ' ' || category.name || ' ' || r.description), lower(btrim(p_search))) > 0)
                OR (p_menu AND (strpos(lower(r.name), lower(btrim(p_search))) > 0
                            OR strpos(lower(category.name), lower(btrim(p_search))) > 0)))
    ), selected AS (
        SELECT f.* FROM filtered f ORDER BY CASE WHEN p_menu THEN '' ELSE f.category_name END, lower(f.name), f.recipe_id LIMIT p_size OFFSET p_offset
    )
    SELECT selected.recipe_id, selected.category_id, selected.category_name, selected.name,
           selected.description, selected.base_servings,
           (SELECT count(*) FROM app.recipe_ingredient dose WHERE dose.recipe_id = selected.recipe_id),
           false, totals.total_count
      FROM (SELECT count(*) AS total_count FROM filtered) totals LEFT JOIN selected ON true
     ORDER BY CASE WHEN p_menu THEN '' ELSE selected.category_name END, lower(selected.name), selected.recipe_id;
END;
$$;

CREATE FUNCTION app.page_chef_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[] DEFAULT NULL::uuid[]) RETURNS TABLE(course_id uuid, category_id uuid, category_name text, title text, description text, start_date date, start_time time without time zone, recurrence_days integer, session_count integer, last_session_ends_at timestamp without time zone, status text, ingredient_margin numeric, total_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE profile_id UUID;
BEGIN
    profile_id := app.authenticated_profile_id(p_token, 'CHEF');
    IF profile_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated CHEF session required' USING ERRCODE = '42501';
    END IF;
    IF p_size IS NULL OR p_size < 1 OR p_size > 100 OR p_offset IS NULL OR p_offset < 0
       OR p_search IS NULL OR p_period IS NULL OR p_period NOT IN ('ACTIVE', 'PAST', 'ALL') OR p_now IS NULL THEN
        RAISE EXCEPTION 'Invalid course page' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY
    WITH filtered AS NOT MATERIALIZED (
        SELECT c.id AS course_id, c.category_id, category.name::TEXT AS category_name,
               c.title::TEXT AS title, c.description, c.start_date, c.start_time,
               c.recurrence_days, c.session_count, schedule.last_end AS last_session_ends_at,
               c.status::TEXT AS status, c.ingredient_margin
          FROM app.course c JOIN app.category category ON category.id = c.category_id
          CROSS JOIN LATERAL (
              SELECT max(s.scheduled_at + make_interval(mins => s.duration_minutes)) AS last_end
                FROM app.course_session s WHERE s.course_id = c.id AND s.status <> 'CANCELLED'
          ) schedule
         WHERE c.chef_profile_id = profile_id
           AND (p_category IS NULL OR c.category_id = p_category)
           AND (p_ids IS NULL OR c.id = ANY(p_ids))
           AND NOT EXISTS (
               SELECT 1 FROM regexp_split_to_table(lower(btrim(p_search)), E'\\s+') term
                WHERE strpos(lower(c.title || ' ' || category.name), term) = 0
           )
           AND (p_period = 'ALL' OR (p_period = 'PAST') =
                (c.status <> 'DRAFT' AND (c.status IN ('COMPLETED', 'CANCELLED')
                 OR schedule.last_end IS NULL OR schedule.last_end <= p_now)))
    ), selected AS (
        SELECT f.* FROM filtered f ORDER BY f.start_date, f.start_time, f.title, f.course_id LIMIT p_size OFFSET p_offset
    )
    SELECT selected.course_id, selected.category_id, selected.category_name, selected.title, selected.description, selected.start_date, selected.start_time, selected.recurrence_days, selected.session_count, selected.last_session_ends_at, selected.status, selected.ingredient_margin, totals.total_count
      FROM (SELECT count(*) AS total_count FROM filtered) totals
      LEFT JOIN selected ON true
     ORDER BY selected.start_date, selected.start_time, selected.title, selected.course_id;
END;
$$;

CREATE FUNCTION app.page_published_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[] DEFAULT NULL::uuid[]) RETURNS TABLE(course_id uuid, category_id uuid, category_name text, chef_name text, title text, description text, start_date date, start_time time without time zone, session_count integer, last_session_ends_at timestamp without time zone, status text, total_count bigint)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE profile_id UUID;
BEGIN
    profile_id := app.authenticated_profile_id(p_token, 'PARTICIPANT');
    IF profile_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated PARTICIPANT session required' USING ERRCODE = '42501';
    END IF;
    IF p_size IS NULL OR p_size < 1 OR p_size > 100 OR p_offset IS NULL OR p_offset < 0
       OR p_search IS NULL OR p_period IS NULL OR p_period NOT IN ('ACTIVE', 'PAST', 'ALL') OR p_now IS NULL THEN
        RAISE EXCEPTION 'Invalid course page' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY
    WITH filtered AS NOT MATERIALIZED (
        SELECT c.id AS course_id, c.category_id, category.name::TEXT AS category_name,
               chef.display_name::TEXT AS chef_name, c.title::TEXT AS title, c.description,
               c.start_date, c.start_time, c.session_count, schedule.last_end AS last_session_ends_at,
               c.status::TEXT AS status
          FROM app.course c JOIN app.category category ON category.id = c.category_id
          JOIN app.chef_profile chef ON chef.id = c.chef_profile_id
          CROSS JOIN LATERAL (
              SELECT coalesce(max(s.scheduled_at + make_interval(mins => s.duration_minutes)) FILTER (WHERE s.status <> 'CANCELLED'),
                     max(s.scheduled_at + make_interval(mins => s.duration_minutes)), c.start_date + c.start_time) AS last_end
                FROM app.course_session s WHERE s.course_id = c.id
          ) schedule
         WHERE (p_category IS NULL OR c.category_id = p_category)
           AND (p_ids IS NULL OR c.id = ANY(p_ids))
           AND ((c.status = 'PUBLISHED' AND schedule.last_end > statement_timestamp() AT TIME ZONE 'Europe/Rome')
                OR (c.status IN ('PUBLISHED', 'COMPLETED', 'CANCELLED') AND EXISTS (
                    SELECT 1 FROM app.enrollment e WHERE e.course_id = c.id AND e.participant_profile_id = profile_id)))
           AND NOT EXISTS (
               SELECT 1 FROM regexp_split_to_table(lower(btrim(p_search)), E'\\s+') term
                WHERE strpos(lower(c.title || ' ' || category.name || ' ' || chef.display_name || ' ' || c.description), term) = 0
           )
           AND (p_period = 'ALL' OR (p_period = 'PAST') =
                (c.status IN ('COMPLETED', 'CANCELLED') OR schedule.last_end <= p_now))
    ), selected AS (
        SELECT f.* FROM filtered f ORDER BY f.start_date, f.start_time, f.title, f.course_id LIMIT p_size OFFSET p_offset
    )
    SELECT selected.course_id, selected.category_id, selected.category_name, selected.chef_name, selected.title, selected.description, selected.start_date, selected.start_time, selected.session_count, selected.last_session_ends_at, selected.status, totals.total_count
      FROM (SELECT count(*) AS total_count FROM filtered) totals
      LEFT JOIN selected ON true
     ORDER BY selected.start_date, selected.start_time, selected.title, selected.course_id;
END;
$$;

CREATE FUNCTION app.preliminary_session_preparation(p_session uuid) RETURNS TABLE(confirmed_participants integer, pending_participants integer, declined_participants integer, ingredient_margin numeric, recipe_id uuid, recipe_name text, base_servings integer, portions_per_participant numeric, ingredient_id uuid, ingredient_name text, unit text, contribution_quantity numeric, required_quantity numeric)
    LANGUAGE sql STABLE
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    WITH responses AS (
        SELECT count(*) FILTER(WHERE pa.status='CONFIRMED')::INTEGER confirmed,
            count(*) FILTER(WHERE pa.status IS NULL)::INTEGER pending,
            count(*) FILTER(WHERE pa.status='DECLINED')::INTEGER declined
        FROM app.enrollment e JOIN app.course_session s ON s.course_id=e.course_id
        LEFT JOIN app.practical_attendance pa ON pa.enrollment_id=e.id AND pa.session_id=s.id AND pa.updated_at>=e.enrolled_at
        WHERE s.id=p_session AND e.status='ACTIVE'
    ), doses AS (
        SELECT m.recipe_id,m.recipe_name,i.* FROM app.session_menu_snapshot m
        CROSS JOIN LATERAL jsonb_to_recordset(m.ingredients)
            AS i(ingredient_id UUID,ingredient_name TEXT,unit TEXT,quantity NUMERIC)
        WHERE m.session_id=p_session
    )
    SELECT r.confirmed,r.pending,r.declined,0::NUMERIC,d.recipe_id,d.recipe_name,1,1::NUMERIC,
        d.ingredient_id,i.name::TEXT,d.unit,d.quantity*r.confirmed,
        sum(d.quantity) OVER(PARTITION BY d.ingredient_id)*r.confirmed
    FROM responses r LEFT JOIN doses d ON TRUE
    LEFT JOIN app.ingredient i ON i.id=d.ingredient_id;
$$;

CREATE FUNCTION app.preserve_recipe_ingredient_unit() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NEW.unit IS DISTINCT FROM OLD.unit AND (
        EXISTS(SELECT 1 FROM app.recipe_ingredient ri WHERE ri.ingredient_id=OLD.id)
        OR EXISTS(SELECT 1 FROM app.session_menu_snapshot m
            WHERE m.ingredients @> jsonb_build_array(jsonb_build_object('ingredient_id',OLD.id)))
    ) THEN
        RAISE EXCEPTION 'Ingredient unit is used by a recipe or saved session' USING ERRCODE='55006';
    END IF;
    RETURN NEW;
END; $$;

CREATE FUNCTION app.preserve_session_change() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF OLD.scheduled_at IS DISTINCT FROM NEW.scheduled_at OR (NEW.status='CANCELLED' AND OLD.status<>'CANCELLED') THEN
        INSERT INTO app.session_change_history(session_id,previous_schedule,next_schedule,previous_status,next_status,previous_attendance)
        VALUES(OLD.id,OLD.scheduled_at,NEW.scheduled_at,OLD.status,NEW.status,
            coalesce((SELECT jsonb_agg(to_jsonb(a)) FROM app.practical_attendance a WHERE a.session_id=OLD.id),'[]'::jsonb));
        UPDATE app.session_preparation_revision SET active=FALSE WHERE session_id=OLD.id AND active;
        IF OLD.scheduled_at IS DISTINCT FROM NEW.scheduled_at THEN
            DELETE FROM app.practical_attendance WHERE session_id=OLD.id;
        END IF;
    END IF;
    RETURN NEW;
END; $$;

CREATE FUNCTION app.prevent_chef_session_overlap() RETURNS trigger
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE owner_id UUID;
BEGIN
    IF NEW.status <> 'SCHEDULED' THEN
        RETURN NEW;
    END IF;

    SELECT chef_profile_id INTO owner_id FROM app.course WHERE id = NEW.course_id;
    IF owner_id IS NULL THEN
        RAISE EXCEPTION 'Course owner is unavailable' USING ERRCODE = '23503';
    END IF;

    PERFORM pg_advisory_xact_lock(hashtextextended(owner_id::TEXT, 0));
    IF EXISTS (
        SELECT 1
          FROM app.course_session existing_session
          JOIN app.course existing_course ON existing_course.id = existing_session.course_id
         WHERE existing_course.chef_profile_id = owner_id
           AND existing_session.status = 'SCHEDULED'
           AND existing_session.id IS DISTINCT FROM NEW.id
           AND existing_session.scheduled_at
                 < NEW.scheduled_at + make_interval(mins => NEW.duration_minutes)
           AND NEW.scheduled_at
                 < existing_session.scheduled_at + make_interval(mins => existing_session.duration_minutes)
    ) THEN
        RAISE EXCEPTION 'Chef already has another session in this time interval'
            USING ERRCODE = '23P01';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION app.publish_course(p_session_token uuid, p_course_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    PERFORM app.publish_course_for_chef(chef_id, p_course_id);
END;
$$;

CREATE FUNCTION app.publish_course_for_chef(p_chef_profile_id uuid, p_course_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    current_status TEXT;
BEGIN
    SELECT course.status
      INTO current_status
      FROM app.course
     WHERE course.id = p_course_id
       AND course.chef_profile_id = p_chef_profile_id
     FOR UPDATE;

    IF current_status IS NULL THEN
        RAISE EXCEPTION 'Course is unavailable' USING ERRCODE = '42501';
    END IF;
    IF current_status = 'PUBLISHED' THEN
        RETURN;
    END IF;
    IF current_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'Course cannot be activated from its current state'
            USING ERRCODE = '55000';
    END IF;

    UPDATE app.course SET status = 'PUBLISHED' WHERE id = p_course_id;
END;
$$;

CREATE FUNCTION app.published_course_sessions_batch(p_token uuid, p_ids uuid[]) RETURNS TABLE(course_id uuid, session_id uuid, sequence_number integer, scheduled_at timestamp without time zone, mode text, duration_minutes integer, online_url text, venue text, capacity integer, attendance_closes_at timestamp without time zone, status text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE profile_id UUID;
BEGIN
    profile_id := app.authenticated_profile_id(p_token, 'PARTICIPANT');
    IF profile_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    IF p_ids IS NULL OR cardinality(p_ids) > 100 THEN
        RAISE EXCEPTION 'At most one hundred course IDs required' USING ERRCODE = '23514';
    END IF;
    RETURN QUERY
    SELECT requested.id, session.session_id, session.sequence_number, session.scheduled_at, session.mode,
           session.duration_minutes, session.online_url, session.venue, session.capacity,
           session.attendance_closes_at, session.status
      FROM unnest(p_ids) requested(id)
      LEFT JOIN LATERAL (
          SELECT s.id AS session_id, s.sequence_number, s.scheduled_at, s.mode::TEXT AS mode,
                 s.duration_minutes,
                 CASE WHEN c.status = 'PUBLISHED' AND s.mode = 'ONLINE'
                           AND app.effective_session_status(s.status::TEXT, s.scheduled_at, s.duration_minutes,
                               statement_timestamp() AT TIME ZONE 'Europe/Rome') = 'SCHEDULED'
                           AND EXISTS (SELECT 1 FROM app.enrollment e WHERE e.course_id = c.id
                                       AND e.participant_profile_id = profile_id AND e.status = 'ACTIVE')
                      THEN s.online_url::TEXT ELSE NULL END AS online_url,
                 s.venue::TEXT AS venue, s.capacity, s.attendance_closes_at,
                 app.effective_session_status(s.status::TEXT, s.scheduled_at, s.duration_minutes,
                     statement_timestamp() AT TIME ZONE 'Europe/Rome') AS status
            FROM app.course c JOIN app.course_session s ON s.course_id = c.id
            CROSS JOIN LATERAL (
                SELECT coalesce(max(other.scheduled_at + make_interval(mins => other.duration_minutes))
                           FILTER (WHERE other.status <> 'CANCELLED'),
                       max(other.scheduled_at + make_interval(mins => other.duration_minutes)),
                       c.start_date + c.start_time) AS last_end
                  FROM app.course_session other WHERE other.course_id = c.id
            ) schedule
           WHERE c.id = requested.id
             AND ((c.status = 'PUBLISHED' AND schedule.last_end > statement_timestamp() AT TIME ZONE 'Europe/Rome')
                  OR (c.status IN ('PUBLISHED', 'COMPLETED', 'CANCELLED') AND EXISTS (
                      SELECT 1 FROM app.enrollment e WHERE e.course_id = c.id AND e.participant_profile_id = profile_id)))
             AND (s.status <> 'CANCELLED' OR EXISTS (
                  SELECT 1 FROM app.enrollment e WHERE e.course_id = c.id AND e.participant_profile_id = profile_id))
      ) session ON true
     ORDER BY requested.id, session.sequence_number;
END;
$$;

CREATE FUNCTION app.refresh_session_menu(p_token uuid, p_session uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    PERFORM app.require_owned_session(p_token,p_session);
    IF NOT EXISTS(SELECT 1 FROM app.course_session s WHERE s.id=p_session AND s.mode='PRACTICAL'
        AND s.status='SCHEDULED' AND least(s.scheduled_at,s.attendance_closes_at)>clock_timestamp() AT TIME ZONE 'Europe/Rome') THEN
        RAISE EXCEPTION 'The menu can only be updated before the response deadline' USING ERRCODE='55000';
    END IF;
    UPDATE app.session_menu_snapshot m SET recipe_name=r.name,description=r.description,
        captured_at=clock_timestamp(),provenance='CAPTURED',ingredients=(
            SELECT jsonb_agg(jsonb_build_object('ingredient_id',i.id,'ingredient_name',i.name,'unit',i.unit,'quantity',ri.quantity) ORDER BY i.id)
            FROM app.recipe_ingredient ri JOIN app.ingredient i ON i.id=ri.ingredient_id WHERE ri.recipe_id=r.id)
    FROM app.recipe r WHERE m.session_id=p_session AND r.id=m.recipe_id;
END; $$;

CREATE FUNCTION app.register_chef(p_username text, p_display_name text, p_password_hash text) RETURNS TABLE(session_token uuid, account_id uuid, profile_id uuid, username text, display_name text, role text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE new_account_id UUID;
DECLARE new_profile_id UUID;
DECLARE new_token UUID;
DECLARE normalized_username TEXT;
DECLARE normalized_display_name TEXT;
BEGIN
    normalized_username := lower(btrim(p_username));
    normalized_display_name := btrim(p_display_name);
    IF normalized_username !~ '^[a-z0-9._-]{3,50}$'
       OR normalized_display_name = '' OR length(normalized_display_name) > 100
       OR p_password_hash !~ '^\$pbkdf2-sha256\$600000\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$' THEN
        RAISE EXCEPTION 'Invalid account registration' USING ERRCODE = '23514';
    END IF;

    INSERT INTO app.account(username, password_hash, role)
    VALUES (normalized_username, p_password_hash, 'CHEF')
    RETURNING id INTO new_account_id;
    INSERT INTO app.chef_profile(account_id, display_name)
    VALUES (new_account_id, normalized_display_name)
    RETURNING id INTO new_profile_id;
    new_token := app.open_auth_session(new_account_id);
    RETURN QUERY SELECT new_token, new_account_id, new_profile_id,
                        normalized_username, normalized_display_name, 'CHEF'::TEXT;
END;
$_$;

CREATE FUNCTION app.register_participant(p_username text, p_display_name text, p_password_hash text) RETURNS TABLE(session_token uuid, account_id uuid, profile_id uuid, username text, display_name text, role text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $_$
DECLARE new_account_id UUID;
DECLARE new_profile_id UUID;
DECLARE new_token UUID;
DECLARE normalized_username TEXT;
DECLARE normalized_display_name TEXT;
BEGIN
    normalized_username := lower(btrim(p_username));
    normalized_display_name := btrim(p_display_name);
    IF normalized_username !~ '^[a-z0-9._-]{3,50}$'
       OR normalized_display_name = '' OR length(normalized_display_name) > 100
       OR p_password_hash !~ '^\$pbkdf2-sha256\$600000\$[A-Za-z0-9+/]+\$[A-Za-z0-9+/]+$' THEN
        RAISE EXCEPTION 'Invalid account registration' USING ERRCODE = '23514';
    END IF;

    INSERT INTO app.account(username, password_hash, role)
    VALUES (normalized_username, p_password_hash, 'PARTICIPANT')
    RETURNING id INTO new_account_id;
    INSERT INTO app.participant_profile(account_id, display_name)
    VALUES (new_account_id, normalized_display_name)
    RETURNING id INTO new_profile_id;
    new_token := app.open_auth_session(new_account_id);
    RETURN QUERY SELECT new_token, new_account_id, new_profile_id,
                        normalized_username, normalized_display_name, 'PARTICIPANT'::TEXT;
END;
$_$;

CREATE FUNCTION app.remove_session_recipe(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE chef_id UUID; lesson app.course_session%ROWTYPE;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    PERFORM id FROM app.recipe WHERE id = p_recipe_id FOR UPDATE;
    SELECT s.* INTO lesson FROM app.course_session s JOIN app.course c ON c.id = s.course_id
     WHERE s.id = p_session_id AND c.chef_profile_id = chef_id FOR UPDATE OF s;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Session is unavailable' USING ERRCODE = '42501';
    END IF;
    IF lesson.mode <> 'PRACTICAL' OR lesson.status <> 'SCHEDULED'
       OR least(lesson.scheduled_at, lesson.attendance_closes_at)
           <= clock_timestamp() AT TIME ZONE 'Europe/Rome' THEN
        RAISE EXCEPTION 'Menu is closed for this session' USING ERRCODE = '55000';
    END IF;
    IF EXISTS (SELECT 1 FROM app.session_recipe WHERE session_id=p_session_id AND recipe_id=p_recipe_id)
       AND (SELECT count(*) FROM app.session_recipe WHERE session_id=p_session_id) = 1
       AND EXISTS (SELECT 1 FROM app.practical_attendance pa JOIN app.enrollment e ON e.id=pa.enrollment_id
            WHERE pa.session_id=p_session_id AND pa.status='CONFIRMED'
              AND e.status='ACTIVE' AND pa.updated_at>=e.enrolled_at) THEN
        RAISE EXCEPTION 'Keep at least one recipe for confirmed participants' USING ERRCODE = '23514';
    END IF;
    DELETE FROM app.session_recipe WHERE session_id = p_session_id AND recipe_id = p_recipe_id;
END;
$$;

CREATE FUNCTION app.require_owned_session(p_token uuid, p_session uuid) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE owned UUID;
BEGIN
    SELECT c.id INTO owned FROM app.course_session s JOIN app.course c ON c.id=s.course_id
    WHERE s.id=p_session AND c.chef_profile_id=app.authenticated_profile_id(p_token,'CHEF');
    IF owned IS NULL THEN RAISE EXCEPTION 'Session unavailable for this Chef' USING ERRCODE='42501'; END IF;

    PERFORM 1 FROM app.course WHERE id=owned FOR UPDATE;
    PERFORM 1 FROM app.enrollment WHERE course_id=owned ORDER BY id FOR UPDATE;
    PERFORM 1 FROM app.course_session WHERE id=p_session FOR UPDATE;
    RETURN owned;
END; $$;

CREATE FUNCTION app.reschedule_course_session(p_session_token uuid, p_session_id uuid, p_scheduled_at timestamp without time zone, p_attendance_closes_at timestamp without time zone, p_location text, p_notification_title text, p_notification_message text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
    owned_course_id UUID;
    course_status TEXT;
    mode_value TEXT;
    status_value TEXT;
    original_time TIMESTAMP;
    original_cutoff TIMESTAMP;
    operation_time TIMESTAMP;
    cutoff TIMESTAMP;
    notice UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;

    SELECT course.id, course.status
      INTO owned_course_id, course_status
      FROM app.course_session session
      JOIN app.course course ON course.id = session.course_id
     WHERE session.id = p_session_id
       AND course.chef_profile_id = chef_id
     FOR UPDATE OF course;
    IF owned_course_id IS NULL THEN
        RAISE EXCEPTION 'Session is unavailable for this Chef' USING ERRCODE = '42501';
    END IF;

    PERFORM enrollment.id
      FROM app.enrollment enrollment
     WHERE enrollment.course_id = owned_course_id
     ORDER BY enrollment.id
     FOR UPDATE;
    SELECT mode, status, scheduled_at, attendance_closes_at
      INTO mode_value, status_value, original_time, original_cutoff
      FROM app.course_session
     WHERE id = p_session_id
     FOR UPDATE;

    operation_time := clock_timestamp() AT TIME ZONE 'Europe/Rome';
    IF p_scheduled_at IS NULL OR p_scheduled_at <= operation_time THEN
        RAISE EXCEPTION 'Session schedule is invalid' USING ERRCODE = '22023';
    END IF;
    IF course_status <> 'PUBLISHED' OR status_value <> 'SCHEDULED'
       OR (p_location IS NOT NULL AND original_time <= operation_time) THEN
        IF p_location IS NOT NULL THEN
            RAISE EXCEPTION 'Only future scheduled sessions of published courses can be changed'
                USING ERRCODE = '55000';
        END IF;
        RAISE EXCEPTION 'Only scheduled sessions of published courses can be moved'
            USING ERRCODE = '55000';
    END IF;

    cutoff := CASE WHEN mode_value = 'PRACTICAL'
        THEN coalesce(p_attendance_closes_at, p_scheduled_at - INTERVAL '1 minute')
        ELSE NULL END;
    IF mode_value = 'PRACTICAL' THEN
        IF cutoff >= p_scheduled_at
           OR (cutoff <= operation_time AND (
               p_location IS NULL
               OR cutoff IS DISTINCT FROM original_cutoff
               OR p_scheduled_at IS DISTINCT FROM original_time)) THEN
            RAISE EXCEPTION 'Attendance cutoff is invalid' USING ERRCODE = '22023';
        END IF;
    ELSIF mode_value = 'ONLINE' AND p_attendance_closes_at IS NOT NULL THEN
        RAISE EXCEPTION 'Attendance cutoff is invalid' USING ERRCODE = '22023';
    END IF;

    IF p_location IS NOT NULL THEN
        IF btrim(p_location) = ''
           OR (mode_value = 'PRACTICAL' AND length(btrim(p_location)) > 180)
           OR (mode_value = 'ONLINE' AND btrim(p_location) !~* '^https://[[:alnum:]][[:alnum:].-]*(:[0-9]+)?([/?#][^[:space:]]*)?$') THEN
            RAISE EXCEPTION 'A valid HTTPS URL or venue is required' USING ERRCODE = '22023';
        END IF;
    END IF;

    UPDATE app.course_session
       SET scheduled_at = p_scheduled_at,
           attendance_closes_at = cutoff,
           online_url = CASE
               WHEN p_location IS NULL THEN online_url
               WHEN mode_value = 'ONLINE' THEN btrim(p_location)
               ELSE NULL END,
           venue = CASE
               WHEN p_location IS NULL THEN venue
               WHEN mode_value = 'PRACTICAL' THEN btrim(p_location)
               ELSE NULL END
     WHERE id = p_session_id;

    UPDATE app.course
       SET start_date = schedule.first_time::DATE,
           start_time = schedule.first_time::TIME
      FROM (
          SELECT min(session.scheduled_at) AS first_time
            FROM app.course_session session
           WHERE session.course_id = owned_course_id
             AND session.status <> 'CANCELLED'
      ) schedule
     WHERE id = owned_course_id AND schedule.first_time IS NOT NULL;

    notice := app.create_notification_for_enrollees(
        chef_id, owned_course_id, 'SCHEDULE_CHANGED',
        p_notification_title, p_notification_message);
    RETURN notice;
END;
$$;

CREATE FUNCTION app.revoke_auth_session(p_session_token uuid) RETURNS void
    LANGUAGE sql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    DELETE FROM app.auth_session
     WHERE token_hash = sha256(convert_to(p_session_token::TEXT, 'UTF8'))
$$;

CREATE FUNCTION app.save_catalog_ingredient(p_token uuid, p_id uuid, p_name text, p_unit text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    saved UUID;
BEGIN
    IF app.authenticated_profile_id(p_token, 'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Chef required' USING ERRCODE = '42501';
    END IF;
    IF p_name IS NULL OR btrim(p_name) = '' OR length(btrim(p_name)) > 100
       OR p_unit IS NULL
       OR p_unit NOT IN ('GRAM', 'MILLILITER', 'PIECE', 'TEASPOON') THEN
        RAISE EXCEPTION 'Invalid ingredient' USING ERRCODE = '23514';
    END IF;
    IF p_id IS NULL THEN
        INSERT INTO app.ingredient(name, unit)
        VALUES(btrim(p_name), p_unit)
        RETURNING id INTO saved;
    ELSE
        PERFORM 1 FROM app.ingredient WHERE id = p_id FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Ingredient unavailable' USING ERRCODE = '23503';
        END IF;
        UPDATE app.ingredient SET name = btrim(p_name), unit = p_unit WHERE id = p_id;
        saved := p_id;
    END IF;
    RETURN saved;
END;
$$;

CREATE FUNCTION app.save_catalog_recipe(p_token uuid, p_id uuid, p_category uuid, p_name text, p_description text, p_servings integer, p_ingredients uuid[], p_quantities numeric[]) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    saved UUID;
BEGIN
    IF app.authenticated_profile_id(p_token, 'CHEF') IS NULL THEN
        RAISE EXCEPTION 'Chef required' USING ERRCODE = '42501';
    END IF;
    IF p_category IS NULL OR p_name IS NULL OR btrim(p_name) = ''
       OR length(btrim(p_name)) > 120
       OR p_description IS NULL OR btrim(p_description) = ''
       OR length(btrim(p_description)) > 4000
       OR p_servings IS NULL OR p_servings <> 1
       OR coalesce(cardinality(p_ingredients), 0) = 0
       OR cardinality(p_ingredients) IS DISTINCT FROM cardinality(p_quantities)
       OR EXISTS (
           SELECT 1 FROM unnest(p_quantities) q
           WHERE q IS NULL OR q <= 0 OR q >= 1000000000000 OR q <> round(q, 3)
       )
       OR EXISTS (SELECT 1 FROM unnest(p_ingredients) i WHERE i IS NULL)
       OR (SELECT count(DISTINCT i) FROM unnest(p_ingredients) i)
           <> cardinality(p_ingredients) THEN
        RAISE EXCEPTION 'Invalid recipe' USING ERRCODE = '23514';
    END IF;
    IF p_id IS NOT NULL THEN
        PERFORM 1 FROM app.recipe WHERE id = p_id FOR UPDATE;
        IF NOT FOUND THEN
            RAISE EXCEPTION 'Recipe unavailable' USING ERRCODE = '23503';
        END IF;
    END IF;
    PERFORM 1 FROM app.ingredient
    WHERE id = ANY(p_ingredients) ORDER BY id FOR UPDATE;
    IF p_id IS NULL THEN
        INSERT INTO app.recipe(category_id, name, description, base_servings)
        VALUES(p_category, btrim(p_name), btrim(p_description), p_servings)
        RETURNING id INTO saved;
    ELSE
        saved := p_id;
        UPDATE app.recipe
        SET category_id = p_category, name = btrim(p_name),
            description = btrim(p_description), base_servings = p_servings
        WHERE id = saved;
        DELETE FROM app.recipe_ingredient WHERE recipe_id = saved;
    END IF;
    INSERT INTO app.recipe_ingredient(recipe_id, ingredient_id, quantity)
    SELECT saved, i, q FROM unnest(p_ingredients, p_quantities) AS items(i, q);
    RETURN saved;
END;
$$;

CREATE FUNCTION app.saved_session_menu(p_token uuid, p_session uuid) RETURNS TABLE(recipe_id uuid, name text, description text, doses text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM app.course_session s JOIN app.course c ON c.id=s.course_id
        WHERE s.id=p_session AND c.chef_profile_id=app.authenticated_profile_id(p_token,'CHEF')) THEN
        RAISE EXCEPTION 'Session unavailable' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT m.recipe_id,m.recipe_name,m.description,
        (SELECT string_agg(i.ingredient_name || ': ' || i.quantity || ' ' ||
            CASE i.unit WHEN 'GRAM' THEN 'g' WHEN 'MILLILITER' THEN 'ml' WHEN 'PIECE' THEN 'pz' ELSE 'cucchiaini' END,E'\n' ORDER BY i.ingredient_name)
         FROM jsonb_to_recordset(m.ingredients) AS i(ingredient_name TEXT,quantity NUMERIC,unit TEXT))
    FROM app.session_menu_snapshot m WHERE m.session_id=p_session ORDER BY m.recipe_name,m.recipe_id;
END; $$;

CREATE FUNCTION app.send_course_notification(p_session_token uuid, p_course_id uuid, p_title text, p_message text) RETURNS uuid
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    chef_id UUID;
BEGIN
    chef_id := app.authenticated_profile_id(p_session_token, 'CHEF');
    IF chef_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Chef session required' USING ERRCODE = '42501';
    END IF;
    RETURN app.create_notification_for_enrollees(
        chef_id,
        p_course_id,
        'GENERAL',
        p_title,
        p_message
    );
END;
$$;

CREATE FUNCTION app.session_lifecycle(p_token uuid, p_session uuid) RETURNS TABLE(revision integer, state text, provenance text)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM app.course_session s JOIN app.course c ON c.id=s.course_id
        WHERE s.id=p_session AND c.chef_profile_id=app.authenticated_profile_id(p_token,'CHEF')) THEN
        RAISE EXCEPTION 'Session unavailable for this Chef' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT 0,
        CASE WHEN app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome')='SCHEDULED' THEN 'PRELIMINARY' ELSE app.effective_session_status(s.status::TEXT,s.scheduled_at,s.duration_minutes,statement_timestamp() AT TIME ZONE 'Europe/Rome') END,
        CASE WHEN EXISTS(SELECT 1 FROM app.session_menu_snapshot m WHERE m.session_id=s.id AND m.provenance='LEGACY_RECONSTRUCTED')
            THEN 'LEGACY_RECONSTRUCTED' ELSE 'CAPTURED' END
    FROM app.course_session s WHERE s.id=p_session;
END; $$;

CREATE FUNCTION app.session_planning_export_context(p_token uuid, p_session uuid) RETURNS TABLE(revision integer, state text, provenance text, scheduled_at timestamp without time zone)
    LANGUAGE sql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
    SELECT lifecycle.revision, lifecycle.state, lifecycle.provenance, session.scheduled_at
      FROM app.session_lifecycle(p_token, p_session) lifecycle
      JOIN app.course_session session ON session.id = p_session
$$;

CREATE FUNCTION app.session_preparation(p_session_token uuid, p_session_id uuid) RETURNS TABLE(confirmed_participants integer, pending_participants integer, declined_participants integer, ingredient_margin numeric, recipe_id uuid, recipe_name text, base_servings integer, portions_per_participant numeric, ingredient_id uuid, ingredient_name text, unit text, contribution_quantity numeric, required_quantity numeric)
    LANGUAGE plpgsql STABLE SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
BEGIN
    IF NOT EXISTS(SELECT 1 FROM app.course_session s JOIN app.course c ON c.id=s.course_id
        WHERE s.id=p_session_id AND s.mode='PRACTICAL'
        AND c.chef_profile_id=app.authenticated_profile_id(p_session_token,'CHEF')) THEN
        RAISE EXCEPTION 'Practical session unavailable for this Chef' USING ERRCODE='42501';
    END IF;
    RETURN QUERY SELECT * FROM app.preliminary_session_preparation(p_session_id);
END; $$;

CREATE FUNCTION app.set_practical_attendance(p_session_token uuid, p_session_id uuid, p_status text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    participant_id UUID;
    enrollment_identifier UUID;
    session_mode TEXT;
    session_state TEXT;
    cutoff TIMESTAMP;
    operation_time TIMESTAMP;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    IF p_status IS NULL OR p_status NOT IN ('CONFIRMED', 'DECLINED') THEN
        RAISE EXCEPTION 'Attendance status is invalid' USING ERRCODE = '22023';
    END IF;
    SELECT enrollment.id INTO enrollment_identifier
      FROM app.enrollment JOIN app.course_session ON course_session.course_id = enrollment.course_id
     WHERE enrollment.participant_profile_id = participant_id
       AND enrollment.status = 'ACTIVE' AND course_session.id = p_session_id
     FOR UPDATE OF enrollment;
    IF enrollment_identifier IS NULL THEN
        RAISE EXCEPTION 'An active enrollment is required' USING ERRCODE = '42501';
    END IF;
    SELECT mode, status, attendance_closes_at INTO session_mode, session_state, cutoff
      FROM app.course_session WHERE id = p_session_id FOR UPDATE;
    operation_time := clock_timestamp() AT TIME ZONE 'Europe/Rome';
    IF session_mode <> 'PRACTICAL' OR session_state <> 'SCHEDULED'
       OR cutoff <= operation_time THEN
        RAISE EXCEPTION 'Attendance changes require an open practical session' USING ERRCODE = '23514';
    END IF;
    IF p_status = 'CONFIRMED' AND NOT EXISTS (
        SELECT 1 FROM app.session_recipe WHERE session_id = p_session_id
    ) THEN
        RAISE EXCEPTION 'Practical session program requires at least one recipe before confirmation'
            USING ERRCODE = '23514';
    END IF;
    INSERT INTO app.practical_attendance(enrollment_id, session_id, status)
    VALUES (enrollment_identifier, p_session_id, p_status)
    ON CONFLICT (enrollment_id, session_id) DO UPDATE SET status = EXCLUDED.status;
END;
$$;

CREATE FUNCTION app.suspend_account(p_username text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE target_account_id UUID;
BEGIN
    SELECT id INTO target_account_id FROM app.account
     WHERE username = lower(btrim(p_username)) FOR UPDATE;
    IF target_account_id IS NULL THEN
        RAISE EXCEPTION 'Account unavailable' USING ERRCODE = '42501';
    END IF;
    UPDATE app.account SET active = false WHERE id = target_account_id;
    DELETE FROM app.auth_session WHERE account_id = target_account_id;
END;
$$;

CREATE FUNCTION app.withdraw_from_course(p_session_token uuid, p_course_id uuid) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'pg_catalog', 'app', 'pg_temp'
    AS $$
DECLARE
    participant_id UUID;
    enrollment_identifier UUID;
    operation_time TIMESTAMP;
BEGIN
    participant_id := app.authenticated_profile_id(p_session_token, 'PARTICIPANT');
    IF participant_id IS NULL THEN
        RAISE EXCEPTION 'Authenticated Participant session required' USING ERRCODE = '42501';
    END IF;
    SELECT id INTO enrollment_identifier FROM app.enrollment
     WHERE participant_profile_id = participant_id AND course_id = p_course_id FOR UPDATE;
    IF enrollment_identifier IS NULL THEN
        RETURN;
    END IF;
    PERFORM id FROM app.course_session WHERE course_id = p_course_id ORDER BY id FOR UPDATE;
    operation_time := clock_timestamp() AT TIME ZONE 'Europe/Rome';
    UPDATE app.enrollment SET status = 'WITHDRAWN', withdrawn_at = coalesce(withdrawn_at, clock_timestamp())
     WHERE id = enrollment_identifier;
    UPDATE app.practical_attendance attendance
       SET status = 'DECLINED', updated_at = clock_timestamp()
      FROM app.course_session session
     WHERE attendance.enrollment_id = enrollment_identifier
       AND attendance.session_id = session.id
       AND attendance.status = 'CONFIRMED'
       AND session.attendance_closes_at > operation_time;
END;
$$;

CREATE TABLE app.account (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    username character varying(50) NOT NULL,
    password_hash text NOT NULL,
    role character varying(20) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT account_password_hash_format CHECK ((password_hash ~~ '$pbkdf2-sha256$600000$%'::text)),
    CONSTRAINT account_role_valid CHECK (((role)::text = ANY ((ARRAY['CHEF'::character varying, 'PARTICIPANT'::character varying])::text[]))),
    CONSTRAINT account_username_format CHECK ((((username)::text = lower((username)::text)) AND ((username)::text ~ '^[a-z0-9._-]{3,50}$'::text)))
);

CREATE TABLE app.account_password_reset (
    token_hash bytea NOT NULL,
    account_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT account_password_reset_expiry_valid CHECK ((expires_at > created_at)),
    CONSTRAINT account_password_reset_used_after_creation CHECK (((used_at IS NULL) OR (used_at >= created_at)))
);

CREATE TABLE app.auth_session (
    token_hash bytea NOT NULL,
    account_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT auth_session_expiry_valid CHECK ((expires_at > created_at))
);

CREATE TABLE app.category (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(80) NOT NULL,
    description text NOT NULL,
    CONSTRAINT category_description_length_check CHECK ((char_length(btrim(description)) <= 500)),
    CONSTRAINT category_name_check CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT category_name_length_check CHECK (((char_length(btrim((name)::text)) >= 3) AND (char_length(btrim((name)::text)) <= 80)))
);

CREATE TABLE app.chef_invitation (
    token_hash bytea NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT chef_invitation_expiry_valid CHECK ((expires_at > created_at)),
    CONSTRAINT chef_invitation_used_after_creation CHECK (((used_at IS NULL) OR (used_at >= created_at)))
);

CREATE TABLE app.chef_profile (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    account_id uuid NOT NULL,
    display_name character varying(100) NOT NULL,
    CONSTRAINT chef_profile_display_name_check CHECK ((btrim((display_name)::text) <> ''::text))
);

CREATE TABLE app.course (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    chef_profile_id uuid NOT NULL,
    category_id uuid NOT NULL,
    title character varying(120) NOT NULL,
    description text NOT NULL,
    start_date date NOT NULL,
    start_time time without time zone NOT NULL,
    recurrence_days integer NOT NULL,
    session_count integer NOT NULL,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    ingredient_margin numeric DEFAULT 0 NOT NULL,
    CONSTRAINT course_description_valid CHECK ((btrim(description) <> ''::text)),
    CONSTRAINT course_ingredient_margin_valid CHECK (((ingredient_margin >= (0)::numeric) AND (ingredient_margin <= (25)::numeric))),
    CONSTRAINT course_no_ingredient_margin CHECK ((ingredient_margin = (0)::numeric)),
    CONSTRAINT course_recurrence_valid CHECK (((recurrence_days >= 1) AND (recurrence_days <= 31))),
    CONSTRAINT course_session_count_valid CHECK (((session_count >= 1) AND (session_count <= 30))),
    CONSTRAINT course_status_valid CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[]))),
    CONSTRAINT course_title_valid CHECK ((((title)::text = btrim((title)::text)) AND ((char_length((title)::text) >= 3) AND (char_length((title)::text) <= 30)) AND ((title)::text !~ E'[\r\n\u000B\f\u0085\u2028\u2029]'::text)))
);

CREATE TABLE app.course_session (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    course_id uuid NOT NULL,
    sequence_number integer NOT NULL,
    scheduled_at timestamp without time zone NOT NULL,
    mode character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    duration_minutes integer DEFAULT 120 NOT NULL,
    online_url text,
    venue character varying(180),
    capacity integer,
    attendance_closes_at timestamp without time zone,
    completed_at timestamp with time zone,
    CONSTRAINT course_session_attendance_cutoff_valid CHECK (((((mode)::text = 'ONLINE'::text) AND (attendance_closes_at IS NULL)) OR (((mode)::text = 'PRACTICAL'::text) AND (attendance_closes_at IS NOT NULL) AND (attendance_closes_at < scheduled_at)))),
    CONSTRAINT course_session_duration_valid CHECK (((duration_minutes >= 1) AND (duration_minutes <= 720))),
    CONSTRAINT course_session_mode_check CHECK (((mode)::text = ANY ((ARRAY['ONLINE'::character varying, 'PRACTICAL'::character varying])::text[]))),
    CONSTRAINT course_session_mode_fields_valid CHECK (((((mode)::text = 'ONLINE'::text) AND (online_url IS NOT NULL) AND (online_url ~* '^https://[[:alnum:]][[:alnum:].-]*(:[0-9]+)?([/?#][^[:space:]]*)?$'::text) AND (venue IS NULL) AND (capacity IS NULL)) OR (((mode)::text = 'PRACTICAL'::text) AND (online_url IS NULL) AND (venue IS NOT NULL) AND ((venue)::text = btrim((venue)::text)) AND (btrim((venue)::text) <> ''::text) AND (capacity IS NOT NULL) AND (capacity > 0)))),
    CONSTRAINT course_session_sequence_number_check CHECK ((sequence_number > 0)),
    CONSTRAINT course_session_status_check CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);

CREATE TABLE app.enrollment (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    participant_profile_id uuid NOT NULL,
    course_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    enrolled_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    withdrawn_at timestamp with time zone,
    CONSTRAINT enrollment_status_valid CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'WITHDRAWN'::character varying])::text[]))),
    CONSTRAINT enrollment_withdrawal_valid CHECK (((((status)::text = 'ACTIVE'::text) AND (withdrawn_at IS NULL)) OR (((status)::text = 'WITHDRAWN'::text) AND (withdrawn_at IS NOT NULL))))
);

CREATE TABLE app.ingredient (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying(100) NOT NULL,
    unit character varying(20) NOT NULL,
    CONSTRAINT ingredient_name_check CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT ingredient_unit_valid CHECK (((unit)::text = ANY ((ARRAY['GRAM'::character varying, 'MILLILITER'::character varying, 'PIECE'::character varying, 'TEASPOON'::character varying])::text[])))
);

CREATE TABLE app.notification (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    chef_profile_id uuid NOT NULL,
    course_id uuid,
    title character varying(120) NOT NULL,
    message character varying(2000) NOT NULL,
    reason character varying(30) NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT notification_message_valid CHECK ((((message)::text = btrim((message)::text)) AND ((char_length((message)::text) >= 3) AND (char_length((message)::text) <= 2000)))),
    CONSTRAINT notification_reason_valid CHECK (((reason)::text = ANY ((ARRAY['SCHEDULE_CHANGED'::character varying, 'SESSION_CANCELLED'::character varying, 'SESSION_ADDED'::character varying, 'GENERAL'::character varying])::text[]))),
    CONSTRAINT notification_title_valid CHECK ((((title)::text = btrim((title)::text)) AND ((char_length((title)::text) >= 3) AND (char_length((title)::text) <= 120))))
);

CREATE TABLE app.notification_recipient (
    notification_id uuid NOT NULL,
    participant_profile_id uuid NOT NULL,
    read_at timestamp with time zone
);

CREATE TABLE app.participant_profile (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    account_id uuid NOT NULL,
    display_name character varying(100) NOT NULL,
    CONSTRAINT participant_profile_display_name_check CHECK ((btrim((display_name)::text) <> ''::text))
);

CREATE TABLE app.practical_attendance (
    enrollment_id uuid NOT NULL,
    session_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    updated_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT practical_attendance_status_valid CHECK (((status)::text = ANY ((ARRAY['CONFIRMED'::character varying, 'DECLINED'::character varying])::text[])))
);

CREATE TABLE app.recipe (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    category_id uuid NOT NULL,
    name character varying(120) NOT NULL,
    description text NOT NULL,
    base_servings integer NOT NULL,
    CONSTRAINT recipe_base_servings_check CHECK ((base_servings > 0)),
    CONSTRAINT recipe_description_check CHECK ((btrim(description) <> ''::text)),
    CONSTRAINT recipe_name_check CHECK ((btrim((name)::text) <> ''::text)),
    CONSTRAINT recipe_single_portion CHECK ((base_servings = 1))
);

CREATE TABLE app.recipe_ingredient (
    recipe_id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    quantity numeric NOT NULL,
    CONSTRAINT recipe_ingredient_quantity_check CHECK ((quantity > (0)::numeric)),
    CONSTRAINT recipe_quantity_precision CHECK (((quantity > (0)::numeric) AND (quantity < ('1000000000000'::bigint)::numeric) AND (quantity = round(quantity, 3))))
);

CREATE TABLE app.schema_metadata (
    component text NOT NULL,
    installed_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);

CREATE TABLE app.session_change_history (
    id bigint NOT NULL,
    session_id uuid NOT NULL,
    changed_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    previous_schedule timestamp without time zone NOT NULL,
    next_schedule timestamp without time zone NOT NULL,
    previous_status text NOT NULL,
    next_status text NOT NULL,
    previous_attendance jsonb NOT NULL
);

ALTER TABLE app.session_change_history ALTER COLUMN id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME app.session_change_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE app.session_menu_snapshot (
    session_id uuid NOT NULL,
    recipe_id uuid NOT NULL,
    recipe_name text NOT NULL,
    description text NOT NULL,
    ingredients jsonb NOT NULL,
    captured_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    provenance text NOT NULL,
    CONSTRAINT session_menu_snapshot_provenance_check CHECK ((provenance = ANY (ARRAY['CAPTURED'::text, 'LEGACY_RECONSTRUCTED'::text])))
);

CREATE TABLE app.session_outcome (
    session_id uuid NOT NULL,
    recorded_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    recipe_ids uuid[] NOT NULL,
    enrollment_ids uuid[],
    note text NOT NULL,
    CONSTRAINT session_outcome_note_check CHECK ((length(btrim(note)) <= 2000))
);

CREATE TABLE app.session_preparation_revision (
    session_id uuid NOT NULL,
    revision integer NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    reason text NOT NULL,
    preparation jsonb NOT NULL,
    confirmed_enrollments uuid[] NOT NULL,
    active boolean DEFAULT true NOT NULL,
    CONSTRAINT session_preparation_revision_reason_check CHECK (((length(btrim(reason)) >= 1) AND (length(btrim(reason)) <= 2000))),
    CONSTRAINT session_preparation_revision_revision_check CHECK ((revision > 0))
);

CREATE TABLE app.session_recipe (
    session_id uuid NOT NULL,
    recipe_id uuid NOT NULL,
    portions_per_participant numeric DEFAULT 1 NOT NULL,
    CONSTRAINT session_recipe_portions_valid CHECK ((portions_per_participant > (0)::numeric)),
    CONSTRAINT session_recipe_single_portion CHECK ((portions_per_participant = (1)::numeric))
);

ALTER TABLE ONLY app.account_password_reset
    ADD CONSTRAINT account_password_reset_pkey PRIMARY KEY (token_hash);

ALTER TABLE ONLY app.account
    ADD CONSTRAINT account_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.account
    ADD CONSTRAINT account_username_key UNIQUE (username);

ALTER TABLE ONLY app.auth_session
    ADD CONSTRAINT auth_session_pkey PRIMARY KEY (token_hash);

ALTER TABLE ONLY app.category
    ADD CONSTRAINT category_name_key UNIQUE (name);

ALTER TABLE ONLY app.category
    ADD CONSTRAINT category_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.chef_invitation
    ADD CONSTRAINT chef_invitation_pkey PRIMARY KEY (token_hash);

ALTER TABLE ONLY app.chef_profile
    ADD CONSTRAINT chef_profile_account_id_key UNIQUE (account_id);

ALTER TABLE ONLY app.chef_profile
    ADD CONSTRAINT chef_profile_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.course
    ADD CONSTRAINT course_natural_key UNIQUE (chef_profile_id, title, start_date);

ALTER TABLE ONLY app.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.course_session
    ADD CONSTRAINT course_session_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.course_session
    ADD CONSTRAINT course_session_sequence_unique UNIQUE (course_id, sequence_number);

ALTER TABLE ONLY app.enrollment
    ADD CONSTRAINT enrollment_participant_course_unique UNIQUE (participant_profile_id, course_id);

ALTER TABLE ONLY app.enrollment
    ADD CONSTRAINT enrollment_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.ingredient
    ADD CONSTRAINT ingredient_name_key UNIQUE (name);

ALTER TABLE ONLY app.ingredient
    ADD CONSTRAINT ingredient_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.notification
    ADD CONSTRAINT notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.notification_recipient
    ADD CONSTRAINT notification_recipient_pkey PRIMARY KEY (notification_id, participant_profile_id);

ALTER TABLE ONLY app.participant_profile
    ADD CONSTRAINT participant_profile_account_id_key UNIQUE (account_id);

ALTER TABLE ONLY app.participant_profile
    ADD CONSTRAINT participant_profile_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.practical_attendance
    ADD CONSTRAINT practical_attendance_pkey PRIMARY KEY (enrollment_id, session_id);

ALTER TABLE ONLY app.recipe_ingredient
    ADD CONSTRAINT recipe_ingredient_pkey PRIMARY KEY (recipe_id, ingredient_id);

ALTER TABLE ONLY app.recipe
    ADD CONSTRAINT recipe_name_key UNIQUE (name);

ALTER TABLE ONLY app.recipe
    ADD CONSTRAINT recipe_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.schema_metadata
    ADD CONSTRAINT schema_metadata_pkey PRIMARY KEY (component);

ALTER TABLE ONLY app.session_change_history
    ADD CONSTRAINT session_change_history_pkey PRIMARY KEY (id);

ALTER TABLE ONLY app.session_menu_snapshot
    ADD CONSTRAINT session_menu_snapshot_pkey PRIMARY KEY (session_id, recipe_id);

ALTER TABLE ONLY app.session_outcome
    ADD CONSTRAINT session_outcome_pkey PRIMARY KEY (session_id);

ALTER TABLE ONLY app.session_preparation_revision
    ADD CONSTRAINT session_preparation_revision_pkey PRIMARY KEY (session_id, revision);

ALTER TABLE ONLY app.session_recipe
    ADD CONSTRAINT session_recipe_pkey PRIMARY KEY (session_id, recipe_id);

CREATE INDEX account_password_reset_account_idx ON app.account_password_reset USING btree (account_id);

CREATE INDEX auth_session_account_idx ON app.auth_session USING btree (account_id);

CREATE UNIQUE INDEX category_name_case_insensitive_unique ON app.category USING btree (lower((name)::text));

CREATE INDEX enrollment_course_status_idx ON app.enrollment USING btree (course_id, status);

CREATE INDEX notification_chef_created_idx ON app.notification USING btree (chef_profile_id, created_at DESC);

CREATE INDEX notification_course_created_idx ON app.notification USING btree (course_id, created_at DESC) WHERE (course_id IS NOT NULL);

CREATE INDEX notification_recipient_participant_idx ON app.notification_recipient USING btree (participant_profile_id, read_at, notification_id);

CREATE INDEX practical_attendance_session_status_idx ON app.practical_attendance USING btree (session_id, status);

CREATE UNIQUE INDEX session_preparation_active ON app.session_preparation_revision USING btree (session_id) WHERE active;

CREATE TRIGGER account_profile_role_guard BEFORE UPDATE OF role ON app.account FOR EACH ROW EXECUTE FUNCTION app.enforce_account_profile_role();

CREATE TRIGGER capture_session_recipe AFTER INSERT ON app.session_recipe FOR EACH ROW EXECUTE FUNCTION app.capture_session_recipe();

CREATE TRIGGER chef_profile_role_guard BEFORE INSERT OR UPDATE OF account_id ON app.chef_profile FOR EACH ROW EXECUTE FUNCTION app.enforce_profile_role();

CREATE TRIGGER chef_session_overlap_guard BEFORE INSERT OR UPDATE OF course_id, scheduled_at, duration_minutes, status ON app.course_session FOR EACH ROW EXECUTE FUNCTION app.prevent_chef_session_overlap();

CREATE TRIGGER course_session_attendance_cutoff_default BEFORE INSERT OR UPDATE OF scheduled_at, mode, attendance_closes_at ON app.course_session FOR EACH ROW EXECUTE FUNCTION app.default_practical_attendance_cutoff();

CREATE TRIGGER lock_assigned_recipe_catalog BEFORE INSERT OR UPDATE ON app.session_recipe FOR EACH ROW EXECUTE FUNCTION app.lock_assigned_recipe_catalog();

CREATE TRIGGER participant_profile_role_guard BEFORE INSERT OR UPDATE OF account_id ON app.participant_profile FOR EACH ROW EXECUTE FUNCTION app.enforce_profile_role();

CREATE TRIGGER practical_attendance_guard BEFORE INSERT OR UPDATE OF enrollment_id, session_id, status ON app.practical_attendance FOR EACH ROW EXECUTE FUNCTION app.enforce_practical_attendance();

CREATE TRIGGER preserve_recipe_ingredient_unit BEFORE UPDATE OF unit ON app.ingredient FOR EACH ROW EXECUTE FUNCTION app.preserve_recipe_ingredient_unit();

CREATE TRIGGER preserve_session_change BEFORE UPDATE ON app.course_session FOR EACH ROW EXECUTE FUNCTION app.preserve_session_change();

CREATE TRIGGER session_recipe_practical_guard BEFORE INSERT OR UPDATE OF session_id ON app.session_recipe FOR EACH ROW EXECUTE FUNCTION app.enforce_practical_session_recipe();

ALTER TABLE ONLY app.account_password_reset
    ADD CONSTRAINT account_password_reset_account_id_fkey FOREIGN KEY (account_id) REFERENCES app.account(id);

ALTER TABLE ONLY app.auth_session
    ADD CONSTRAINT auth_session_account_id_fkey FOREIGN KEY (account_id) REFERENCES app.account(id) ON DELETE CASCADE;

ALTER TABLE ONLY app.chef_profile
    ADD CONSTRAINT chef_profile_account_id_fkey FOREIGN KEY (account_id) REFERENCES app.account(id);

ALTER TABLE ONLY app.course
    ADD CONSTRAINT course_category_id_fkey FOREIGN KEY (category_id) REFERENCES app.category(id);

ALTER TABLE ONLY app.course
    ADD CONSTRAINT course_chef_profile_id_fkey FOREIGN KEY (chef_profile_id) REFERENCES app.chef_profile(id);

ALTER TABLE ONLY app.course_session
    ADD CONSTRAINT course_session_course_id_fkey FOREIGN KEY (course_id) REFERENCES app.course(id) ON DELETE CASCADE;

ALTER TABLE ONLY app.enrollment
    ADD CONSTRAINT enrollment_course_id_fkey FOREIGN KEY (course_id) REFERENCES app.course(id);

ALTER TABLE ONLY app.enrollment
    ADD CONSTRAINT enrollment_participant_profile_id_fkey FOREIGN KEY (participant_profile_id) REFERENCES app.participant_profile(id);

ALTER TABLE ONLY app.notification
    ADD CONSTRAINT notification_chef_profile_id_fkey FOREIGN KEY (chef_profile_id) REFERENCES app.chef_profile(id);

ALTER TABLE ONLY app.notification
    ADD CONSTRAINT notification_course_id_fkey FOREIGN KEY (course_id) REFERENCES app.course(id);

ALTER TABLE ONLY app.notification_recipient
    ADD CONSTRAINT notification_recipient_notification_id_fkey FOREIGN KEY (notification_id) REFERENCES app.notification(id) ON DELETE CASCADE;

ALTER TABLE ONLY app.notification_recipient
    ADD CONSTRAINT notification_recipient_participant_profile_id_fkey FOREIGN KEY (participant_profile_id) REFERENCES app.participant_profile(id);

ALTER TABLE ONLY app.participant_profile
    ADD CONSTRAINT participant_profile_account_id_fkey FOREIGN KEY (account_id) REFERENCES app.account(id);

ALTER TABLE ONLY app.practical_attendance
    ADD CONSTRAINT practical_attendance_enrollment_id_fkey FOREIGN KEY (enrollment_id) REFERENCES app.enrollment(id);

ALTER TABLE ONLY app.practical_attendance
    ADD CONSTRAINT practical_attendance_session_id_fkey FOREIGN KEY (session_id) REFERENCES app.course_session(id);

ALTER TABLE ONLY app.recipe
    ADD CONSTRAINT recipe_category_id_fkey FOREIGN KEY (category_id) REFERENCES app.category(id);

ALTER TABLE ONLY app.recipe_ingredient
    ADD CONSTRAINT recipe_ingredient_ingredient_id_fkey FOREIGN KEY (ingredient_id) REFERENCES app.ingredient(id);

ALTER TABLE ONLY app.recipe_ingredient
    ADD CONSTRAINT recipe_ingredient_recipe_id_fkey FOREIGN KEY (recipe_id) REFERENCES app.recipe(id) ON DELETE CASCADE;

ALTER TABLE ONLY app.session_change_history
    ADD CONSTRAINT session_change_history_session_id_fkey FOREIGN KEY (session_id) REFERENCES app.course_session(id);

ALTER TABLE ONLY app.session_menu_snapshot
    ADD CONSTRAINT session_menu_snapshot_session_id_recipe_id_fkey FOREIGN KEY (session_id, recipe_id) REFERENCES app.session_recipe(session_id, recipe_id) ON DELETE CASCADE;

ALTER TABLE ONLY app.session_outcome
    ADD CONSTRAINT session_outcome_session_id_fkey FOREIGN KEY (session_id) REFERENCES app.course_session(id);

ALTER TABLE ONLY app.session_preparation_revision
    ADD CONSTRAINT session_preparation_revision_session_id_fkey FOREIGN KEY (session_id) REFERENCES app.course_session(id);

ALTER TABLE ONLY app.session_recipe
    ADD CONSTRAINT session_recipe_recipe_id_fkey FOREIGN KEY (recipe_id) REFERENCES app.recipe(id);

ALTER TABLE ONLY app.session_recipe
    ADD CONSTRAINT session_recipe_session_id_fkey FOREIGN KEY (session_id) REFERENCES app.course_session(id) ON DELETE CASCADE;

GRANT USAGE ON SCHEMA app TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.add_course_session(p_session_token uuid, p_course_id uuid, p_scheduled_at timestamp without time zone, p_mode text, p_duration_minutes integer, p_online_url text, p_venue text, p_capacity integer, p_attendance_closes_at timestamp without time zone, p_notification_title text, p_notification_message text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.add_course_session(p_session_token uuid, p_course_id uuid, p_scheduled_at timestamp without time zone, p_mode text, p_duration_minutes integer, p_online_url text, p_venue text, p_capacity integer, p_attendance_closes_at timestamp without time zone, p_notification_title text, p_notification_message text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.assign_recipe_to_session(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.assign_recipe_to_session(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.assign_recipe_to_session_for_chef(p_chef_profile_id uuid, p_session_id uuid, p_recipe_id uuid) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.authenticate_user(p_username text, p_derived_key bytea) FROM PUBLIC;
GRANT ALL ON FUNCTION app.authenticate_user(p_username text, p_derived_key bytea) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.authenticated_profile_id(p_session_token uuid, p_required_role text) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.cancel_course_session(p_session_token uuid, p_session_id uuid, p_notification_title text, p_notification_message text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.cancel_course_session(p_session_token uuid, p_session_id uuid, p_notification_title text, p_notification_message text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.capture_session_recipe() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.catalog_recipe(p_token uuid, p_recipe uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.catalog_recipe(p_token uuid, p_recipe uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.catalog_recipe_counts(p_token uuid, p_category uuid, p_search text, p_menu boolean) FROM PUBLIC;
GRANT ALL ON FUNCTION app.catalog_recipe_counts(p_token uuid, p_category uuid, p_search text, p_menu boolean) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.change_account_password(p_session_token uuid, p_current_proof bytea, p_new_password_hash text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.change_account_password(p_session_token uuid, p_current_proof bytea, p_new_password_hash text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.chef_course_sessions_batch(p_token uuid, p_ids uuid[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.chef_course_sessions_batch(p_token uuid, p_ids uuid[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.chef_monthly_report(p_session_token uuid, p_year integer, p_month integer) FROM PUBLIC;
GRANT ALL ON FUNCTION app.chef_monthly_report(p_session_token uuid, p_year integer, p_month integer) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.chef_monthly_report_details(p_token uuid, p_year integer, p_month integer) FROM PUBLIC;
GRANT ALL ON FUNCTION app.chef_monthly_report_details(p_token uuid, p_year integer, p_month integer) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.count_notification_recipients(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.count_notification_recipients(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.create_category(p_session_token uuid, p_name text, p_description text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.create_category(p_session_token uuid, p_name text, p_description text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.create_course(p_chef_profile_id uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_session_modes text[]) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.create_course(p_session_token uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_ingredient_margin numeric, p_session_modes text[], p_duration_minutes integer[], p_online_urls text[], p_venues text[], p_capacities integer[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.create_course(p_session_token uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_ingredient_margin numeric, p_session_modes text[], p_duration_minutes integer[], p_online_urls text[], p_venues text[], p_capacities integer[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.create_course_for_chef(p_chef_profile_id uuid, p_category_id uuid, p_title text, p_description text, p_start_date date, p_start_time time without time zone, p_recurrence_days integer, p_ingredient_margin numeric, p_session_modes text[], p_duration_minutes integer[], p_online_urls text[], p_venues text[], p_capacities integer[]) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.create_notification_for_enrollees(p_chef_profile_id uuid, p_course_id uuid, p_reason text, p_title text, p_message text) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.default_practical_attendance_cutoff() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.disable_account(p_username text) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.effective_session_status(p_status text, p_scheduled_at timestamp without time zone, p_duration_minutes integer, p_as_of timestamp without time zone) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.enforce_account_profile_role() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.enforce_practical_attendance() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.enforce_practical_session_recipe() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.enforce_profile_role() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.enroll_in_course(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.enroll_in_course(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.get_password_parameters(p_username text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.get_password_parameters(p_username text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_catalog_categories(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_catalog_categories(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_catalog_recipes(p_session_token uuid, p_category_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_catalog_recipes(p_session_token uuid, p_category_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_catalog_recipes_with_usage(p_token uuid, p_category uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_catalog_recipes_with_usage(p_token uuid, p_category uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_chef_courses(p_session_token uuid, p_category_id uuid, p_status text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_chef_courses(p_session_token uuid, p_category_id uuid, p_status text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_chef_notifications(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_chef_notifications(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_chef_notifications_with_course_context(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_chef_notifications_with_course_context(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_course_participants(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_course_participants(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_course_sessions(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_course_sessions(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_enrollment_practical_sessions(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_enrollment_practical_sessions(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_managed_ingredients(p_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_managed_ingredients(p_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_managed_ingredients_with_usage(p_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_managed_ingredients_with_usage(p_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_managed_recipe_ingredients(p_token uuid, p_recipe uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_managed_recipe_ingredients(p_token uuid, p_recipe uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_participant_commitments(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_participant_commitments(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_participant_enrollments(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_participant_enrollments(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_participant_enrollments_by_ids(p_session_token uuid, p_course_ids uuid[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_participant_enrollments_by_ids(p_session_token uuid, p_course_ids uuid[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_participant_enrollments_page(p_session_token uuid, p_category_id uuid, p_search text, p_period text, p_now timestamp without time zone, p_limit integer, p_offset integer) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_participant_enrollments_page(p_session_token uuid, p_category_id uuid, p_search text, p_period text, p_now timestamp without time zone, p_limit integer, p_offset integer) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_participant_notifications(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_participant_notifications(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_published_course_sessions(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_published_course_sessions(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_published_courses(p_session_token uuid, p_category_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_published_courses(p_session_token uuid, p_category_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_published_session_menu(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_published_session_menu(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_published_session_menu_doses(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_published_session_menu_doses(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_session_ingredient_requirements(p_session_token uuid, p_session_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_session_ingredient_requirements(p_session_token uuid, p_session_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.list_session_participants(p_session_token uuid, p_session_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.list_session_participants(p_session_token uuid, p_session_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.lock_assigned_recipe_catalog() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.mark_notification_read(p_session_token uuid, p_notification_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.mark_notification_read(p_session_token uuid, p_notification_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.open_auth_session(p_account_id uuid) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.page_catalog_recipes(p_token uuid, p_category uuid, p_search text, p_menu boolean, p_size integer, p_offset integer) FROM PUBLIC;
GRANT ALL ON FUNCTION app.page_catalog_recipes(p_token uuid, p_category uuid, p_search text, p_menu boolean, p_size integer, p_offset integer) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.page_chef_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.page_chef_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.page_published_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.page_published_courses(p_token uuid, p_category uuid, p_search text, p_period text, p_now timestamp without time zone, p_size integer, p_offset integer, p_ids uuid[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.preliminary_session_preparation(p_session uuid) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.preserve_recipe_ingredient_unit() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.preserve_session_change() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.prevent_chef_session_overlap() FROM PUBLIC;

REVOKE ALL ON FUNCTION app.publish_course(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.publish_course(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.publish_course_for_chef(p_chef_profile_id uuid, p_course_id uuid) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.published_course_sessions_batch(p_token uuid, p_ids uuid[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.published_course_sessions_batch(p_token uuid, p_ids uuid[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.refresh_session_menu(p_token uuid, p_session uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.refresh_session_menu(p_token uuid, p_session uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.register_chef(p_username text, p_display_name text, p_password_hash text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.register_chef(p_username text, p_display_name text, p_password_hash text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.register_participant(p_username text, p_display_name text, p_password_hash text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.register_participant(p_username text, p_display_name text, p_password_hash text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.remove_session_recipe(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.remove_session_recipe(p_session_token uuid, p_session_id uuid, p_recipe_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.require_owned_session(p_token uuid, p_session uuid) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.reschedule_course_session(p_session_token uuid, p_session_id uuid, p_scheduled_at timestamp without time zone, p_attendance_closes_at timestamp without time zone, p_location text, p_notification_title text, p_notification_message text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.reschedule_course_session(p_session_token uuid, p_session_id uuid, p_scheduled_at timestamp without time zone, p_attendance_closes_at timestamp without time zone, p_location text, p_notification_title text, p_notification_message text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.revoke_auth_session(p_session_token uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.revoke_auth_session(p_session_token uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.save_catalog_ingredient(p_token uuid, p_id uuid, p_name text, p_unit text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.save_catalog_ingredient(p_token uuid, p_id uuid, p_name text, p_unit text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.save_catalog_recipe(p_token uuid, p_id uuid, p_category uuid, p_name text, p_description text, p_servings integer, p_ingredients uuid[], p_quantities numeric[]) FROM PUBLIC;
GRANT ALL ON FUNCTION app.save_catalog_recipe(p_token uuid, p_id uuid, p_category uuid, p_name text, p_description text, p_servings integer, p_ingredients uuid[], p_quantities numeric[]) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.saved_session_menu(p_token uuid, p_session uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.saved_session_menu(p_token uuid, p_session uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.send_course_notification(p_session_token uuid, p_course_id uuid, p_title text, p_message text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.send_course_notification(p_session_token uuid, p_course_id uuid, p_title text, p_message text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.session_lifecycle(p_token uuid, p_session uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.session_lifecycle(p_token uuid, p_session uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.session_planning_export_context(p_token uuid, p_session uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.session_planning_export_context(p_token uuid, p_session uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.session_preparation(p_session_token uuid, p_session_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.session_preparation(p_session_token uuid, p_session_id uuid) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.set_practical_attendance(p_session_token uuid, p_session_id uuid, p_status text) FROM PUBLIC;
GRANT ALL ON FUNCTION app.set_practical_attendance(p_session_token uuid, p_session_id uuid, p_status text) TO uninafoodlab_app;

REVOKE ALL ON FUNCTION app.suspend_account(p_username text) FROM PUBLIC;

REVOKE ALL ON FUNCTION app.withdraw_from_course(p_session_token uuid, p_course_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION app.withdraw_from_course(p_session_token uuid, p_course_id uuid) TO uninafoodlab_app;

GRANT SELECT ON TABLE app.schema_metadata TO uninafoodlab_app;

INSERT INTO app.schema_metadata(component) VALUES ('foundation');

SET check_function_bodies = true;
