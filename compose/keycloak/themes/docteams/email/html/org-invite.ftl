<#import "template.ftl" as layout>
<@layout.emailLayout>
<#--
  Route the KC invite link through the frontend `/accept-invite` bounce page so
  any resident KC SSO cookie on the user's browser is cleared via KC's own
  end_session_endpoint before the invite action token is presented. Prevents
  the "already authenticated as different user" session collision (which would
  otherwise consume the single-use token on a failed attempt).

  See `qa_cycle/fix-specs/GAP-L-01.md`.

  bounceBase is env-driven via theme.properties (`bounceBase` key, backed by
  KC_INVITE_BOUNCE_BASE_URL). Local dev falls back to localhost:3000; the VPS/
  prod deploy sets KC_INVITE_BOUNCE_BASE_URL=https://app-dev.heykazi.com/accept-invite.
  The FTL `!` default is a second safety net if the property is unavailable.
  Resolves the GAP-L-01 prod-rollout TODO (flagged by CodeRabbit on PR #1065).
-->
<#assign bounceBase = (properties.bounceBase)!"http://localhost:3000/accept-invite">
<#assign bounceUrl = bounceBase + "?kcUrl=" + link?url('UTF-8')>
<h1>You've been invited to join ${organization.name}</h1>
<#if firstName?? && lastName??>
<p>Hi ${firstName},</p>
</#if>
<p>You've been invited to join the <strong>${organization.name}</strong> organization on ${realmName}. Click the button below to accept the invitation and get started.</p>
<div class="btn-wrapper" style="text-align:center;padding:8px 0 16px 0;">
  <a href="${bounceUrl}" class="btn-primary" style="display:inline-block;background-color:#0d9488;color:#ffffff;font-size:14px;font-weight:500;text-decoration:none;padding:10px 24px;border-radius:9999px;">Accept Invitation</a>
</div>
<p class="muted" style="font-size:13px;color:#64748b;">This link will expire in ${linkExpirationFormatter(linkExpiration)}.</p>
<p class="muted" style="font-size:13px;color:#64748b;">If you don't want to join this organization, you can safely ignore this email.</p>
</@layout.emailLayout>
