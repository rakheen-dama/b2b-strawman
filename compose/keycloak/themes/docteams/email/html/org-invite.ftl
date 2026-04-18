<#import "template.ftl" as layout>
<@layout.emailLayout>
<#--
  Route the KC invite link through the frontend `/accept-invite` bounce page so
  any resident KC SSO cookie on the user's browser is cleared via KC's own
  end_session_endpoint before the invite action token is presented. Prevents
  the "already authenticated as different user" session collision (which would
  otherwise consume the single-use token on a failed attempt).

  See `qa_cycle/fix-specs/GAP-L-01.md`.

  TODO(GAP-L-01 prod rollout): bounceBase is hard-coded to localhost:3000 for
  the local dev QA cycle — see qa_cycle/fix-specs/GAP-L-01.md "Production
  concern" note. Before staging/prod ship, route this through either:
    (a) a realm-scoped SMTP attribute read via `${realm.attributes['...']}`,
    (b) the `frontendBaseUrl` used by KeycloakProvisioningClient.java, or
    (c) a custom KC SPI that injects per-environment config.
  CodeRabbit review on PR #1065 flagged this — deferred as an explicit non-goal
  of the legal-za QA cycle (intent was to unblock QA, not ship prod infra).
-->
<#assign bounceBase = "http://localhost:3000/accept-invite">
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
