--
-- PostgreSQL database dump
--

\restrict xcZfuuhOyQwQLxQUmCyPP01qg7tuxUjzCgbzUsO6Lg2pQndNT5lXviWX8sdUexw

-- Dumped from database version 18.4 (Ubuntu 18.4-0ubuntu0.26.04.1)
-- Dumped by pg_dump version 18.4 (Ubuntu 18.4-0ubuntu0.26.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: block_immutable_ledger_changes(); Type: FUNCTION; Schema: public; Owner: cross_pesa_dev
--

CREATE FUNCTION public.block_immutable_ledger_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN RAISE EXCEPTION 'Financial Ledger entries are immutable. You cannot modify or delete past logs.'; END; $$;


ALTER FUNCTION public.block_immutable_ledger_changes() OWNER TO cross_pesa_dev;

--
-- Name: update_modified_column(); Type: FUNCTION; Schema: public; Owner: cross_pesa_dev
--

CREATE FUNCTION public.update_modified_column() RETURNS trigger
    LANGUAGE plpgsql
    AS $$ BEGIN NEW.updated_at = CURRENT_TIMESTAMP; RETURN NEW; END $$;


ALTER FUNCTION public.update_modified_column() OWNER TO cross_pesa_dev;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: beneficiaries; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.beneficiaries (
    account_currency character varying(3) NOT NULL,
    country_code character varying(2) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    phone_number character varying(20) NOT NULL,
    account_number character varying(50) NOT NULL,
    beneficiary_type character varying(50) NOT NULL,
    city character varying(50),
    first_name character varying(50) NOT NULL,
    last_name character varying(50) NOT NULL,
    payout_method character varying(50) NOT NULL,
    payout_provider character varying(50) NOT NULL,
    email character varying(100) NOT NULL,
    CONSTRAINT beneficiaries_account_currency_check CHECK (((account_currency)::text = ANY ((ARRAY['KES'::character varying, 'USD'::character varying, 'CNY'::character varying, 'JPY'::character varying, 'GBP'::character varying, 'CAD'::character varying, 'AUD'::character varying, 'PKR'::character varying, 'AED'::character varying, 'SAR'::character varying, 'EUR'::character varying, 'SEK'::character varying])::text[]))),
    CONSTRAINT beneficiaries_beneficiary_type_check CHECK (((beneficiary_type)::text = ANY ((ARRAY['INDIVIDUAL'::character varying, 'ORGANIZATION'::character varying, 'BUSINESS'::character varying])::text[]))),
    CONSTRAINT beneficiaries_payout_method_check CHECK (((payout_method)::text = ANY ((ARRAY['BANK_TRANSFER'::character varying, 'MOBILE_MONEY'::character varying, 'CARD_PAYMENT'::character varying])::text[]))),
    CONSTRAINT beneficiaries_payout_provider_check CHECK (((payout_provider)::text = ANY ((ARRAY['M-PESA'::character varying, 'MASTERCARD'::character varying, 'VISA'::character varying, 'EQUITY BANK'::character varying])::text[])))
);


ALTER TABLE public.beneficiaries OWNER TO cross_pesa_dev;

--
-- Name: fx_rates; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.fx_rates (
    destination_currency character varying(3) NOT NULL,
    rate numeric(18,6) NOT NULL,
    source_currency character varying(3) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL
);


ALTER TABLE public.fx_rates OWNER TO cross_pesa_dev;

--
-- Name: kyc_submissions; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.kyc_submissions (
    created_at timestamp(6) without time zone NOT NULL,
    reviewed_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone NOT NULL,
    document_country character varying(10) NOT NULL,
    id uuid NOT NULL,
    reviewed_by uuid,
    user_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    document_type character varying(50) NOT NULL,
    smile_job_id character varying(100) NOT NULL,
    id_image_url character varying(500),
    selfie_image_url character varying(500),
    rejection_reason text
);


ALTER TABLE public.kyc_submissions OWNER TO cross_pesa_dev;

--
-- Name: ledger_entries; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.ledger_entries (
    balance_after numeric(18,4) DEFAULT 0.0000 NOT NULL,
    credit numeric(18,4) NOT NULL,
    currency character varying(3) NOT NULL,
    debit numeric(18,4) NOT NULL,
    usd_baseline_amount numeric(18,4),
    created_at timestamp(6) with time zone NOT NULL,
    routing_pair character varying(10),
    id uuid NOT NULL,
    transaction_id uuid NOT NULL,
    wallet_id uuid NOT NULL,
    entry_class character varying(50) NOT NULL,
    markup_tiers_applied character varying(100),
    description character varying(255) NOT NULL,
    CONSTRAINT ledger_entries_currency_check CHECK (((currency)::text = ANY ((ARRAY['KES'::character varying, 'USD'::character varying, 'CNY'::character varying, 'JPY'::character varying, 'GBP'::character varying, 'CAD'::character varying, 'AUD'::character varying, 'PKR'::character varying, 'AED'::character varying, 'SAR'::character varying, 'EUR'::character varying, 'SEK'::character varying])::text[]))),
    CONSTRAINT ledger_entries_entry_class_check CHECK (((entry_class)::text = ANY ((ARRAY['PRINCIPAL_TRANSFER'::character varying, 'MARKUP_FEE'::character varying, 'ROUTING_FEE'::character varying, 'FX_CLEARING'::character varying, 'DEPOSIT'::character varying, 'WITHDRAWAL'::character varying, 'REFUND'::character varying, 'TREASURY_ADJUSTMENT'::character varying])::text[])))
);


ALTER TABLE public.ledger_entries OWNER TO cross_pesa_dev;

--
-- Name: notifications; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.notifications (
    retry_count integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    idempotency_key uuid,
    transaction_id uuid,
    user_id uuid NOT NULL,
    notification_type character varying(20),
    status character varying(20) NOT NULL,
    title character varying(150) NOT NULL,
    error_message text,
    message text NOT NULL,
    metadata jsonb,
    CONSTRAINT notifications_notification_type_check CHECK (((notification_type)::text = ANY ((ARRAY['EMAIL'::character varying, 'SMS'::character varying, 'IN_APP'::character varying])::text[]))),
    CONSTRAINT notifications_status_check CHECK (((status)::text = ANY ((ARRAY['UNREAD'::character varying, 'READ'::character varying, 'ARCHIVED'::character varying])::text[])))
);


ALTER TABLE public.notifications OWNER TO cross_pesa_dev;

--
-- Name: transactions; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.transactions (
    destination_amount numeric(18,4) NOT NULL,
    destination_currency character varying(3) NOT NULL,
    fx_rate_applied numeric(18,6) NOT NULL,
    gross_amount numeric(18,4) NOT NULL,
    markup_fee numeric(18,4) NOT NULL,
    net_amount numeric(18,4) NOT NULL,
    routing_fee numeric(18,4) NOT NULL,
    source_currency character varying(3) NOT NULL,
    total_fee numeric(18,4) NOT NULL,
    usd_normalization_rate numeric(18,6) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    beneficiary_id uuid,
    destination_wallet_id uuid,
    id uuid NOT NULL,
    idempotency_key uuid NOT NULL,
    sender_id uuid NOT NULL,
    source_wallet_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    funding_gateway character varying(50),
    payout_gateway character varying(50),
    gateway_reference character varying(150),
    payout_reference character varying(150),
    CONSTRAINT transactions_destination_currency_check CHECK (((destination_currency)::text = ANY ((ARRAY['KES'::character varying, 'USD'::character varying, 'CNY'::character varying, 'JPY'::character varying, 'GBP'::character varying, 'CAD'::character varying, 'AUD'::character varying, 'PKR'::character varying, 'AED'::character varying, 'SAR'::character varying, 'EUR'::character varying, 'SEK'::character varying])::text[]))),
    CONSTRAINT transactions_source_currency_check CHECK (((source_currency)::text = ANY ((ARRAY['KES'::character varying, 'USD'::character varying, 'CNY'::character varying, 'JPY'::character varying, 'GBP'::character varying, 'CAD'::character varying, 'AUD'::character varying, 'PKR'::character varying, 'AED'::character varying, 'SAR'::character varying, 'EUR'::character varying, 'SEK'::character varying])::text[]))),
    CONSTRAINT transactions_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying, 'CANCELLED'::character varying, 'FLAGGED'::character varying])::text[])))
);


ALTER TABLE public.transactions OWNER TO cross_pesa_dev;

--
-- Name: users; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.users (
    date_of_birth date,
    kyc_level integer,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    phone_number character varying(20),
    id_type character varying(30),
    first_name character varying(50) NOT NULL,
    id_number character varying(50),
    last_name character varying(50) NOT NULL,
    email character varying(100) NOT NULL,
    auth_provider character varying(255) NOT NULL,
    kyc_status character varying(255),
    password_hash character varying(255),
    role character varying(255) NOT NULL,
    status character varying(255),
    CONSTRAINT users_auth_provider_check CHECK (((auth_provider)::text = ANY ((ARRAY['LOCAL'::character varying, 'GOOGLE'::character varying])::text[]))),
    CONSTRAINT users_kyc_status_check CHECK (((kyc_status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['USER'::character varying, 'MERCHANT'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying, 'LOCKED'::character varying])::text[])))
);


ALTER TABLE public.users OWNER TO cross_pesa_dev;

--
-- Name: wallets; Type: TABLE; Schema: public; Owner: cross_pesa_dev
--

