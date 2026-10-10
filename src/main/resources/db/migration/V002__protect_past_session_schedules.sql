CREATE OR REPLACE FUNCTION app.reschedule_course_session(p_session_token uuid, p_session_id uuid, p_scheduled_at timestamp without time zone, p_attendance_closes_at timestamp without time zone, p_location text, p_notification_title text, p_notification_message text) RETURNS uuid
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
       OR original_time <= operation_time THEN
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
