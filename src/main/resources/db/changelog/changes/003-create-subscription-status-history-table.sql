--liquibase formatted sql

--changeset subscription-service:7
CREATE TABLE subscription_status_history (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    from_status VARCHAR(50),
    to_status VARCHAR(50) NOT NULL,
    reason VARCHAR(255) NOT NULL,
    changed_by VARCHAR(100) NOT NULL,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

--changeset subscription-service:8
CREATE INDEX idx_status_history_subscription
    ON subscription_status_history(subscription_id, changed_at);