CREATE TABLE public.wallets (
    balance numeric(18,4) NOT NULL,
    currency character varying(3) NOT NULL,
    locked_balance numeric(18,4) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    id uuid NOT NULL,
    user_id uuid,
    status character varying(20) NOT NULL,
    wallet_type character varying(30) NOT NULL,
    CONSTRAINT wallets_currency_check CHECK (((currency)::text = ANY ((ARRAY['KES'::character varying, 'USD'::character varying, 'CNY'::character varying, 'JPY'::character varying, 'GBP'::character varying, 'CAD'::character varying, 'AUD'::character varying, 'PKR'::character varying, 'AED'::character varying, 'SAR'::character varying, 'EUR'::character varying, 'SEK'::character varying])::text[]))),
    CONSTRAINT wallets_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'FROZEN'::character varying, 'SUSPENDED'::character varying])::text[]))),
    CONSTRAINT wallets_wallet_type_check CHECK (((wallet_type)::text = ANY ((ARRAY['USER_RETAIL'::character varying, 'SYSTEM_MARKUP'::character varying, 'SYSTEM_ROUTING'::character varying, 'SYSTEM_LIQUIDITY'::character varying])::text[])))
);


ALTER TABLE public.wallets OWNER TO cross_pesa_dev;

--
-- Data for Name: beneficiaries; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.beneficiaries (account_currency, country_code, created_at, updated_at, id, user_id, phone_number, account_number, beneficiary_type, city, first_name, last_name, payout_method, payout_provider, email) FROM stdin;
GBP	UK	2026-07-27 23:29:08.299306+03	2026-07-27 23:29:08.299346+03	b01b4f56-2874-4bc0-befb-66f2fcb9bb30	85a78d09-a0fe-4cad-8c94-fc44942fb616	+254709875678	+254709875678	BUSINESS	London	Marcus	Anderson	MOBILE_MONEY	M-PESA	marcusan@gmail.com
KES	KE	2026-07-28 02:34:15.93453+03	2026-07-28 02:34:15.934593+03	0ea69470-fd77-4192-af28-d602e976ffb0	556ca27c-23b4-479d-8d7e-13cae1d05f31	+25407756789	+25407756789	INDIVIDUAL	Kisumu	Demis	Umina	MOBILE_MONEY	M-PESA	dmiumina@gmail.com
EUR	KE	2026-07-28 14:19:45.363833+03	2026-07-28 14:19:45.363875+03	b85a95e0-56ea-400e-8a1b-2ddb2c70ed4e	556ca27c-23b4-479d-8d7e-13cae1d05f31	+25401476456	145046457867	INDIVIDUAL	Paris	Reynold	Kalasinga	CARD_PAYMENT	VISA	reykalasinga@gmail.com
GBP	UK	2026-07-30 14:07:50.711678+03	2026-07-30 14:07:50.7117+03	f9ad5aa2-a382-4987-a66b-648885f54210	556ca27c-23b4-479d-8d7e-13cae1d05f31	+254709866567	0123461224545	ORGANIZATION	London	Larry	Christie	CARD_PAYMENT	VISA	lchristie@gmail.com
KES	KE	2026-07-31 15:49:10.166902+03	2026-07-31 15:49:10.167014+03	64be7c13-348e-4bc0-b19d-f73feb1e2c13	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	+254702376345	27893201234	INDIVIDUAL	Kisumu	Claudi	Oron	CARD_PAYMENT	VISA	claudiaoron@gmail.com
EUR	UK	2026-08-03 15:56:31.985568+03	2026-08-03 15:56:31.985591+03	da4b8624-5183-4add-bb1c-4ea92d7a92f7	37511b6f-f04e-427a-aa68-8fce2435f306	+254709867345	103454657097876	BUSINESS	London	George	Anderson	CARD_PAYMENT	VISA	georgeanderson@gmail.com
EUR	FR	2026-08-04 12:13:20.673714+03	2026-08-04 12:13:20.67378+03	728aa33f-cb85-44c0-b760-54252ee3e067	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	+254702456780	167065423452	ORGANIZATION	Paris	Amin	Tyla	CARD_PAYMENT	MASTERCARD	amintyla@gmail.com
\.


