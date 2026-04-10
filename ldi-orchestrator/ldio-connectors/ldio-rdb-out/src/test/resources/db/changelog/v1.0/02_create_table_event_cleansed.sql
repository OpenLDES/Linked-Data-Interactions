CREATE TABLE event_cleansed
(
    event                   varchar(4096),
    version_of              varchar(4096),
    generated               timestamp with time zone,
    end_time                timestamp with time zone,
    start_time              timestamp with time zone,
    has_feature_of_interest varchar(4096),
    made_by_sensor          varchar(4096),
    modified_at             timestamp with time zone,
    status                  varchar(256),
    end_timestamp_known     bit
);
