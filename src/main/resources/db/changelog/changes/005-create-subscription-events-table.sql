--liquibase formatted sql

--changeset subscription-service:11
CREATE TABLE subscription_events (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);

--changeset subscription-service:12
CREATE INDEX idx_events_unpublished
    ON subscription_events(published_at)
    WHERE published_at IS NULL;