--
-- Data for Name: fx_rates; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.fx_rates (destination_currency, rate, source_currency, created_at, expires_at, updated_at, valid_from, id) FROM stdin;
GBP	0.005773	KES	2026-07-30 14:28:27.230714+03	2026-07-30 14:43:25.647664+03	2026-07-30 14:28:27.23072+03	2026-07-30 14:28:25.647664+03	90daced8-c119-40e4-aa27-56d965206c96
GBP	0.005773	KES	2026-07-30 14:45:00.935203+03	2026-07-30 14:59:59.355697+03	2026-07-30 14:45:00.935216+03	2026-07-30 14:44:59.355697+03	62e139cf-c1c2-4636-8550-768090672d0b
KES	129.370000	USD	2026-07-30 14:45:14.724841+03	2026-07-30 15:00:14.494602+03	2026-07-30 14:45:14.724852+03	2026-07-30 14:45:14.494602+03	644aa3a8-5614-4609-a8d8-77b027f87f37
KES	90.826266	AUD	2026-07-31 15:49:33.811641+03	2026-07-31 16:04:31.964607+03	2026-07-31 15:49:33.811661+03	2026-07-31 15:49:31.964607+03	89c04dcf-0ece-4e06-809f-495191356f51
KES	90.706112	AUD	2026-07-31 16:24:02.964469+03	2026-07-31 16:39:01.686292+03	2026-07-31 16:24:02.964485+03	2026-07-31 16:24:01.686292+03	0f252141-950a-4943-8c37-644be3ea8489
EUR	0.006729	KES	2026-07-31 16:27:34.56773+03	2026-07-31 16:42:33.731415+03	2026-07-31 16:27:34.567744+03	2026-07-31 16:27:33.731415+03	88098197-f2fc-4bf2-bb0a-818bc2f1dda8
KES	129.350000	USD	2026-07-31 16:27:44.968538+03	2026-07-31 16:42:44.721922+03	2026-07-31 16:27:44.968544+03	2026-07-31 16:27:44.721922+03	1fbe5db3-a059-4b32-a7ff-501b8585793d
GBP	0.005760	KES	2026-07-31 16:30:58.552304+03	2026-07-31 16:45:55.591732+03	2026-07-31 16:30:58.55232+03	2026-07-31 16:30:55.591732+03	e3cc4747-a29a-4cd0-af32-e80e0bf84af7
EUR	0.006729	KES	2026-07-31 16:48:21.519791+03	2026-07-31 17:03:20.451871+03	2026-07-31 16:48:21.519815+03	2026-07-31 16:48:20.451871+03	e1cdb990-19a9-4aa7-b266-c4a6039ae50e
KES	129.350000	USD	2026-07-31 16:48:27.555002+03	2026-07-31 17:03:27.327664+03	2026-07-31 16:48:27.555007+03	2026-07-31 16:48:27.327664+03	e0e74e40-b2ad-4de8-b820-38a2c065a40e
KES	129.220000	USD	2026-07-31 17:40:47.444286+03	2026-07-31 17:55:46.025055+03	2026-07-31 17:40:47.444299+03	2026-07-31 17:40:46.025055+03	27ffb834-8998-490a-b65d-93fcfc454f52
GBP	0.005736	KES	2026-07-31 21:42:27.014194+03	2026-07-31 21:57:24.530053+03	2026-07-31 21:42:27.0142+03	2026-07-31 21:42:24.530053+03	fe26c9da-b8bd-44da-bc8f-2cf9c76b745b
KES	129.290000	USD	2026-07-31 21:42:47.279887+03	2026-07-31 21:57:47.050897+03	2026-07-31 21:42:47.279892+03	2026-07-31 21:42:47.050897+03	af1e425b-9d32-42d1-8805-4404a9fcfd45
GBP	0.005736	KES	2026-07-31 22:59:17.468657+03	2026-07-31 23:14:14.634225+03	2026-07-31 22:59:17.468665+03	2026-07-31 22:59:14.634225+03	3c5130ab-fe37-45f3-8194-12beaaff10c2
KES	174.254063	GBP	2026-07-31 23:29:14.620305+03	2026-07-31 23:44:13.544909+03	2026-07-31 23:29:14.620322+03	2026-07-31 23:29:13.544909+03	1a3e6010-4bc4-4be7-a076-4f39b544b13e
SEK	12.832457	GBP	2026-07-31 23:29:29.08543+03	2026-07-31 23:44:28.852865+03	2026-07-31 23:29:29.085439+03	2026-07-31 23:29:28.852865+03	252c3514-ab84-43ba-a88d-627d4e23ad08
USD	1.347673	GBP	2026-07-31 23:29:32.78874+03	2026-07-31 23:44:32.5586+03	2026-07-31 23:29:32.788749+03	2026-07-31 23:29:32.5586+03	07c32098-aba4-4b99-83fd-4e8901ecbf14
AED	4.949934	GBP	2026-07-31 23:29:48.247602+03	2026-07-31 23:44:46.452168+03	2026-07-31 23:29:48.247619+03	2026-07-31 23:29:46.452168+03	ea61fbe1-fa1b-4c26-a5e8-389393eda14f
AED	0.028406	KES	2026-07-31 23:30:23.37254+03	2026-07-31 23:45:22.332996+03	2026-07-31 23:30:23.372557+03	2026-07-31 23:30:22.332996+03	a99b98ad-5d04-4ee6-a226-35922d62ca83
KES	174.304803	GBP	2026-08-01 00:17:48.372447+03	2026-08-01 00:32:46.904888+03	2026-08-01 00:17:48.372461+03	2026-08-01 00:17:46.904888+03	08fded68-a62f-4d29-a1d9-46b94c19031d
KES	129.300000	USD	2026-08-01 00:18:57.705563+03	2026-08-01 00:33:56.301005+03	2026-08-01 00:18:57.705587+03	2026-08-01 00:18:56.301005+03	4981b801-8113-4057-ab95-6daaa5c25951
KES	174.190465	GBP	2026-08-03 11:39:23.432409+03	2026-08-03 11:54:21.1849+03	2026-08-03 11:39:23.432429+03	2026-08-03 11:39:21.1849+03	feaca260-5b8e-4253-93f5-3aea5b69edbf
KES	129.400000	USD	2026-08-03 11:39:58.979986+03	2026-08-03 11:54:58.014329+03	2026-08-03 11:39:58.980006+03	2026-08-03 11:39:58.014329+03	4400d85d-e014-47da-bfb7-d57d31ca6b60
KES	90.809312	AUD	2026-08-03 11:40:14.754518+03	2026-08-03 11:55:14.501194+03	2026-08-03 11:40:14.754543+03	2026-08-03 11:40:14.501194+03	f18d418c-04a2-4c94-90f8-68027f56f5d4
KES	92.150343	CAD	2026-08-03 11:40:27.404565+03	2026-08-03 11:55:27.162168+03	2026-08-03 11:40:27.404587+03	2026-08-03 11:40:27.162168+03	ca63bcf9-8c5f-4edd-b927-cf75c7d9d191
GBP	0.005747	KES	2026-08-03 13:56:26.763176+03	2026-08-03 14:11:25.355624+03	2026-08-03 13:56:26.763186+03	2026-08-03 13:56:25.355624+03	a637c834-ab9e-49c1-9eb1-1308ca35b180
KES	129.380000	USD	2026-08-03 13:57:30.674746+03	2026-08-03 14:12:29.503156+03	2026-08-03 13:57:30.674769+03	2026-08-03 13:57:29.503156+03	34260525-65ab-45ce-b4bf-0b9896d13e61
KES	174.142679	GBP	2026-08-03 14:00:33.774877+03	2026-08-03 14:15:32.324838+03	2026-08-03 14:00:33.774905+03	2026-08-03 14:00:32.324838+03	efe64d7a-63d1-431f-8904-05753f9ef457
EUR	0.006699	KES	2026-08-03 15:56:47.813767+03	2026-08-03 16:11:46.064507+03	2026-08-03 15:56:47.81378+03	2026-08-03 15:56:46.064507+03	80bea021-e53f-4e31-ae47-0e2e56963198
KES	129.450000	USD	2026-08-03 15:56:55.495362+03	2026-08-03 16:11:55.272903+03	2026-08-03 15:56:55.495371+03	2026-08-03 15:56:55.272903+03	e00c7264-66e7-4252-a408-05fee12fef12
EUR	0.006706	KES	2026-08-03 16:26:44.370151+03	2026-08-03 16:41:43.184868+03	2026-08-03 16:26:44.37017+03	2026-08-03 16:26:43.184868+03	dcd7d4a4-f0b7-4d68-b363-b667cad9d73b
KES	129.360000	USD	2026-08-03 16:26:53.963239+03	2026-08-03 16:41:53.406217+03	2026-08-03 16:26:53.963262+03	2026-08-03 16:26:53.406217+03	b65490b8-5e8c-4ad5-8341-38b32dc1802d
KES	173.953825	GBP	2026-08-03 18:54:06.103971+03	2026-08-03 19:09:04.523277+03	2026-08-03 18:54:06.103995+03	2026-08-03 18:54:04.523277+03	c22e17f7-27b1-430c-a850-323dcf5a328c
EUR	0.006712	KES	2026-08-03 19:33:58.122035+03	2026-08-03 19:48:56.500532+03	2026-08-03 19:33:58.122091+03	2026-08-03 19:33:56.500532+03	86be3f38-7a38-46c4-a264-306bcf02340b
KES	129.450000	USD	2026-08-03 19:34:07.455703+03	2026-08-03 19:49:07.199246+03	2026-08-03 19:34:07.455757+03	2026-08-03 19:34:07.199246+03	83a07a17-4589-4f53-a16d-0a418e61bdc0
EUR	0.006712	KES	2026-08-03 19:58:15.256423+03	2026-08-03 20:13:13.267539+03	2026-08-03 19:58:15.256454+03	2026-08-03 19:58:13.267539+03	a70250ad-0cc2-4f45-8981-72ab696dd1ef
KES	129.450000	USD	2026-08-03 19:58:25.694235+03	2026-08-03 20:13:25.234334+03	2026-08-03 19:58:25.694256+03	2026-08-03 19:58:25.234334+03	a9d32fba-1b59-4418-9688-21a2dd102e4e
GBP	0.005754	KES	2026-08-03 20:04:50.451997+03	2026-08-03 20:19:48.784592+03	2026-08-03 20:04:50.452087+03	2026-08-03 20:04:48.784592+03	d9f9c397-ff99-4e17-9538-1d51fb38f731
GBP	0.005757	KES	2026-08-03 22:50:44.132913+03	2026-08-03 23:05:42.650805+03	2026-08-03 22:50:44.132929+03	2026-08-03 22:50:42.650805+03	39dc72dc-dba8-4f99-9ae5-150840abe0c7
KES	129.330000	USD	2026-08-03 22:50:52.377699+03	2026-08-03 23:05:52.135742+03	2026-08-03 22:50:52.377707+03	2026-08-03 22:50:52.135742+03	7f409592-69e4-4e39-aff7-3f3df988b0da
EUR	0.006719	KES	2026-08-04 00:59:23.496066+03	2026-08-04 01:14:22.015995+03	2026-08-04 00:59:23.496085+03	2026-08-04 00:59:22.015995+03	1820c842-6224-44ec-aa60-e795ec0cab31
KES	129.328333	USD	2026-08-04 00:59:30.23459+03	2026-08-04 01:14:29.981181+03	2026-08-04 00:59:30.234615+03	2026-08-04 00:59:29.981181+03	890a3a39-1253-471d-b3eb-eb4b2d2f0bc6
KES	90.808657	AUD	2026-08-04 10:05:14.532451+03	2026-08-04 10:20:12.615368+03	2026-08-04 10:05:14.532495+03	2026-08-04 10:05:12.615368+03	b8d51809-3916-489d-8422-69715868138f
AUD	1.425635	USD	2026-08-04 10:08:43.832948+03	2026-08-04 10:23:42.647493+03	2026-08-04 10:08:43.832967+03	2026-08-04 10:08:42.647493+03	df545eb9-554a-46e2-9532-63bad987c984
EUR	0.610248	AUD	2026-08-04 12:13:50.089567+03	2026-08-04 12:28:47.862315+03	2026-08-04 12:13:50.089598+03	2026-08-04 12:13:47.862315+03	f3b3c8d1-a7c5-4af3-978b-0ee6a3985828
KES	90.874105	AUD	2026-08-04 12:13:59.729749+03	2026-08-04 12:28:57.357661+03	2026-08-04 12:13:59.729766+03	2026-08-04 12:13:57.357661+03	d358f51b-1c92-49a8-a433-d0b790f7e7eb
AUD	1.423948	USD	2026-08-04 12:14:00.04592+03	2026-08-04 12:28:59.758605+03	2026-08-04 12:14:00.045938+03	2026-08-04 12:13:59.758605+03	418ba197-f046-4646-a338-d80ae88d9bec
EUR	0.006715	KES	2026-08-04 12:39:08.930951+03	2026-08-04 12:54:05.510074+03	2026-08-04 12:39:08.93097+03	2026-08-04 12:39:05.510074+03	fe9ae154-209b-4755-bcd0-e1c19c9b65b3
GBP	0.005749	KES	2026-08-04 13:08:58.996089+03	2026-08-04 13:23:57.709788+03	2026-08-04 13:08:58.996103+03	2026-08-04 13:08:57.709788+03	faa6538d-0694-4a49-a4c2-4f084c8a9b77
KES	129.400000	USD	2026-08-04 13:53:52.622863+03	2026-08-04 14:08:51.158393+03	2026-08-04 13:53:52.622904+03	2026-08-04 13:53:51.158393+03	5960fdf7-6608-4bba-a54f-1fd7b7576245
GBP	0.005749	KES	2026-08-04 13:59:00.861966+03	2026-08-04 14:13:59.431272+03	2026-08-04 13:59:00.86199+03	2026-08-04 13:58:59.431272+03	52ca3619-9fe1-4c84-bc84-f0b351196140
KES	129.500000	USD	2026-08-04 14:08:55.456267+03	2026-08-04 14:23:54.383295+03	2026-08-04 14:08:55.456283+03	2026-08-04 14:08:54.383295+03	a71c9c54-587e-4dd4-b875-4e668c93c529
EUR	0.868489	USD	2026-08-04 14:10:08.658575+03	2026-08-04 14:25:07.824346+03	2026-08-04 14:10:08.658618+03	2026-08-04 14:10:07.824346+03	ac1a2106-0f3c-4b9b-abaa-8d85dd6afe53
KES	129.360000	USD	2026-08-04 17:51:15.564458+03	2026-08-04 18:06:14.148417+03	2026-08-04 17:51:15.564485+03	2026-08-04 17:51:14.148417+03	4a43934e-51bb-414e-832a-3a58a06b10ac
GBP	0.005745	KES	2026-08-04 22:28:55.856732+03	2026-08-04 22:43:54.323961+03	2026-08-04 22:28:55.856748+03	2026-08-04 22:28:54.323961+03	b6365b3d-9759-4cd2-91d5-2aefbbc8fcb4
KES	129.400000	USD	2026-08-04 22:29:00.840138+03	2026-08-04 22:44:00.60858+03	2026-08-04 22:29:00.84015+03	2026-08-04 22:29:00.60858+03	f271ceef-56cc-497c-b11e-ad7210a8ff09
EUR	0.006701	KES	2026-08-04 22:30:25.302439+03	2026-08-04 22:45:24.138914+03	2026-08-04 22:30:25.302456+03	2026-08-04 22:30:24.138914+03	4f01f4a1-238e-45ab-a97c-0fc18b6f0b83
KES	174.575841	GBP	2026-08-10 13:47:16.551019+03	2026-08-10 14:02:14.618669+03	2026-08-10 13:47:16.551041+03	2026-08-10 13:47:14.618669+03	66fe89d7-5c11-4ae8-b6a4-a98ad6c1b802
KES	174.669306	GBP	2026-08-10 14:04:43.632968+03	2026-08-10 14:19:41.937493+03	2026-08-10 14:04:43.632989+03	2026-08-10 14:04:41.937493+03	471bc024-88a8-436a-9565-f487fd16cc9e
JPY	214.371700	GBP	2026-08-10 14:05:00.929365+03	2026-08-10 14:20:00.571748+03	2026-08-10 14:05:00.929391+03	2026-08-10 14:05:00.571748+03	b9338d90-acaa-4487-9367-2fce884d67b2
\.


