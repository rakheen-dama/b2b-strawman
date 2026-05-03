# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: portal/portal-navigation.spec.ts >> PORTAL-03: Portal Navigation >> No firm-side leakage in portal
- Location: e2e/tests/portal/portal-navigation.spec.ts:320:7

# Error details

```
Test timeout of 30000ms exceeded.
```

# Page snapshot

```yaml
- generic [active] [ref=e1]:
  - link "Skip to content" [ref=e2] [cursor=pointer]:
    - /url: "#main-content"
  - generic [ref=e4]:
    - generic [ref=e5]:
      - heading "Kazi Portal" [level=1] [ref=e6]
      - paragraph [ref=e7]: Access your shared documents and projects
    - generic [ref=e10]:
      - generic [ref=e11]:
        - text: Email address
        - textbox "Email address" [ref=e12]:
          - /placeholder: you@example.com
      - generic [ref=e13]:
        - text: Organization
        - textbox "Organization" [ref=e14]:
          - /placeholder: your-organization
      - button "Send Magic Link" [ref=e15]:
        - img
        - text: Send Magic Link
      - button "Already have a token? Enter it here" [ref=e16]
  - region "Notifications alt+T"
  - alert [ref=e17]
```