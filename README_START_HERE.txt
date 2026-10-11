RAILYATRA v2 — JAVA + RESPONSIVE WEB FRONTEND
=================================================

WHAT YOU GET
- New blue-and-white mobile / desktop website inspired by the clean references you shared.
- Java 17 backend, API endpoints and persistent local records (no external database setup).
- Passenger login, registration, train and flight search, date-wise seat selection, demo payments,
  ticket information, cancellation with timestamps, and simulated refund calculations.
- Station timetable, search history, language selection for core labels, light/dark theme,
  feedback, admin management and sample routes.

EASIEST WAY FOR YOU (WINDOWS + VS CODE)
1. Extract the ZIP completely (do not open files while still inside the ZIP).
2. Open extracted RailYatra_Web_Complete folder in VS Code.
3. Double-click Run_RailYatra.bat, OR open Terminal in that folder and paste:
   & "$env:USERPROFILE\Tools\apache-maven-3.9.16\bin\mvn.cmd" compile exec:java
4. Open http://localhost:8080 in Google Chrome or Microsoft Edge.
   The exact URL is printed in the terminal; if 8080 was busy, the app chooses 8081-8086.
5. Keep the terminal open. Press Ctrl+C to stop the server.

SAMPLE DEMO LOGINS
Passenger: demo@railyatra.test      Password: Demo@1234
Admin:     admin@railyatra.test     Password: Admin@123
You can register your own new passenger account as well.

QUICK TEST
1. Login as demo passenger, or register.
2. From Bengaluru -> Chennai, choose tomorrow's date and click Find journeys.
3. Choose Lalbagh Express -> enter passenger name / age / gender.
4. Choose one green seat -> Continue to payment.
5. Choose UPI QR or Google Pay / UPI ID / PhonePe / Paytm / Net Banking / Demo Card.
6. Click Next, tick the demo acknowledgement and confirm the DEMO payment.
7. View/print/download your demo ticket with PNR and journey/passenger details.
8. Click Cancel Ticket -> see warning / demo refund -> confirm cancellation.
9. Read the detailed cancellation timestamp and note that the seat is released.
10. Test Flights, Station departure board, Dark / Light mode, language menu, and Admin.

IMPORTANT HONEST LIMITATIONS
- SAMPLE ROUTES ONLY: no real-time rail/airline availability, live GPS, accurate timetable, or real status.
- NO REAL MONEY: all payment buttons are simulations, the UPI pattern is deliberately NOT scannable,
  demo@rail.invalid is not a real UPI destination, and no real Google Pay / banking API is used.
- NO ACTUAL TICKETS: generated PNRs and ticket files cannot be used for travel.
- NO REAL REFUND: demo cancellation amount and date/time are recorded in the local project only.
- DATA IS LOCAL: data/railyatra-store.bin persists local demo accounts/bookings on your laptop.
  It is NOT the previous desktop project's H2 database. Keep or back up this folder to retain new records.
- For a real product you need official railway/flight APIs, payment gateway, service-side validation,
  HTTPS deployment, secure auth / account recovery, compliance and approved merchant onboarding.

TROUBLESHOOTING
- 'mvn is not recognized': use the full Maven command shown above, as Maven was installed
  under your user Tools folder in previous steps.
- 'javac is not recognized': install Temurin JDK 17 and reopen VS Code.
- Browser did not open automatically: manually open URL printed in terminal.
- Cannot connect: keep terminal running and check the last ERROR lines.
- Port occupied: application tries localhost:8080 to localhost:8086 automatically.
- To reset demo data, close app, back up / delete data/ folder and restart.

FOLDER GUIDE
pom.xml                                      Maven project configuration
src/main/java/railway/Main.java               Java HTTP server, authentication, persistence and APIs
src/main/resources/public/index.html          Frontend HTML shell
src/main/resources/public/style.css           Responsive styling
src/main/resources/public/app.js              Frontend logic, forms, payment flow, UI
Run_RailYatra.bat                             Windows startup script
README_START_HERE.txt                         This guide
