# RailYatra | Share with everyone

## What this ZIP contains
- Java 17 backend, HTML/CSS/JavaScript responsive frontend, sample train & flight journeys.
- `Dockerfile` + `render.yaml` for a Render web service.
- `src/main/resources/public/project-document.pdf` is **served by the website** at `/project-document.pdf`.

## To obtain a real public URL
1. Create a **public GitHub repository** named `railyatra-student-demo` at https://github.com/new.
2. Upload the **contents** of this extracted directory to that repository (not the ZIP as one file). `pom.xml`, `Dockerfile`, `render.yaml`, `src/` must be in the repository root.
3. Open https://dashboard.render.com/ and sign in.
4. Click **New** -> **Web Service**; connect the GitHub repository.
5. Choose **Docker** runtime, and the **Free** plan if offered. Deploy.
6. Wait for Render to show *Live*. Copy the HTTPS URL shown by Render; that's the real public link.
7. Share that URL with students; share `<your-render-URL>/project-document.pdf` for the project PDF.

The `render.yaml` can also be used via Render Blueprint deployment.

## Public hosting limitations and safety
- This is an educational simulation. No live railway, airline, payment, UPI, refund, or booking services.
- In public mode, the widely known demo logins are disabled. Students register their own **dummy demo accounts**; do not enter real personal or banking details.
- Optional admin access can be set with Render environment variables `RAILYATRA_ADMIN_EMAIL` and `RAILYATRA_ADMIN_PASSWORD` (password must be at least 12 characters). These values are **not** in the source code. Do not share them.
- Render's Free plan may sleep when idle, delaying first load. Free web services have **ephemeral disks**: registered accounts and bookings can disappear on sleep or redeploy. Free is appropriate for a classroom preview, NOT durable bookings.
- For persistent accounts and tickets you must move storage to a managed database (PostgreSQL is recommended). That migration is not included.
- This demo is not hardened for real public authentication: no email verification, CAPTCHA, rate limiting, CSRF protection, or persistent sessions. No real payments.

## Local run
Use Java 17 and `mvn compile exec:java`, then open the localhost URL printed in the terminal. Local mode retains test admin/passenger accounts and stores local data in `data/`.

## Local port
When `PORT` is supplied, Java listens on `0.0.0.0:<PORT>` for hosting. Without it, it listens on localhost on ports 8080-8086.
