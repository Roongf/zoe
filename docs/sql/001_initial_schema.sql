-- 轻记 v1：PostgreSQL 15+ 初始化脚本。
-- 金额统一用整数分（amount_cents），避免浮点精度问题；时间统一使用 UTC。

create extension if not exists pgcrypto;

create table app_user (
    id uuid primary key default gen_random_uuid(),
    phone varchar(32) unique,
    email varchar(320) unique,
    display_name varchar(64) not null,
    avatar_url text,
    timezone varchar(64) not null default 'Asia/Shanghai',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint app_user_contact_check check (phone is not null or email is not null)
);

create table ledger (
    id uuid primary key default gen_random_uuid(),
    name varchar(64) not null,
    owner_id uuid not null references app_user(id),
    currency char(3) not null default 'CNY',
    monthly_budget_cents bigint check (monthly_budget_cents is null or monthly_budget_cents >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- 账本成员决定谁可以读取/录入同一本账，也为日后邀请和权限扩展留出位置。
create table ledger_member (
    ledger_id uuid not null references ledger(id) on delete cascade,
    user_id uuid not null references app_user(id) on delete cascade,
    role varchar(16) not null default 'member',
    joined_at timestamptz not null default now(),
    primary key (ledger_id, user_id),
    constraint ledger_member_role_check check (role in ('owner', 'member', 'viewer'))
);

-- 短期、一次性的邀请码；二维码和微信分享链接都只承载 invite_token。
create table ledger_invitation (
    id uuid primary key default gen_random_uuid(),
    ledger_id uuid not null references ledger(id) on delete cascade,
    inviter_id uuid not null references app_user(id),
    invite_token varchar(96) not null unique,
    intended_role varchar(16) not null default 'member',
    status varchar(16) not null default 'active',
    expires_at timestamptz not null,
    accepted_by uuid references app_user(id),
    accepted_at timestamptz,
    created_at timestamptz not null default now(),
    revoked_at timestamptz,
    constraint ledger_invitation_role_check check (intended_role in ('member', 'viewer')),
    constraint ledger_invitation_status_check check (status in ('active', 'accepted', 'revoked', 'expired'))
);
create index ledger_invitation_ledger_status_idx
    on ledger_invitation (ledger_id, status, expires_at desc);

create table category (
    id uuid primary key default gen_random_uuid(),
    ledger_id uuid references ledger(id) on delete cascade,
    name varchar(32) not null,
    entry_type varchar(16) not null,
    icon varchar(32) not null default '◦',
    color varchar(9) not null default '#295C49',
    sort_order integer not null default 0,
    is_system boolean not null default false,
    is_archived boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint category_entry_type_check check (entry_type in ('expense', 'income')),
    constraint category_name_per_ledger_unique unique nulls not distinct (ledger_id, entry_type, name)
);

create table transaction_record (
    id uuid primary key default gen_random_uuid(),
    ledger_id uuid not null references ledger(id) on delete cascade,
    category_id uuid references category(id) on delete set null,
    created_by uuid not null references app_user(id),
    paid_by uuid not null references app_user(id),
    entry_type varchar(16) not null,
    amount_cents bigint not null check (amount_cents > 0),
    occurred_at timestamptz not null,
    note varchar(280) not null default '',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz,
    constraint transaction_record_entry_type_check check (entry_type in ('expense', 'income'))
);

-- 一笔家庭消费可以有多个参与成员；paid_by 仍只表示实际付款人。
create table transaction_participant (
    transaction_id uuid not null references transaction_record(id) on delete cascade,
    user_id uuid not null references app_user(id) on delete cascade,
    primary key (transaction_id, user_id)
);

-- 轻量垫付分摊：一笔账按成员记录应承担金额；实际付款仍由 transaction_record.paid_by 表示。
create table transaction_share (
    transaction_id uuid not null references transaction_record(id) on delete cascade,
    user_id uuid not null references app_user(id) on delete cascade,
    owed_cents bigint not null check (owed_cents >= 0),
    primary key (transaction_id, user_id)
);

create index transaction_record_ledger_occurred_idx
    on transaction_record (ledger_id, occurred_at desc)
    where deleted_at is null;
create index transaction_record_category_occurred_idx
    on transaction_record (category_id, occurred_at desc)
    where deleted_at is null;
create index transaction_participant_user_idx
    on transaction_participant (user_id, transaction_id);
create index transaction_share_user_idx
    on transaction_share (user_id, transaction_id);