--
-- Data for Name: kyc_submissions; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.kyc_submissions (created_at, reviewed_at, updated_at, document_country, id, reviewed_by, user_id, status, document_type, smile_job_id, id_image_url, selfie_image_url, rejection_reason) FROM stdin;
2026-08-05 10:59:35.487153	\N	2026-08-05 10:59:35.487192	KE	2bebea4e-d1ca-4fa0-bc93-fb18c5b98afd	\N	556ca27c-23b4-479d-8d7e-13cae1d05f31	PENDING	NATIONAL_ID	job_override_0kq7vp4ct	\N	\N	\N
2026-08-05 00:51:13.12284	2026-08-05 11:00:55.574316	2026-08-05 11:00:55.578304	KE	98951bb2-ceee-4686-8225-dad7a5cb9bb6	37511b6f-f04e-427a-aa68-8fce2435f306	556ca27c-23b4-479d-8d7e-13cae1d05f31	APPROVED	NATIONAL_ID	mock_job_id_1785880258845	\N	\N	\N
2026-08-05 11:16:32.178216	\N	2026-08-05 11:16:32.178248	KE	1a945870-7005-4684-92f1-b98efbc7badf	\N	556ca27c-23b4-479d-8d7e-13cae1d05f31	PENDING	NATIONAL_ID	job_override_h7as1lrhz	\N	\N	\N
\.


