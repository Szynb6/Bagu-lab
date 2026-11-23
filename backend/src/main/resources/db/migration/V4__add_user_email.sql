-- ============================================================================
-- V4: 切换为邮箱+密码登录体系，username 仅作右上角头像与名称展示
-- ============================================================================

ALTER TABLE sys_user
    ADD COLUMN IF NOT EXISTS email VARCHAR(120);

-- 解除 username 的唯一约束，使 email 成为唯一登录凭证，username 仅用于头像与昵称展示
ALTER TABLE sys_user
    DROP CONSTRAINT IF EXISTS sys_user_username_key;

COMMENT ON COLUMN sys_user.email IS '用户登录邮箱（唯一登录与找回密码凭证）';
COMMENT ON COLUMN sys_user.username IS '用户名（用于右上角头像与界面展示）';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_email_lower
    ON sys_user (LOWER(email))
    WHERE email IS NOT NULL;
