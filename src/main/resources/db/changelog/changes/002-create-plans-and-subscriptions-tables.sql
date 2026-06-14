--liquibase formatted sql

--changeset subscription-service:2
CREATE TABLE plans (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    monthly_price NUMERIC(10,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'BRL',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

--changeset subscription-service:3
INSERT INTO plans (id, name, display_name, monthly_price, currency, active, created_at) VALUES
    (gen_random_uuid(), 'BASICO', 'Básico', 19.90, 'BRL', true, now()),
    (gen_random_uuid(), 'PREMIUM', 'Premium', 39.90, 'BRL', true, now()),
    (gen_random_uuid(), 'FAMILIA', 'Família', 59.90, 'BRL', true, now());

--changeset subscription-service:4
CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    plan_id UUID NOT NULL REFERENCES plans(id),
    price_at_purchase NUMERIC(10,2) NOT NULL,
    currency_at_purchase VARCHAR(3) NOT NULL DEFAULT 'BRL',
    status VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    expiration_date DATE NOT NULL,
    cancel_requested_at TIMESTAMP WITH TIME ZONE,
    suspended_at TIMESTAMP WITH TIME ZONE,
    failed_attempts INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

--changeset subscription-service:5
CREATE UNIQUE INDEX uq_active_subscription_per_user
    ON subscriptions(user_id)
    WHERE status IN ('ATIVA', 'PENDENTE_PAGAMENTO');

--changeset subscription-service:6
CREATE INDEX idx_subscriptions_due_renewal
    ON subscriptions(status, expiration_date);