--
-- Data for Name: ledger_entries; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.ledger_entries (balance_after, credit, currency, debit, usd_baseline_amount, created_at, routing_pair, id, transaction_id, wallet_id, entry_class, markup_tiers_applied, description) FROM stdin;
0.0000	45800.0000	KES	0.0000	\N	2026-08-04 13:48:40.166441+03	\N	3e7b4ce1-184d-4a40-b022-b0e31563bdfd	4a680ead-e1f1-4ca6-bf2a-dcaeede2727e	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	DEPOSIT	\N	External Gateway Top-Up: FLW-10411185
0.0000	0.0000	KES	200.0000	1.5456	2026-08-04 13:53:52.734611+03	KES_KES	029d6f36-5d25-499f-a606-48041d61f26a	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	KES	1.2000	1.5456	2026-08-04 13:53:52.747096+03	KES_KES	fd580719-ae89-4dea-9be5-2f235ad2ab56	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	KES	0.6000	1.5456	2026-08-04 13:53:52.74943+03	KES_KES	6e8afaed-4a86-4d95-b04b-e0adfcc3952f	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	1.2000	KES	0.0000	1.5456	2026-08-04 13:53:52.751141+03	KES_KES	6cc9f3f0-0dc8-4101-8e3b-5990d38eb29c	26911e65-f0a9-4c2c-bc7d-78570f5010ba	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	0.6000	KES	0.0000	1.5456	2026-08-04 13:53:52.752812+03	KES_KES	bd9b82e9-60d0-4005-9f00-8570af12696e	26911e65-f0a9-4c2c-bc7d-78570f5010ba	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	199.4000	KES	0.0000	1.5456	2026-08-04 13:53:52.755068+03	KES_KES	cebfe144-207a-4306-a9c9-9cd70cfd185d	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	KES	198.2000	1.5456	2026-08-04 13:53:52.757115+03	KES_KES	9541aba0-f3c2-4518-938d-eaad18ab1391	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	KES	200.0000	129.4000	2026-08-04 13:55:13.512074+03	USD-KES	eb55594d-4192-4ed0-98ba-f2aec9dc9794	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	KES	1.2000	129.4000	2026-08-04 13:55:13.517969+03	USD-KES	16549a5b-7dca-47c8-a616-17dfbde79d45	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	KES	0.6000	129.4000	2026-08-04 13:55:13.522921+03	USD-KES	64c84815-a5ac-4af1-80f3-98f9e3a87585	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	1.2000	KES	0.0000	129.4000	2026-08-04 13:55:13.526977+03	USD-KES	482c7b8c-4929-4cfc-93e9-2575a1fb4efb	26911e65-f0a9-4c2c-bc7d-78570f5010ba	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	0.6000	KES	0.0000	129.4000	2026-08-04 13:55:13.531143+03	USD-KES	fea59204-de44-4bee-86c6-f69edf2056b2	26911e65-f0a9-4c2c-bc7d-78570f5010ba	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	199.4000	KES	0.0000	129.4000	2026-08-04 13:55:13.535697+03	USD-KES	7a4aec2a-738f-4c40-851d-acedec8e4888	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	KES	198.2000	129.4000	2026-08-04 13:55:13.540073+03	USD-KES	318f5f02-6d0d-412f-8935-09181f946784	26911e65-f0a9-4c2c-bc7d-78570f5010ba	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	0.0000	KES	396.0000	3.0603	2026-08-04 13:59:20.913393+03	KES_GBP	0c2362b0-0bd6-4bc4-be6d-8e5e4b1f12d2	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	KES	2.3760	3.0603	2026-08-04 13:59:20.91766+03	KES_GBP	1494dd86-b891-4411-8923-ab931e7ae45a	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	KES	1.1880	3.0603	2026-08-04 13:59:20.920785+03	KES_GBP	1aad3562-0a3f-4514-aa6b-186a26873bb9	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	2.3760	KES	0.0000	3.0603	2026-08-04 13:59:20.924374+03	KES_GBP	365d7fb7-f643-4dcc-864a-0623cf0a67c4	57164a23-b0f9-4e92-afd7-787d64e5a0b2	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	1.1880	KES	0.0000	3.0603	2026-08-04 13:59:20.930215+03	KES_GBP	98c2182d-e8d3-492f-9414-f655177b1bf3	57164a23-b0f9-4e92-afd7-787d64e5a0b2	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	394.8120	KES	0.0000	3.0603	2026-08-04 13:59:20.934036+03	KES_GBP	a281d45a-4050-4311-be13-6f9a55ec94d2	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	GBP	2.2561	3.0603	2026-08-04 13:59:20.938288+03	KES_GBP	47769441-272d-41ad-b434-10edf26e151d	57164a23-b0f9-4e92-afd7-787d64e5a0b2	11bccf86-dd33-4050-8fff-fe52822dc8cb	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	KES	396.0000	129.4000	2026-08-04 14:00:43.749818+03	USD-KES	a57e0041-067e-4448-978c-97d6a08d7b52	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	KES	2.3760	129.4000	2026-08-04 14:00:43.754799+03	USD-KES	c6fafe56-6ff7-41e8-b489-f5e58e955f3a	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	KES	1.1880	129.4000	2026-08-04 14:00:43.758375+03	USD-KES	cbd00a61-ecc4-4aa2-a718-5c58407adf32	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	2.3760	KES	0.0000	129.4000	2026-08-04 14:00:43.761578+03	USD-KES	561108a7-0905-452b-a38c-4f08149f0215	57164a23-b0f9-4e92-afd7-787d64e5a0b2	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	1.1880	KES	0.0000	129.4000	2026-08-04 14:00:43.764949+03	USD-KES	aa0ca5b0-2686-4452-b2ae-9f1172dfca71	57164a23-b0f9-4e92-afd7-787d64e5a0b2	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	394.8120	KES	0.0000	129.4000	2026-08-04 14:00:43.768291+03	USD-KES	b612bb63-e67a-461a-8612-525b727e77c6	57164a23-b0f9-4e92-afd7-787d64e5a0b2	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	GBP	2.2561	129.4000	2026-08-04 14:00:43.771239+03	USD-KES	83da1449-7dbc-41d1-b164-3309bad61e2e	57164a23-b0f9-4e92-afd7-787d64e5a0b2	11bccf86-dd33-4050-8fff-fe52822dc8cb	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	2000.0000	KES	0.0000	\N	2026-08-04 14:02:04.275991+03	\N	0628080b-1d42-4613-b126-05b3cfc4f2d8	1043fa69-f3f6-4579-bcb1-17342cff55d6	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	DEPOSIT	\N	External Gateway Top-Up: FLW-10411207
0.0000	1400.0000	USD	0.0000	\N	2026-08-04 14:08:38.037957+03	\N	01dc60ab-b732-4f6a-bba1-17e3f051902e	f6498aef-9964-4a6f-9a4f-deeac5d6c27e	2728f8a7-8028-4376-bdbb-047f46ccd7bf	DEPOSIT	\N	External Gateway Top-Up: FLW-10411216
0.0000	0.0000	USD	20.0000	20.0000	2026-08-04 14:09:23.509592+03	USD_KES	bea36c2b-8ef5-4136-a259-ff0f1736b912	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	USD	0.1200	20.0000	2026-08-04 14:09:23.513001+03	USD_KES	2fadff3f-9f36-4f69-8ffc-547fbd1da721	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	USD	0.0500	20.0000	2026-08-04 14:09:23.515653+03	USD_KES	3aed4608-5426-4665-8c6f-6f2e734b5e80	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	0.1200	USD	0.0000	20.0000	2026-08-04 14:09:23.518829+03	USD_KES	c5595ed5-fe1b-4d3c-a42a-651362900cc2	e9bc9652-2a53-41a5-beab-0d2829a2fa50	fbf0bb12-d3dd-454a-bd04-c86ff9cabc52	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	0.0500	USD	0.0000	20.0000	2026-08-04 14:09:23.522113+03	USD_KES	041c8558-d08a-4438-a5e0-ad2ad044b65a	e9bc9652-2a53-41a5-beab-0d2829a2fa50	c5ef6169-5cdf-4ed3-9e70-58381afb1841	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	19.9500	USD	0.0000	20.0000	2026-08-04 14:09:23.525434+03	USD_KES	d6e75838-38e2-437a-809b-ec6852f78dcc	e9bc9652-2a53-41a5-beab-0d2829a2fa50	b29e22e6-0654-4a07-9d96-7e530ee161f4	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	KES	2567.9850	20.0000	2026-08-04 14:09:23.528243+03	USD_KES	a48f712e-fee9-4b12-98f0-88c5256c91b2	e9bc9652-2a53-41a5-beab-0d2829a2fa50	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	USD	79.0000	79.0000	2026-08-04 14:10:20.805731+03	USD_EUR	5480e9b0-9b5a-4055-9f60-1e25f6eb4993	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	USD	0.4740	79.0000	2026-08-04 14:10:20.809964+03	USD_EUR	4a2e7249-5e8f-48b0-9f78-210eaab8ce35	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	USD	0.2370	79.0000	2026-08-04 14:10:20.814085+03	USD_EUR	f24c9a49-e04c-42cd-9636-0c9d2641d5d9	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	0.4740	USD	0.0000	79.0000	2026-08-04 14:10:20.818916+03	USD_EUR	fcf66756-9b34-479c-a208-9202629a9ea0	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	fbf0bb12-d3dd-454a-bd04-c86ff9cabc52	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	0.2370	USD	0.0000	79.0000	2026-08-04 14:10:20.821757+03	USD_EUR	93bf4b3b-99df-4a75-ae50-ed21aaea4668	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	c5ef6169-5cdf-4ed3-9e70-58381afb1841	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	78.7630	USD	0.0000	79.0000	2026-08-04 14:10:20.824459+03	USD_EUR	0018ab0e-6be1-414a-a0e8-0a5c1449e051	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	b29e22e6-0654-4a07-9d96-7e530ee161f4	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	EUR	67.9931	79.0000	2026-08-04 14:10:20.827299+03	USD_EUR	434173f2-7125-4a05-b39a-8ae0972efe4f	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	d53cbf9d-dd8d-4d44-82a3-3cf73f84a117	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	USD	20.0000	1.0000	2026-08-04 14:10:44.014172+03	USD-USD	4294825d-09c3-4960-98b8-e9a387033b04	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	USD	0.1200	1.0000	2026-08-04 14:10:44.018592+03	USD-USD	f878c5c8-81eb-4f68-b75e-1bdb3ee54405	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	USD	0.0500	1.0000	2026-08-04 14:10:44.02188+03	USD-USD	7b45aa43-fa48-4504-b3ec-065bad1ed81e	e9bc9652-2a53-41a5-beab-0d2829a2fa50	2728f8a7-8028-4376-bdbb-047f46ccd7bf	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	0.1200	USD	0.0000	1.0000	2026-08-04 14:10:44.025988+03	USD-USD	1c369a63-186b-41bd-b70d-8a0502af88f2	e9bc9652-2a53-41a5-beab-0d2829a2fa50	fbf0bb12-d3dd-454a-bd04-c86ff9cabc52	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	0.0500	USD	0.0000	1.0000	2026-08-04 14:10:44.029326+03	USD-USD	34f4669d-37d4-4ceb-b702-b03e93a1f4f7	e9bc9652-2a53-41a5-beab-0d2829a2fa50	c5ef6169-5cdf-4ed3-9e70-58381afb1841	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	19.9500	USD	0.0000	1.0000	2026-08-04 14:10:44.032317+03	USD-USD	c3a221dc-4dee-4e00-b627-97fb2ddc4557	e9bc9652-2a53-41a5-beab-0d2829a2fa50	b29e22e6-0654-4a07-9d96-7e530ee161f4	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	KES	2567.9850	1.0000	2026-08-04 14:10:44.035356+03	USD-USD	d22c6054-641d-491f-9b60-736894f05df2	e9bc9652-2a53-41a5-beab-0d2829a2fa50	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	0.0000	USD	79.0000	1.0000	2026-08-04 14:11:44.125818+03	USD-USD	55cd1d36-87b0-4979-97a8-7e77844a3f5e	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	USD	0.4740	1.0000	2026-08-04 14:11:44.130007+03	USD-USD	679ac09e-56f8-4474-abb6-eef3a729ffc1	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	USD	0.2370	1.0000	2026-08-04 14:11:44.132599+03	USD-USD	3c052164-76d4-45a7-bf5a-90563f1dcf00	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	2728f8a7-8028-4376-bdbb-047f46ccd7bf	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	0.4740	USD	0.0000	1.0000	2026-08-04 14:11:44.135729+03	USD-USD	1ac52be4-9b3e-481d-ba0c-5e615dcb0e1c	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	fbf0bb12-d3dd-454a-bd04-c86ff9cabc52	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	0.2370	USD	0.0000	1.0000	2026-08-04 14:11:44.138599+03	USD-USD	44689b24-c9fa-4304-a0c7-e9db37a0758b	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	c5ef6169-5cdf-4ed3-9e70-58381afb1841	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	78.7630	USD	0.0000	1.0000	2026-08-04 14:11:44.141153+03	USD-USD	ee86fb38-d241-4a8c-95a2-fe62dab8ee62	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	b29e22e6-0654-4a07-9d96-7e530ee161f4	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	EUR	67.9931	1.0000	2026-08-04 14:11:44.143667+03	USD-USD	704a172c-2320-4bcb-bfdf-cb936908b8b6	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	d53cbf9d-dd8d-4d44-82a3-3cf73f84a117	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	0.0000	KES	500.0000	3.8652	2026-08-04 17:51:15.746244+03	KES_KES	28de0cab-3213-4b83-80c8-dc430516800c	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	KES	3.0000	3.8652	2026-08-04 17:51:15.767064+03	KES_KES	3fe73de1-42b4-41af-83b0-cf1e79aedd90	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	KES	1.5000	3.8652	2026-08-04 17:51:15.76886+03	KES_KES	c0e728df-9a63-4dab-9108-0a8d6357938e	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	3.0000	KES	0.0000	3.8652	2026-08-04 17:51:15.770292+03	KES_KES	e68a7b30-414f-4095-84f6-61abbe060b6d	f545da9c-b75a-4f94-8738-6d09692e3047	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	1.5000	KES	0.0000	3.8652	2026-08-04 17:51:15.771622+03	KES_KES	1eff9ff5-f249-4a8c-8343-82a90c847ad6	f545da9c-b75a-4f94-8738-6d09692e3047	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	498.5000	KES	0.0000	3.8652	2026-08-04 17:51:15.773982+03	KES_KES	a211915f-7181-4a94-9bae-685b4dcb9759	f545da9c-b75a-4f94-8738-6d09692e3047	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	KES	495.5000	3.8652	2026-08-04 17:51:15.775603+03	KES_KES	90a07a7b-86d3-45ed-ba36-7faf90089298	f545da9c-b75a-4f94-8738-6d09692e3047	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	KES	500.0000	129.3600	2026-08-04 17:52:36.651691+03	USD-KES	e354bfea-5d29-4d0f-9b89-1e972178a844	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	KES	3.0000	129.3600	2026-08-04 17:52:36.657134+03	USD-KES	d631417e-5392-4091-b015-4075359a03ac	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	KES	1.5000	129.3600	2026-08-04 17:52:36.661719+03	USD-KES	55fb4996-85bc-4797-ac88-afb8f456c434	f545da9c-b75a-4f94-8738-6d09692e3047	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	3.0000	KES	0.0000	129.3600	2026-08-04 17:52:36.665255+03	USD-KES	d560b3e3-f0fa-4600-baff-6563c8531e59	f545da9c-b75a-4f94-8738-6d09692e3047	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	1.5000	KES	0.0000	129.3600	2026-08-04 17:52:36.668598+03	USD-KES	2eaef060-b929-4253-a4ab-5e059e0b655f	f545da9c-b75a-4f94-8738-6d09692e3047	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	498.5000	KES	0.0000	129.3600	2026-08-04 17:52:36.673109+03	USD-KES	8650a9de-8512-4333-b15f-057e7bd1a739	f545da9c-b75a-4f94-8738-6d09692e3047	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	KES	495.5000	129.3600	2026-08-04 17:52:36.677052+03	USD-KES	093390fa-3be2-4957-be7f-e54dbdd03e82	f545da9c-b75a-4f94-8738-6d09692e3047	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	0.0000	KES	88.0000	0.6801	2026-08-04 22:29:00.897207+03	KES_GBP	5ded1196-4525-461e-b6a2-1277c39f5a48	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	KES	0.5280	0.6801	2026-08-04 22:29:00.908784+03	KES_GBP	4cebb9b1-be93-4262-b0ca-1f683b77ae79	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	KES	0.2640	0.6801	2026-08-04 22:29:00.910213+03	KES_GBP	722fdc28-cc98-4d03-84f3-da0fa194761f	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	0.5280	KES	0.0000	0.6801	2026-08-04 22:29:00.911535+03	KES_GBP	0dead388-064d-4fc7-a03e-b4ea62271e92	867b755d-c23b-4b1b-9521-e49b749fede4	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	0.2640	KES	0.0000	0.6801	2026-08-04 22:29:00.912903+03	KES_GBP	ed115fd2-a38b-40c5-9118-31730db4103c	867b755d-c23b-4b1b-9521-e49b749fede4	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	87.7360	KES	0.0000	0.6801	2026-08-04 22:29:00.915365+03	KES_GBP	da30fa1f-78aa-4367-ab3f-bd43f83aaa51	867b755d-c23b-4b1b-9521-e49b749fede4	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	GBP	0.5010	0.6801	2026-08-04 22:29:00.916569+03	KES_GBP	cdfaee15-dd30-4283-84cd-f582131436f2	867b755d-c23b-4b1b-9521-e49b749fede4	11bccf86-dd33-4050-8fff-fe52822dc8cb	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	KES	88.0000	129.4000	2026-08-04 22:30:08.433361+03	USD-KES	18823a38-6877-4f79-bb9e-fcf7309d1271	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	KES	0.5280	129.4000	2026-08-04 22:30:08.434994+03	USD-KES	9c6b65b3-07a2-4f5f-8216-261635697774	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	KES	0.2640	129.4000	2026-08-04 22:30:08.435989+03	USD-KES	a5e267f8-664a-43b4-9d60-4703789b12ce	867b755d-c23b-4b1b-9521-e49b749fede4	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	0.5280	KES	0.0000	129.4000	2026-08-04 22:30:08.436974+03	USD-KES	6ba71918-ae62-4198-a339-0733dc7fbb02	867b755d-c23b-4b1b-9521-e49b749fede4	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	0.2640	KES	0.0000	129.4000	2026-08-04 22:30:08.437752+03	USD-KES	7ff59a0c-087d-4fc3-9624-125f2cead8eb	867b755d-c23b-4b1b-9521-e49b749fede4	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	87.7360	KES	0.0000	129.4000	2026-08-04 22:30:08.43845+03	USD-KES	792d7158-72d6-484c-9657-5f0b64627648	867b755d-c23b-4b1b-9521-e49b749fede4	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	GBP	0.5010	129.4000	2026-08-04 22:30:08.439162+03	USD-KES	c011ffac-8ee4-4b20-921b-fb81a74adeb8	867b755d-c23b-4b1b-9521-e49b749fede4	11bccf86-dd33-4050-8fff-fe52822dc8cb	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
0.0000	0.0000	KES	99.0000	0.7651	2026-08-04 22:30:30.730969+03	KES_EUR	295792c7-775e-41f8-a1d4-3b4148a1889c	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	TIER_1	Outbound remittance principal
0.0000	0.0000	KES	0.5940	0.7651	2026-08-04 22:30:30.731951+03	KES_EUR	7b62d8bf-4d55-4914-9b91-46d9ebe0b99a	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	TIER_1	Deducting platform profit
0.0000	0.0000	KES	0.2970	0.7651	2026-08-04 22:30:30.732684+03	KES_EUR	d78add8a-4142-46ad-a6b1-55c7ce9b8c3e	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	TIER_1	Deducting banking corridor cost
0.0000	0.5940	KES	0.0000	0.7651	2026-08-04 22:30:30.733432+03	KES_EUR	5726a02b-babd-4ae1-896e-3e5d2e0c642b	0747c516-b450-424e-b517-b1e3ea6b9c03	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	TIER_1	Crediting platform pure profit
0.0000	0.2970	KES	0.0000	0.7651	2026-08-04 22:30:30.734162+03	KES_EUR	1f3c9e11-08f9-4e8b-b907-ee329809af83	0747c516-b450-424e-b517-b1e3ea6b9c03	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	TIER_1	Crediting money to pay external banks
0.0000	98.7030	KES	0.0000	0.7651	2026-08-04 22:30:30.734972+03	KES_EUR	dc11c897-2c4b-4f77-8da3-e71667c555bf	0747c516-b450-424e-b517-b1e3ea6b9c03	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	TIER_1	Inbound clearing float lock
0.0000	0.0000	EUR	0.6574	0.7651	2026-08-04 22:30:30.736041+03	KES_EUR	f32e8120-38bd-45cb-91e0-1b85662ee985	0747c516-b450-424e-b517-b1e3ea6b9c03	d53cbf9d-dd8d-4d44-82a3-3cf73f84a117	FX_CLEARING	TIER_1	Local float payout to beneficiary
0.0000	0.0000	KES	99.0000	129.4000	2026-08-04 22:31:38.505718+03	USD-KES	db79809c-b640-432f-9766-3a5d2da21c73	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	PRINCIPAL_TRANSFER	DEFAULT_TIER	Outbound remittance principal
0.0000	0.0000	KES	0.5940	129.4000	2026-08-04 22:31:38.507832+03	USD-KES	174f26ef-84a0-434b-a601-479d65f2ec12	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	MARKUP_FEE	DEFAULT_TIER	Deducting platform profit
0.0000	0.0000	KES	0.2970	129.4000	2026-08-04 22:31:38.509326+03	USD-KES	bae5d391-f7bc-41b9-ae45-e8006f76cb33	0747c516-b450-424e-b517-b1e3ea6b9c03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	ROUTING_FEE	DEFAULT_TIER	Deducting banking corridor cost
0.0000	0.5940	KES	0.0000	129.4000	2026-08-04 22:31:38.510763+03	USD-KES	4effe034-d571-480e-b541-b0e60962de9b	0747c516-b450-424e-b517-b1e3ea6b9c03	f93d24e7-0d5c-4b66-a3eb-407835795e31	MARKUP_FEE	DEFAULT_TIER	Crediting platform pure profit
0.0000	0.2970	KES	0.0000	129.4000	2026-08-04 22:31:38.512111+03	USD-KES	983e081d-6def-42a5-afba-b53578db3de1	0747c516-b450-424e-b517-b1e3ea6b9c03	b6702a4b-b4f2-4fdb-ac65-24694399474e	ROUTING_FEE	DEFAULT_TIER	Crediting money to pay external banks
0.0000	98.7030	KES	0.0000	129.4000	2026-08-04 22:31:38.513455+03	USD-KES	31e3513c-2b2e-4c7a-bf2f-d7575e79816a	0747c516-b450-424e-b517-b1e3ea6b9c03	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	FX_CLEARING	DEFAULT_TIER	Inbound clearing float lock
0.0000	0.0000	EUR	0.6574	129.4000	2026-08-04 22:31:38.51477+03	USD-KES	62ae2260-e37e-4bc5-b4f7-c993aeb9015b	0747c516-b450-424e-b517-b1e3ea6b9c03	d53cbf9d-dd8d-4d44-82a3-3cf73f84a117	FX_CLEARING	DEFAULT_TIER	Local float payout to beneficiary
\.


