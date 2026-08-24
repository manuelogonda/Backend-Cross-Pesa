-- =============================================================================
-- V5__notification_delivery_state.sql
-- Delivery state must be independent of the user's read status: the poller
-- previously selected UNREAD notifications for dispatch, so marking a
-- notification READ cancelled its pending SMS/email and IN_APP rows were
-- re-polled forever.
-- =============================================================================

ALTER TABLE notifications
    ADD COLUMN IF NOT EXISTS dispatched_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_notifications_pending_dispatch
    ON notifications (dispatched_at)
    WHERE dispatched_at IS NULL;
