<#macro emailLayout>
<!DOCTYPE html>
<html lang="${locale.language}" dir="${(ltr)?then('ltr','rtl')}">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="X-UA-Compatible" content="IE=edge">
  <title>${realmName}</title>
  <!--[if mso]>
  <noscript>
    <xml>
      <o:OfficeDocumentSettings>
        <o:PixelsPerInch>96</o:PixelsPerInch>
      </o:OfficeDocumentSettings>
    </xml>
  </noscript>
  <![endif]-->
  <style>
    @import url('https://fonts.googleapis.com/css2?family=IBM+Plex+Sans:wght@400;500;600&family=Sora:wght@600&display=swap');

    body {
      margin: 0;
      padding: 0;
      background-color: #f8fafc;
      font-family: 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      -webkit-font-smoothing: antialiased;
      -moz-osx-font-smoothing: grayscale;
    }
    .email-wrapper {
      width: 100%;
      background-color: #f8fafc;
      padding: 40px 0;
    }
    .email-content {
      max-width: 560px;
      margin: 0 auto;
    }
    .email-logo {
      text-align: center;
      padding-bottom: 32px;
    }
    .email-logo a {
      font-family: 'Sora', -apple-system, BlinkMacSystemFont, sans-serif;
      font-size: 22px;
      font-weight: 600;
      color: #020617;
      text-decoration: none;
      letter-spacing: -0.025em;
    }
    .email-card {
      background-color: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 32px;
    }
    .email-card h1 {
      font-family: 'Sora', -apple-system, BlinkMacSystemFont, sans-serif;
      font-size: 20px;
      font-weight: 600;
      color: #0f172a;
      margin: 0 0 16px 0;
    }
    .email-card p {
      font-size: 14px;
      line-height: 1.6;
      color: #334155;
      margin: 0 0 16px 0;
    }
    .email-card .muted {
      color: #64748b;
      font-size: 13px;
    }
    .btn-primary {
      display: inline-block;
      background-color: #0d9488;
      color: #ffffff !important;
      font-size: 14px;
      font-weight: 500;
      text-decoration: none;
      padding: 10px 24px;
      border-radius: 9999px;
      text-align: center;
    }
    .btn-wrapper {
      text-align: center;
      padding: 8px 0 16px 0;
    }
    .email-footer {
      text-align: center;
      padding-top: 32px;
    }
    .email-footer p {
      font-size: 12px;
      color: #94a3b8;
      margin: 0;
    }
    /* Responsive */
    @media only screen and (max-width: 600px) {
      .email-content { width: 100% !important; padding: 0 16px !important; }
      .email-card { padding: 24px !important; }
    }
  </style>
</head>
<body>
  <div class="email-wrapper">
    <div class="email-content">
      <!-- Logo -->
      <div class="email-logo">
        <a href="#">DocTeams</a>
      </div>

      <!-- Card -->
      <div class="email-card">
        <#nested>
      </div>

      <!-- Footer -->
      <div class="email-footer">
        <p>&copy; ${.now?string('yyyy')} DocTeams. All rights reserved.</p>
      </div>
    </div>
  </div>
</body>
</html>
</#macro>