--
-- Data for Name: notifications; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.notifications (retry_count, created_at, updated_at, id, idempotency_key, transaction_id, user_id, notification_type, status, title, error_message, message, metadata) FROM stdin;
\.


--
-- Data for Name: transactions; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.transactions (destination_amount, destination_currency, fx_rate_applied, gross_amount, markup_fee, net_amount, routing_fee, source_currency, total_fee, usd_normalization_rate, created_at, updated_at, beneficiary_id, destination_wallet_id, id, idempotency_key, sender_id, source_wallet_id, status, funding_gateway, payout_gateway, gateway_reference, payout_reference) FROM stdin;
45800.0000	KES	1.000000	45800.0000	0.0000	45800.0000	0.0000	KES	0.0000	1.000000	2026-08-04 13:48:40.147643+03	2026-08-04 13:48:40.147724+03	\N	\N	4a680ead-e1f1-4ca6-bf2a-dcaeede2727e	66d24dd9-48e9-420e-b50e-ad2a7558d40b	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	FLW-10411185	\N
198.2000	KES	1.000000	200.0000	1.2000	198.2000	0.6000	KES	1.8000	129.400000	2026-08-04 13:53:52.731129+03	2026-08-04 13:55:13.543594+03	0ea69470-fd77-4192-af28-d602e976ffb0	\N	26911e65-f0a9-4c2c-bc7d-78570f5010ba	39a61403-5183-4ed1-bac2-75e5362102ab	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	GW-OUT-a97d4a78-673c-4d7f-bbd3-609a041bb8c1	PO-IN-0add29c9-8b41-40e3-9141-0391ecf1942f
2.2561	GBP	0.005749	396.0000	2.3760	392.4360	1.1880	KES	3.5640	129.400000	2026-08-04 13:59:20.905834+03	2026-08-04 14:00:43.791046+03	f9ad5aa2-a382-4987-a66b-648885f54210	\N	57164a23-b0f9-4e92-afd7-787d64e5a0b2	d437776b-4a66-47bc-90d3-096bfce9d7df	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	GW-OUT-3f27300e-bc7b-4306-ab13-ee81439f1b63	PO-IN-5b5dd11c-a8c3-47d5-9269-1444994d3b35
2000.0000	KES	1.000000	2000.0000	0.0000	2000.0000	0.0000	KES	0.0000	1.000000	2026-08-04 14:02:04.271509+03	2026-08-04 14:02:04.271547+03	\N	\N	1043fa69-f3f6-4579-bcb1-17342cff55d6	db3ce9ee-6d27-4165-bbee-77b9e3431e95	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	FLW-10411207	\N
1400.0000	USD	1.000000	1400.0000	0.0000	1400.0000	0.0000	USD	0.0000	1.000000	2026-08-04 14:08:38.034409+03	2026-08-04 14:08:38.034444+03	\N	\N	f6498aef-9964-4a6f-9a4f-deeac5d6c27e	c6eb5e6e-7b68-40d5-a29c-e96ab6949e4f	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	2728f8a7-8028-4376-bdbb-047f46ccd7bf	COMPLETED	\N	\N	FLW-10411216	\N
2567.9850	KES	129.500000	20.0000	0.1200	19.8300	0.0500	USD	0.1700	1.000000	2026-08-04 14:09:23.503479+03	2026-08-04 14:10:44.037952+03	64be7c13-348e-4bc0-b19d-f73feb1e2c13	\N	e9bc9652-2a53-41a5-beab-0d2829a2fa50	43e81734-6d74-4ea2-bd49-afbee0d1864a	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	2728f8a7-8028-4376-bdbb-047f46ccd7bf	COMPLETED	\N	\N	GW-OUT-55d6f584-a349-4608-a402-1053a630d065	PO-IN-0a53f865-47f2-4599-8216-0f34895d453e
67.9931	EUR	0.868489	79.0000	0.4740	78.2890	0.2370	USD	0.7110	1.000000	2026-08-04 14:10:20.801594+03	2026-08-04 14:11:44.146154+03	728aa33f-cb85-44c0-b760-54252ee3e067	\N	7ee19d08-49e9-43a8-8977-47c9f4d03b9c	eb9fc96b-0d6d-4c5d-893e-4aee2355cd1d	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	2728f8a7-8028-4376-bdbb-047f46ccd7bf	COMPLETED	\N	\N	GW-OUT-8b42191f-03eb-43a7-81df-9412eadf48c6	PO-IN-c2eea014-11d2-459d-b4a6-1b79361efa6b
495.5000	KES	1.000000	500.0000	3.0000	495.5000	1.5000	KES	4.5000	129.360000	2026-08-04 17:51:15.736925+03	2026-08-04 17:52:36.679901+03	0ea69470-fd77-4192-af28-d602e976ffb0	\N	f545da9c-b75a-4f94-8738-6d09692e3047	4fa4d31d-57d2-46de-a177-c99ee2fc8479	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	GW-OUT-e0565947-8676-4a80-abc7-bdf2de61d309	PO-IN-200948c1-f7fe-449e-982c-7efadcc2d6f2
0.5010	GBP	0.005745	88.0000	0.5280	87.2080	0.2640	KES	0.7920	129.400000	2026-08-04 22:29:00.891463+03	2026-08-04 22:30:08.439892+03	f9ad5aa2-a382-4987-a66b-648885f54210	\N	867b755d-c23b-4b1b-9521-e49b749fede4	1907963c-76a3-45b2-a48c-2f413c44b6c5	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	GW-OUT-62951da9-d777-40f7-a5e2-f84602a9eb45	PO-IN-285027e4-7026-426b-a5fb-03836801e512
0.6574	EUR	0.006701	99.0000	0.5940	98.1090	0.2970	KES	0.8910	129.400000	2026-08-04 22:30:30.729494+03	2026-08-04 22:31:38.516007+03	b85a95e0-56ea-400e-8a1b-2ddb2c70ed4e	\N	0747c516-b450-424e-b517-b1e3ea6b9c03	a7a3af1c-1bab-48b5-ae4a-55221da232d5	556ca27c-23b4-479d-8d7e-13cae1d05f31	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	COMPLETED	\N	\N	GW-OUT-31a1d796-c76e-4067-9c90-ccdbf4db6f4b	PO-IN-6320f676-8405-4036-96c2-64f92ff659ac
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.users (date_of_birth, kyc_level, created_at, updated_at, id, phone_number, id_type, first_name, id_number, last_name, email, auth_provider, kyc_status, password_hash, role, status) FROM stdin;
\N	1	2026-07-27 14:14:28.451258+03	2026-07-27 14:14:28.451314+03	37511b6f-f04e-427a-aa68-8fce2435f306	+254700000000	\N	System	\N	Admin	admin@crosspesa.com	LOCAL	APPROVED	$2a$10$lEpkzpiBM3eH82LJ3oD7f.rE4P2lOtnT/.eRdIBDKUKk85J6oUk8K	ADMIN	ACTIVE
\N	1	2026-07-27 20:16:50.073127+03	2026-07-27 20:16:50.073224+03	d80788c6-06ea-44d5-9237-e4af1aa57b79	0702322780	\N	Francis 	\N	Sakwa	fransa@gmail.com	LOCAL	PENDING	$2a$10$Vw83khVrltRflWiJP5Gg3.Fl3JJOlJ9NVHT.GAWZi3QoApVZVKg0W	USER	ACTIVE
\N	1	2026-07-27 23:23:58.079746+03	2026-07-27 23:23:58.079788+03	85a78d09-a0fe-4cad-8c94-fc44942fb616	0709976876	\N	Ken	\N	Michuki	kenmichu@gmail.com	LOCAL	PENDING	$2a$10$Tg0zDphG/7eXGF4u9orKR.oSB3S7PsQxisD1HJUmDakaUQAApfdWS	USER	ACTIVE
\N	1	2026-07-31 15:39:39.573524+03	2026-08-04 10:08:01.733149+03	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	0710034789	\N	Bryson	\N	Mulama	brysonmula@gmail.com	LOCAL	APPROVED	$2a$10$y//hjuYoxonBQwxhfj/QwuJ9EUHfL6tqvsdLlQUHk48d1Vfio0tTS	USER	ACTIVE
\N	2	2026-07-27 15:52:52.192837+03	2026-08-05 11:00:55.583766+03	556ca27c-23b4-479d-8d7e-13cae1d05f31	0711392245	\N	Emmanuel	\N	Odhiambo	emanuelodhiambo84@gmail.com	LOCAL	APPROVED	$2a$10$fSlnV6aX8owvlF.5N91K0eSkA3ITahThszAZHIC54Jmfr2yxd9Fdm	USER	ACTIVE
\.


