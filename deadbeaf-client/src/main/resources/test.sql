CREATE
MATERIALIZED VIEW IF NOT EXISTS stage.sms_events
ENGINE=MergeTree()
        ORDER BY (date, event_action)
        PARTITION BY (toYYYYMM(date))
        POPULATE
        AS
SELECT ifNull(date, toDate(now('America/Los_Angeles'))) as date,
       release_env,
       client_type,
       user_agent,
       device_type,
       event_id,
       event_action,
       event_label,
       created_at,
       JSONExtractString(event_metadata, 'id')                                         AS sms_delivery_id,
       JSONExtractString(event_metadata, 'trackingId')                                 AS tracking_id,
       JSONExtractString(event_metadata, 'from')                                       AS from_number,
       JSONExtractString(event_metadata, 'to')                                         AS to_number,
       JSONExtractString(event_metadata, 'vendor')                                     AS vendor,
       JSONExtractString(event_metadata, 'msg')                                        AS msg,
       JSONExtractString(event_metadata, 'usageType')                                  AS usage_type,
       JSONExtractString(event_metadata, 'invoker')                                    AS invoker,
       JSONExtractUInt(event_metadata, 'attempts')                                     AS attempts,
       fromUnixTimestamp(JSONExtract(event_metadata, 'timestamp', 'Nullable(UInt64)')) AS event_time,
       JSONExtractRaw(event_metadata, 'mediaUrls')                                     AS media_urls,
       JSONExtractString(event_metadata, 'errorCode')                                  AS error_code,
       JSONExtractString(event_metadata, 'sid')                                        AS sid
FROM stage.track_kafka
WHERE event_category = 'SMS' AND date >='2022-09-25';


WITH e AS (SELECT tracking_id
                , CASE
                      WHEN {{unit}}='week' then formatDateTime(toMonday(date), '%Y-%m-%d,%V')
                      WHEN {{unit}}='month' then formatDateTime(toStartOfMonth(date), '%Y-%m')
                      ELSE formatDateTime(toStartOfDay(date), '%Y-%m-%d') END AS start_of_time_unit
                , IF(empty(invoker), '-', invoker)                            AS invoker
                , IF(empty(vendor), '-', vendor)                              AS vendor
                , row_number()                                                   over w AS row_number
   , toUnixTimestamp(created_at)
    AS time
   , last_value(toUnixTimestamp(created_at)) over w AS end_time
   , event_action AS state
   , last_value(event_action) over w AS final_state
FROM events.sms_events
WHERE notEmpty(tracking_id)
  AND event_action in ('Submitted'
    , 'Delivered'
    , 'Failed')
  AND CASE WHEN {{unit}}='week' THEN date >= toMonday(date_add(WEEK
    , 1 - {{duration_number}}
    , now())) WHEN {{unit}}='month' THEN date >= toStartOfMonth(date_add(MONTH
    , 1 - {{duration_number}}
    , now())) ELSE date >= date_add(DAY
    , 1 - {{duration_number}}
    , now())
END
    WINDOW
w AS (PARTITION BY tracking_id ORDER BY created_at ASC ROWS BETWEEN CURRENT ROW
  AND UNBOUNDED FOLLOWING)
    )
SELECT e.start_of_time_unit,
       e.invoker,
       e.vendor,
       count(1)                                                    AS `submitted_#`,
       sum(if(e.final_state = 'Delivered', 1, 0))                  AS `delivered_#`,
       sum(if(e.final_state = 'Delivered', 1, 0)) / count(1) * 100 AS `delivered_%`,
       avg(e.end_time - e.time)                                    AS `delivery_seconds_avg`,
       quantileExact(0.5)(e.end_time - e.time) AS `delivery_seconds_p50`, quantileExact(0.95) (e.end_time - e.time) AS `delivery_seconds_p95`
FROM e
WHERE e.state = 'Submitted'
GROUP BY e.start_of_time_unit, e.invoker, e.vendor
ORDER BY e.start_of_time_unit DESC, e.invoker, e.vendor SETTINGS allow_experimental_window_functions = 1

WITH e AS (
    SELECT date,
    event_time AS submitted_time,
    tracking_id,
    invoker,
    event_action AS state,
    last_value(event_action) OVER w AS final_state,
    last_value(event_label) OVER w AS event_label,
    last_value(error_code) OVER w AS error_code,
    last_value(sid) OVER w AS sid,
    sms_delivery_id,
    last_value(from_number) OVER w AS from_number,
    to_number,
    vendor,
    usage_type,
    attempts,
    msg,
    media_urls
    FROM events.sms_events
    WHERE notEmpty(tracking_id)
    AND vendor = 'twilio'
    AND date ='2022-10-05'
    WINDOW
    w AS (PARTITION BY tracking_id ORDER BY created_at ASC ROWS BETWEEN CURRENT ROW
    AND UNBOUNDED FOLLOWING)
    )
SELECT *
FROM e
WHERE e.state = 'Submitted'
ORDER BY e.submitted_time DESC LIMIT 1000
SETTINGS allow_experimental_window_functions = 1;

select toStartOfDay(now()),
       formatDateTime(toStartOfDay(now()), '%Y-%m-%d %H:%M'),
       formatDateTime(now('Asia/Shanghai'),
                      '%Y-%m-%d %H:%M'),
       formatDateTime(now('UTC'),
                      '%Y-%m-%d %H:%M'),
       toDate(now()),
       now();


ALTER TABLE events.track_rt DROP PARTITION toDate('2022-10-13');


INSERT INTO events.track_rt
SELECT DISTINCT
ON(event_id) *
FROM
    (
    SELECT
    release_env,
    client_type,
    event_id,
    event_category,
    event_action,
    event_label,
    date_add(HOUR, 7, created_at) AS created_at,
    session_id,
    user_id,
    client_ip,
    url,
    title,
    language,
    referrer,
    referrer_medium,
    referrer_name,
    referrer_uri,
    user_agent,
    browser_name,
    browser_version,
    device_type,
    device_model,
    device_vendor,
    os_name,
    os_version,
    device_id,
    event_metadata,
    dec_user_id,
    toDate(date_add(HOUR, 7, created_at)) AS `date`
    FROM events.track_opt
    WHERE (event_id NOT IN
    (
    SELECT event_id
    FROM events.track_rt
    WHERE date BETWEEN toDate('2022-10-12') AND toDate('2022-10-14')
    )
    ) AND date IN (toDate('2022-10-12'), toDate('2022-10-13'))
    AND toDate(date_add(HOUR, 7, created_at)) = '2022-10-13');