--
-- Data for Name: wallets; Type: TABLE DATA; Schema: public; Owner: cross_pesa_dev
--

COPY public.wallets (balance, currency, locked_balance, created_at, updated_at, id, user_id, status, wallet_type) FROM stdin;
0.0000	CNY	0.0000	2026-08-04 13:53:07.401999+03	2026-08-04 13:53:07.402104+03	f4e41823-ebe8-4288-b9f3-a69c3a7677c4	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	CNY	0.0000	2026-08-04 13:53:07.420085+03	2026-08-04 13:53:07.420209+03	f8186c16-24a5-41db-b367-cb8d92f30b94	\N	ACTIVE	SYSTEM_MARKUP
0.0000	CNY	0.0000	2026-08-04 13:53:07.439815+03	2026-08-04 13:53:07.43992+03	fe3a73aa-9cf4-4390-a862-ab5f5be98bed	\N	ACTIVE	SYSTEM_ROUTING
0.0000	JPY	0.0000	2026-08-04 13:53:07.45206+03	2026-08-04 13:53:07.452099+03	ae64b914-7312-4209-b355-c0799925f8a9	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	JPY	0.0000	2026-08-04 13:53:07.461309+03	2026-08-04 13:53:07.461344+03	1b6e6435-ef38-4945-a11c-e4b6e78b679c	\N	ACTIVE	SYSTEM_MARKUP
0.0000	JPY	0.0000	2026-08-04 13:53:07.470831+03	2026-08-04 13:53:07.470914+03	46baf986-3fd1-49dd-8c11-1e6864dc1731	\N	ACTIVE	SYSTEM_ROUTING
0.0000	GBP	0.0000	2026-08-04 13:53:07.487871+03	2026-08-04 13:53:07.487913+03	d676e82a-c729-40da-a83f-6621d1af3086	\N	ACTIVE	SYSTEM_MARKUP
0.0000	GBP	0.0000	2026-08-04 13:53:07.496651+03	2026-08-04 13:53:07.496694+03	2fe4ba2c-cb00-4084-93f2-6f8d5c7368c2	\N	ACTIVE	SYSTEM_ROUTING
0.0000	CAD	0.0000	2026-08-04 13:53:07.506282+03	2026-08-04 13:53:07.506317+03	88490eb6-c2d2-4b71-82d0-45659789b937	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	CAD	0.0000	2026-08-04 13:53:07.514065+03	2026-08-04 13:53:07.514093+03	f0c5ece4-8c29-47cf-b05c-862d037da6ad	\N	ACTIVE	SYSTEM_MARKUP
0.0000	CAD	0.0000	2026-08-04 13:53:07.522123+03	2026-08-04 13:53:07.522163+03	c3dde36e-515c-43a7-a732-3d177147be2d	\N	ACTIVE	SYSTEM_ROUTING
0.0000	AUD	0.0000	2026-08-04 13:53:07.531854+03	2026-08-04 13:53:07.531894+03	fa6f0710-1c94-4001-9433-8bbcbfa503fd	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	AUD	0.0000	2026-08-04 13:53:07.541472+03	2026-08-04 13:53:07.541513+03	fef0d16d-f142-48dd-8108-2e980e816486	\N	ACTIVE	SYSTEM_MARKUP
0.0000	AUD	0.0000	2026-08-04 13:53:07.55099+03	2026-08-04 13:53:07.551029+03	4b1b224b-202d-458e-80ec-6ca0ddf08553	\N	ACTIVE	SYSTEM_ROUTING
0.0000	PKR	0.0000	2026-08-04 13:53:07.560034+03	2026-08-04 13:53:07.560064+03	ab1d9f97-9f77-4886-a084-575098652de2	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	PKR	0.0000	2026-08-04 13:53:07.570921+03	2026-08-04 13:53:07.570954+03	ca20dad1-d0a6-4be5-be10-46deac6b911e	\N	ACTIVE	SYSTEM_MARKUP
0.0000	PKR	0.0000	2026-08-04 13:53:07.57947+03	2026-08-04 13:53:07.579502+03	7f6eb24f-18df-4f3c-baa1-bdd64089fccc	\N	ACTIVE	SYSTEM_ROUTING
0.0000	AED	0.0000	2026-08-04 13:53:07.5872+03	2026-08-04 13:53:07.587229+03	0c293cda-f27b-49aa-8b7d-17104f227da1	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	AED	0.0000	2026-08-04 13:53:07.595412+03	2026-08-04 13:53:07.595455+03	d4d0f5e2-8ae3-4bb8-9b00-0b3f735d2a9e	\N	ACTIVE	SYSTEM_MARKUP
0.0000	AED	0.0000	2026-08-04 13:53:07.603128+03	2026-08-04 13:53:07.60317+03	104d5f19-052c-4366-b57a-debecba0cfab	\N	ACTIVE	SYSTEM_ROUTING
0.0000	SAR	0.0000	2026-08-04 13:53:07.611726+03	2026-08-04 13:53:07.611765+03	2c6dfc3c-6155-47fb-b7c1-5be90ec3f17f	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	SAR	0.0000	2026-08-04 13:53:07.619635+03	2026-08-04 13:53:07.619667+03	febb14ac-e040-412e-a1c3-a11ad764ca8c	\N	ACTIVE	SYSTEM_MARKUP
0.0000	SAR	0.0000	2026-08-04 13:53:07.62891+03	2026-08-04 13:53:07.628943+03	5c91cba1-ae0a-4b44-ad30-c8152ae8b4d5	\N	ACTIVE	SYSTEM_ROUTING
0.0000	EUR	0.0000	2026-08-04 13:53:07.647157+03	2026-08-04 13:53:07.647246+03	76d7e92a-08a7-4e6b-b50b-89eb7172126d	\N	ACTIVE	SYSTEM_MARKUP
0.0000	EUR	0.0000	2026-08-04 13:53:07.655161+03	2026-08-04 13:53:07.655206+03	dc9210a3-7bdf-4378-9a18-7ade691638cc	\N	ACTIVE	SYSTEM_ROUTING
0.0000	SEK	0.0000	2026-08-04 13:53:07.663114+03	2026-08-04 13:53:07.663145+03	d3e81bd5-f77a-4051-bf1d-08234f3835b3	\N	ACTIVE	SYSTEM_LIQUIDITY
0.0000	SEK	0.0000	2026-08-04 13:53:07.671617+03	2026-08-04 13:53:07.671651+03	de99b5f8-411c-488f-b1c3-c69641815e8e	\N	ACTIVE	SYSTEM_MARKUP
0.0000	SEK	0.0000	2026-08-04 13:53:07.679275+03	2026-08-04 13:53:07.67932+03	9e90256c-1dfc-4c8d-a896-0627a404980e	\N	ACTIVE	SYSTEM_ROUTING
-5.5142	GBP	0.0000	2026-08-04 13:53:07.479174+03	2026-08-04 22:30:08.44424+03	11bccf86-dd33-4050-8fff-fe52822dc8cb	\N	ACTIVE	SYSTEM_LIQUIDITY
1200.2380	USD	0.0000	2026-08-04 14:06:24.177367+03	2026-08-04 14:11:44.149062+03	2728f8a7-8028-4376-bdbb-047f46ccd7bf	4bc3cb16-bbf0-496d-93f3-e34aaf855ddf	ACTIVE	USER_RETAIL
1.1880	USD	0.0000	2026-08-04 13:53:07.364749+03	2026-08-04 14:11:44.15089+03	fbf0bb12-d3dd-454a-bd04-c86ff9cabc52	\N	ACTIVE	SYSTEM_MARKUP
0.5740	USD	0.0000	2026-08-04 13:53:07.38445+03	2026-08-04 14:11:44.152617+03	c5ef6169-5cdf-4ed3-9e70-58381afb1841	\N	ACTIVE	SYSTEM_ROUTING
197.4260	USD	0.0000	2026-08-04 13:53:07.345643+03	2026-08-04 14:11:44.154342+03	b29e22e6-0654-4a07-9d96-7e530ee161f4	\N	ACTIVE	SYSTEM_LIQUIDITY
45210.9060	KES	0.0000	2026-08-04 13:47:42.956655+03	2026-08-04 22:31:38.517767+03	e5ed3f32-930a-4eb8-8b4c-f70d52425a95	556ca27c-23b4-479d-8d7e-13cae1d05f31	ACTIVE	USER_RETAIL
15.3960	KES	0.0000	2026-08-04 13:53:07.308097+03	2026-08-04 22:31:38.518655+03	f93d24e7-0d5c-4b66-a3eb-407835795e31	\N	ACTIVE	SYSTEM_MARKUP
7.6980	KES	0.0000	2026-08-04 13:53:07.326162+03	2026-08-04 22:31:38.519357+03	b6702a4b-b4f2-4fdb-ac65-24694399474e	\N	ACTIVE	SYSTEM_ROUTING
-3965.0680	KES	0.0000	2026-08-04 13:53:07.066152+03	2026-08-04 22:31:38.51999+03	e97eff44-c6cd-4ddd-94ea-f5ee3292a5d0	\N	ACTIVE	SYSTEM_LIQUIDITY
-137.3010	EUR	0.0000	2026-08-04 13:53:07.638522+03	2026-08-04 22:31:38.520627+03	d53cbf9d-dd8d-4d44-82a3-3cf73f84a117	\N	ACTIVE	SYSTEM_LIQUIDITY
\.


--
-- Name: beneficiaries beneficiaries_email_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.beneficiaries
    ADD CONSTRAINT beneficiaries_email_key UNIQUE (email);


--
-- Name: beneficiaries beneficiaries_phone_number_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.beneficiaries
    ADD CONSTRAINT beneficiaries_phone_number_key UNIQUE (phone_number);


--
-- Name: beneficiaries beneficiaries_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.beneficiaries
    ADD CONSTRAINT beneficiaries_pkey PRIMARY KEY (id);


--
-- Name: fx_rates fx_rates_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.fx_rates
    ADD CONSTRAINT fx_rates_pkey PRIMARY KEY (id);


--
-- Name: kyc_submissions kyc_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.kyc_submissions
    ADD CONSTRAINT kyc_submissions_pkey PRIMARY KEY (id);


--
-- Name: kyc_submissions kyc_submissions_smile_job_id_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.kyc_submissions
    ADD CONSTRAINT kyc_submissions_smile_job_id_key UNIQUE (smile_job_id);


--
-- Name: ledger_entries ledger_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (id);


--
-- Name: notifications notifications_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: notifications notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);


--
-- Name: transactions transactions_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: transactions transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT transactions_pkey PRIMARY KEY (id);


--
-- Name: beneficiaries uk_user_beneficiary_routing; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.beneficiaries
    ADD CONSTRAINT uk_user_beneficiary_routing UNIQUE (user_id, payout_provider, account_number);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_id_number_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_id_number_key UNIQUE (id_number);


--
-- Name: users users_phone_number_key; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_phone_number_key UNIQUE (phone_number);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: wallets wallets_pkey; Type: CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT wallets_pkey PRIMARY KEY (id);


--
-- Name: idx_kyc_smile_job_id; Type: INDEX; Schema: public; Owner: cross_pesa_dev
--

CREATE INDEX idx_kyc_smile_job_id ON public.kyc_submissions USING btree (smile_job_id);


--
-- Name: idx_kyc_status; Type: INDEX; Schema: public; Owner: cross_pesa_dev
--

CREATE INDEX idx_kyc_status ON public.kyc_submissions USING btree (status);


--
-- Name: idx_kyc_user_id; Type: INDEX; Schema: public; Owner: cross_pesa_dev
--

CREATE INDEX idx_kyc_user_id ON public.kyc_submissions USING btree (user_id);


--
-- Name: transactions fk2bsc2s2qa105a84ttie6p81iu; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fk2bsc2s2qa105a84ttie6p81iu FOREIGN KEY (source_wallet_id) REFERENCES public.wallets(id);


--
-- Name: transactions fk3ly4r8r6ubt0blftudix2httv; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fk3ly4r8r6ubt0blftudix2httv FOREIGN KEY (sender_id) REFERENCES public.users(id);


--
-- Name: transactions fk5ydl5lva775mkrf7dbit23rqu; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fk5ydl5lva775mkrf7dbit23rqu FOREIGN KEY (destination_wallet_id) REFERENCES public.wallets(id);


--
-- Name: transactions fk75ax7mj8udi8w30twigxirgo9; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.transactions
    ADD CONSTRAINT fk75ax7mj8udi8w30twigxirgo9 FOREIGN KEY (beneficiary_id) REFERENCES public.beneficiaries(id);


--
-- Name: notifications fk9y21adhxn0ayjhfocscqox7bh; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fk9y21adhxn0ayjhfocscqox7bh FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: wallets fkc1foyisidw7wqqrkamafuwn4e; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.wallets
    ADD CONSTRAINT fkc1foyisidw7wqqrkamafuwn4e FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: ledger_entries fkcfwn04lfp5eyco3vbjc3729ib; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT fkcfwn04lfp5eyco3vbjc3729ib FOREIGN KEY (wallet_id) REFERENCES public.wallets(id);


--
-- Name: kyc_submissions fkea2b3m8ur55vcf7ie0wulf3n8; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.kyc_submissions
    ADD CONSTRAINT fkea2b3m8ur55vcf7ie0wulf3n8 FOREIGN KEY (reviewed_by) REFERENCES public.users(id);


--
-- Name: ledger_entries fkgwcsld4m3g325l66qro45i14x; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT fkgwcsld4m3g325l66qro45i14x FOREIGN KEY (transaction_id) REFERENCES public.transactions(id);


--
-- Name: notifications fkh0p74cs3ivqslup5nvekv8tau; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT fkh0p74cs3ivqslup5nvekv8tau FOREIGN KEY (transaction_id) REFERENCES public.transactions(id);


--
-- Name: beneficiaries fkk8iehn8e7itlnc8pev97p1bty; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.beneficiaries
    ADD CONSTRAINT fkk8iehn8e7itlnc8pev97p1bty FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: kyc_submissions fkqxe2gb1mkewuc9ctghfrybfk6; Type: FK CONSTRAINT; Schema: public; Owner: cross_pesa_dev
--

ALTER TABLE ONLY public.kyc_submissions
    ADD CONSTRAINT fkqxe2gb1mkewuc9ctghfrybfk6 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: pg_database_owner
--

GRANT ALL ON SCHEMA public TO cross_pesa_dev;


--
-- PostgreSQL database dump complete
--

\unrestrict xcZfuuhOyQwQLxQUmCyPP01qg7tuxUjzCgbzUsO6Lg2pQndNT5lXviWX8sdUexw

