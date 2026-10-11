package railway;

import com.sun.net.httpserver.*;
import java.awt.Desktop;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** RailYatra educational demonstration — local Java 17 web backend. No real booking or payment. */
public class Main {
    static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a 'IST'",Locale.ENGLISH);
    static final boolean PUBLIC_DEMO = Boolean.parseBoolean(System.getenv().getOrDefault("RAILYATRA_PUBLIC", "false"));
    static final Path DATA = Paths.get(System.getenv().getOrDefault("RAILYATRA_DATA_DIR", "data"), "railyatra-store.bin");
    static Store store;
    static final Map<String,String> sessions = new ConcurrentHashMap<>();
    static final Map<String,Checkout> checkouts = new ConcurrentHashMap<>();
    static int port = 8080;

    static class User implements Serializable {
        private static final long serialVersionUID=1L;
        String id,name,email,phone,hash,salt,role;
        User(String id,String name,String email,String phone,String password,String role) throws Exception {
            this.id=id;this.name=name;this.email=email;this.phone=phone;this.role=role;
            byte[] s=new byte[16]; new SecureRandom().nextBytes(s);salt=Base64.getEncoder().encodeToString(s);
            hash=hash(password,salt);
        }
        Map<String,Object> data(){return map("id",id,"name",name,"email",email,"role",role,"phone",phone);}
    }
    static class Service implements Serializable {
        private static final long serialVersionUID=1L;
        String id,type,number,name,from,to,departure,arrival,duration,days;
        int fare,capacity; boolean active=true;
        Service(String id,String type,String number,String name,String from,String to,String dep,String arr,String duration,int fare,int cap) {
            this.id=id;this.type=type;this.number=number;this.name=name;this.from=from;this.to=to;
            this.departure=dep;this.arrival=arr;this.duration=duration;this.fare=fare;this.capacity=cap;
            this.days="Daily";
        }
        Map<String,Object> data(String date){
            int taken=0; List<Integer> occupied=new ArrayList<>();
            for(Ticket t:store.tickets) if(t.serviceId.equals(id)&&t.date.equals(date)&&"CONFIRMED".equals(t.status)){taken+=t.seats.size();occupied.addAll(t.seats);}
            int delay=Math.abs(number.hashCode())%4==0?12:Math.abs(number.hashCode())%5==0?5:0;
            return map("id",id,"type",type,"number",number,"name",name,"from",from,"to",to,
                "departure",departure,"arrival",arrival,"duration",duration,"fare",fare,"capacity",capacity,
                "remaining",capacity-taken,"occupiedSeats",occupied,"delayMinutes",delay,"waitlist","WL 0", "active",active,"days",days);
        }
    }
    static class Passenger implements Serializable {
        private static final long serialVersionUID=1L;
        String name,gender;int age;
        Passenger(String n,int a,String g){name=n;age=a;gender=g;}
        Map<String,Object> data(){return map("name",name,"age",age,"gender",gender);}
    }
    static class Ticket implements Serializable {
        private static final long serialVersionUID=1L;
        String pnr,userId,serviceId,date,travelClass,method,status,paidAt,cancelledAt,refundStatus;
        int fare,fee,insurance,tax,total,refund;
        List<Passenger> passengers;List<Integer> seats;
        Map<String,Object> data(){
            Service s=findService(serviceId);
            List<Object> ps=new ArrayList<>();for(Passenger p:passengers)ps.add(p.data());
            return map("pnr",pnr,"service",s==null?null:s.data(date),"date",date,"travelClass",travelClass,
                "method",method,"status",status,"paidAt",paidAt,"cancelledAt",cancelledAt,"refundStatus",refundStatus,
                "fare",fare,"fee",fee,"insurance",insurance,"tax",tax,"total",total,"refund",refund,
                "passengers",ps,"seats",seats,"paymentReference","DEMO-"+pnr.substring(2));
        }
    }
    static class Feedback implements Serializable {
        private static final long serialVersionUID=1L;
        String userId,message,date;int rating;
        Feedback(String u,String m,int r){userId=u;message=m;rating=r;date=now();}
    }
    static class Store implements Serializable {
        private static final long serialVersionUID=1L;
        List<User> users=new ArrayList<>();List<Service> services=new ArrayList<>();List<Ticket> tickets=new ArrayList<>();List<Feedback> feedback=new ArrayList<>();
    }
    static class Checkout {
        String id,userId,serviceId,date,travelClass;int fare,fee,tax,insurance,total;
        List<Passenger> passengers;List<Integer> seats;long created=System.currentTimeMillis();
        Map<String,Object> data(){return map("id",id,"service",findService(serviceId).data(date),"date",date,"travelClass",travelClass,
            "fare",fare,"fee",fee,"tax",tax,"insurance",insurance,"total",total,"seats",seats,"passengers",passengers.stream().map(Passenger::data).toList());}
    }
    static String now(){return ZonedDateTime.now(INDIA).format(STAMP);}
    static String hash(String pass,String salt) throws Exception {
        PBEKeySpec spec=new PBEKeySpec(pass.toCharArray(),Base64.getDecoder().decode(salt),120000,256);
        try{return Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded());}
        finally{spec.clearPassword();}
    }
    static Map<String,Object> map(Object... vals){Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<vals.length;i+=2)m.put((String)vals[i],vals[i+1]);return m;}
    static String json(Object v){
        if(v==null)return "null";if(v instanceof Boolean || v instanceof Number)return String.valueOf(v);
        if(v instanceof Map<?,?> m){StringJoiner j=new StringJoiner(",","{","}");for(var e:m.entrySet())j.add(json(String.valueOf(e.getKey()))+":"+json(e.getValue()));return j.toString();}
        if(v instanceof Collection<?> c){StringJoiner j=new StringJoiner(",","[","]");for(Object x:c)j.add(json(x));return j.toString();}
        String s=String.valueOf(v);StringBuilder out=new StringBuilder("\"");for(int i=0;i<s.length();i++){char ch=s.charAt(i);switch(ch){case '"'->out.append("\\\"");case '\\'->out.append("\\\\");case '\n'->out.append("\\n");case '\r'->out.append("\\r");case '\t'->out.append("\\t");default->{if(ch<32)out.append(String.format("\\u%04x",(int)ch));else out.append(ch);}}}return out.append('"').toString();
    }
    static synchronized void save() throws Exception {
        Files.createDirectories(DATA.getParent());Path tmp=DATA.resolveSibling(DATA.getFileName()+".tmp");
        try(ObjectOutputStream out=new ObjectOutputStream(Files.newOutputStream(tmp))){out.writeObject(store);}
        try{Files.move(tmp,DATA,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
        catch(AtomicMoveNotSupportedException e){Files.move(tmp,DATA,StandardCopyOption.REPLACE_EXISTING);}
    }
    static synchronized void load() throws Exception {
        if(Files.exists(DATA)){
            try(ObjectInputStream in=new ObjectInputStream(Files.newInputStream(DATA))){store=(Store)in.readObject();}
        }else{store=new Store();seed();save();}
    }
    static void seed() throws Exception {
        if(!PUBLIC_DEMO){
            store.users.add(new User("U1","Demo Passenger","demo@railyatra.test","","Demo@1234","PASSENGER"));
            store.users.add(new User("U2","Demo Admin","admin@railyatra.test","","Admin@123","ADMIN"));
        }else{
            String adminEmail=System.getenv().getOrDefault("RAILYATRA_ADMIN_EMAIL", "").trim();
            String adminPassword=System.getenv().getOrDefault("RAILYATRA_ADMIN_PASSWORD", "");
            if(!adminEmail.isBlank() && adminPassword.length()>=12){
                store.users.add(new User("U2","Project Admin",adminEmail,"",adminPassword,"ADMIN"));
            }
        }
        String[][] trains={
            {"12608","Lalbagh Express","Bengaluru","Chennai","06:30","12:10","5h 40m","550"},
            {"12610","Chennai Night Express","Bengaluru","Chennai","21:00","04:30","7h 30m","720"},
            {"12785","Kacheguda Express","Bengaluru","Hyderabad","18:20","07:00","12h 40m","690"},
            {"12786","Deccan Link","Hyderabad","Bengaluru","19:40","08:20","12h 40m","690"},
            {"12951","Mumbai Rajdhani Demo","Mumbai","Delhi","16:30","08:10","15h 40m","1850"},
            {"12952","Delhi Mumbai Express","Delhi","Mumbai","17:00","09:00","16h 00m","1850"},
            {"12301","Howrah Capital Demo","Kolkata","Delhi","16:50","10:15","17h 25m","1550"},
            {"12842","Coromandel Demo","Chennai","Kolkata","07:00","10:30","27h 30m","1450"},
            {"12627","Karnataka Express Demo","Bengaluru","Delhi","19:00","09:30","38h 30m","1700"},
            {"12711","Goa Coastal Demo","Hyderabad","Goa","06:00","18:30","12h 30m","900"},
            {"11013","Pune Mumbai Intercity","Pune","Mumbai","07:00","10:30","3h 30m","320"},
            {"12959","Jaipur Delhi Express","Jaipur","Delhi","06:20","11:30","5h 10m","440"},
            {"12635","Madurai Chennai Demo","Madurai","Chennai","20:15","06:30","10h 15m","640"},
            {"22691","Coimbatore Bengaluru","Coimbatore","Bengaluru","08:00","15:30","7h 30m","580"},
            {"12007","Mysuru Bengaluru Express","Mysuru","Bengaluru","07:15","10:10","2h 55m","250"},
            {"12797","Vijayawada Hyderabad","Vijayawada","Hyderabad","09:00","15:00","6h 00m","490"},
            {"17261","Kadapa Bengaluru Demo","Kadapa","Bengaluru","22:00","06:00","8h 00m","480"},
            {"17262","Bengaluru Kadapa Demo","Bengaluru","Kadapa","20:45","05:00","8h 15m","480"},
            {"16345","Kochi Bengaluru Demo","Kochi","Bengaluru","18:00","07:30","13h 30m","780"},
            {"16346","Bengaluru Kochi Demo","Bengaluru","Kochi","19:00","08:00","13h 00m","780"},
            {"12402","Ahmedabad Jaipur Demo","Ahmedabad","Jaipur","07:00","16:00","9h 00m","700"},
            {"22411","Lucknow Delhi Demo","Lucknow","Delhi","06:00","13:30","7h 30m","620"},
            {"12133","Mumbai Pune Express","Mumbai","Pune","07:45","11:15","3h 30m","330"},
            {"12798","Hyderabad Vijayawada","Hyderabad","Vijayawada","16:10","22:20","6h 10m","510"},
            {"12609","Chennai Bengaluru Demo","Chennai","Bengaluru","06:45","12:30","5h 45m","570"},
            {"17021","Hyderabad Chennai Demo","Hyderabad","Chennai","18:10","08:00","13h 50m","820"},
            {"17022","Chennai Hyderabad Demo","Chennai","Hyderabad","18:20","08:30","14h 10m","820"}
        };
        int i=1;for(String[] a:trains)store.services.add(new Service("T"+i++,"TRAIN",a[0],a[1],a[2],a[3],a[4],a[5],a[6],Integer.parseInt(a[7]),32));
        String[][] flights={
            {"AV101","AirVista Demo","Bengaluru","Delhi","07:10","09:55","2h 45m","4499"},
            {"SJ210","SkyJet Demo","Bengaluru","Mumbai","10:00","11:55","1h 55m","3399"},
            {"AV202","AirVista Demo","Bengaluru","Hyderabad","16:30","17:40","1h 10m","2899"},
            {"SJ310","SkyJet Demo","Delhi","Bengaluru","15:30","18:10","2h 40m","4599"},
            {"AV404","AirVista Demo","Chennai","Bengaluru","08:45","09:45","1h 00m","2699"},
            {"SJ505","SkyJet Demo","Bengaluru","Goa","09:20","10:30","1h 10m","3199"},
            {"AV606","AirVista Demo","Hyderabad","Delhi","06:40","08:50","2h 10m","3999"},
            {"SJ707","SkyJet Demo","Mumbai","Delhi","20:00","22:10","2h 10m","4099"},
            {"AV808","AirVista Demo","Bengaluru","Chennai","13:30","14:30","1h 00m","2799"},
            {"SJ909","SkyJet Demo","Mumbai","Bengaluru","12:00","13:55","1h 55m","3499"},
            {"AV111","AirVista Demo","Delhi","Mumbai","09:20","11:25","2h 05m","3999"},
            {"SJ121","SkyJet Demo","Kolkata","Delhi","14:15","16:35","2h 20m","4199"},
            {"AV131","AirVista Demo","Chennai","Hyderabad","11:15","12:30","1h 15m","3099"},
            {"SJ141","SkyJet Demo","Hyderabad","Bengaluru","08:10","09:25","1h 15m","2999"},
            {"AV151","AirVista Demo","Delhi","Jaipur","11:00","12:00","1h 00m","2599"}
        };
        i=1;for(String[] a:flights)store.services.add(new Service("F"+i++,"FLIGHT",a[0],a[1],a[2],a[3],a[4],a[5],a[6],Integer.parseInt(a[7]),24));
    }
    static User findUser(String id){for(User u:store.users)if(u.id.equals(id))return u;return null;}
    static Service findService(String id){for(Service s:store.services)if(s.id.equals(id))return s;return null;}
    static Ticket findTicket(String pnr){for(Ticket t:store.tickets)if(t.pnr.equalsIgnoreCase(pnr))return t;return null;}
    static String val(Map<String,String> f,String name){return f.getOrDefault(name,"").trim();}
    static int intVal(Map<String,String> f,String name){try{return Integer.parseInt(val(f,name));}catch(Exception e){throw new IllegalArgumentException("Invalid "+name);}}
    static Map<String,String> params(String raw){Map<String,String> m=new LinkedHashMap<>();if(raw==null)return m;
        for(String a:raw.split("&")){int sep=a.indexOf('=');if(sep<0)continue;m.put(URLDecoder.decode(a.substring(0,sep),StandardCharsets.UTF_8),URLDecoder.decode(a.substring(sep+1),StandardCharsets.UTF_8));}return m;}
    static class Problem extends RuntimeException {int status;Problem(int s,String m){super(m);status=s;}}
    static User getUser(HttpExchange ex){
        String cookies=ex.getRequestHeaders().getFirst("Cookie");if(cookies==null)return null;
        for(String c:cookies.split(";"))if(c.trim().startsWith("ry_session="))return findUser(sessions.getOrDefault(c.trim().substring(11),""));
        return null;
    }
    static User required(HttpExchange ex){User u=getUser(ex);if(u==null)throw new Problem(401,"Please log in to continue");return u;}
    static void adminOnly(HttpExchange ex){if(!"ADMIN".equals(required(ex).role))throw new Problem(403,"Admin access required");}
    static void send(HttpExchange ex,int status,Object body) throws IOException {
        byte[] b=json(body).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,b.length);try(OutputStream out=ex.getResponseBody()){out.write(b);}
    }
    static void api(HttpExchange ex) throws IOException {
        try{
            String path=ex.getRequestURI().getPath(),method=ex.getRequestMethod();
            Map<String,String> f="GET".equals(method)?params(ex.getRequestURI().getRawQuery()):params(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            Object result=route(ex,method,path,f);
            send(ex,200,map("ok",true,"data",result));
        }catch(Problem e){send(ex,e.status,map("ok",false,"error",e.getMessage()));}
        catch(IllegalArgumentException e){send(ex,400,map("ok",false,"error",e.getMessage()));}
        catch(Exception e){e.printStackTrace();send(ex,500,map("ok",false,"error","Unable to complete request. Please try again."));}
    }
    static synchronized Object route(HttpExchange ex,String method,String path,Map<String,String> f) throws Exception {
        if(path.equals("/api/bootstrap")&&method.equals("GET")){
            User u=getUser(ex);TreeSet<String> stations=new TreeSet<>();for(Service s:store.services){stations.add(s.from);stations.add(s.to);}
            return map("user",u==null?null:u.data(),"stations",stations,"today",LocalDate.now(INDIA).toString(),
                "trainCount",store.services.stream().filter(s->s.type.equals("TRAIN")).count(),"flightCount",store.services.stream().filter(s->s.type.equals("FLIGHT")).count(),"publicDemo",PUBLIC_DEMO);
        }
        if(path.equals("/api/auth/register")&&method.equals("POST")){
            String name=val(f,"name"),email=val(f,"email").toLowerCase(Locale.ROOT),phone=val(f,"phone"),pw=val(f,"password");
            if(name.length()<2||name.length()>80)throw new IllegalArgumentException("Enter a name (2 to 80 characters)");
            if(!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"))throw new IllegalArgumentException("Enter a valid email address");
            if(pw.length()<8)throw new IllegalArgumentException("Password must contain at least 8 characters");
            for(User u:store.users)if(u.email.equals(email))throw new IllegalArgumentException("Email is already registered. Please log in.");
            User u=new User("U"+UUID.randomUUID().toString().substring(0,8),name,email,phone,pw,"PASSENGER");store.users.add(u);save();loginCookie(ex,u);return u.data();
        }
        if(path.equals("/api/auth/login")&&method.equals("POST")){
            String email=val(f,"email").toLowerCase(Locale.ROOT),pw=val(f,"password");
            for(User u:store.users)if(u.email.equals(email)&&MessageDigest.isEqual(hash(pw,u.salt).getBytes(StandardCharsets.UTF_8),u.hash.getBytes(StandardCharsets.UTF_8))){loginCookie(ex,u);return u.data();}
            throw new Problem(401,"Incorrect email or password");
        }
        if(path.equals("/api/auth/logout")&&method.equals("POST")){
            String cookie=ex.getRequestHeaders().getFirst("Cookie");if(cookie!=null)for(String c:cookie.split(";"))if(c.trim().startsWith("ry_session="))sessions.remove(c.trim().substring(11));
            ex.getResponseHeaders().add("Set-Cookie","ry_session=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");return map("message","Logged out");
        }
        if(path.equals("/api/services")&&method.equals("GET")){
            String date=val(f,"date");if(date.isEmpty())date=LocalDate.now(INDIA).plusDays(1).toString();checkDate(date);
            String type=val(f,"type").toUpperCase(),from=val(f,"from"),to=val(f,"to"),q=val(f,"q").toLowerCase(Locale.ROOT);
            List<Object> results=new ArrayList<>();for(Service s:store.services){if(!s.active&&!("1".equals(val(f,"all"))&&getUser(ex)!=null&&"ADMIN".equals(getUser(ex).role)))continue;
                if(!type.isEmpty()&&!s.type.equals(type))continue;
                if(!from.isEmpty()&&!s.from.equalsIgnoreCase(from))continue;
                if(!to.isEmpty()&&!s.to.equalsIgnoreCase(to))continue;
                if(!q.isEmpty()&&!s.name.toLowerCase(Locale.ROOT).contains(q)&&!s.number.toLowerCase(Locale.ROOT).contains(q))continue;
                results.add(s.data(date));}
            return map("services",results,"date",date,"count",results.size());
        }
        if(path.equals("/api/stationboard")&&method.equals("GET")){
            String station=val(f,"station"),date=LocalDate.now(INDIA).toString();List<Object> results=new ArrayList<>();
            for(Service s:store.services){if(!s.active||!s.type.equals("TRAIN")||(!station.isEmpty()&&!s.from.equalsIgnoreCase(station)))continue;
                results.add(s.data(date));}return map("services",results,"station",station,"notice","Sample timetable and simulated delays — NOT live railway data");
        }
        if(path.equals("/api/checkout")&&method.equals("POST")){
            User u=required(ex);Service s=findService(val(f,"serviceId"));if(s==null||!s.active)throw new IllegalArgumentException("Selected service is unavailable");
            String date=val(f,"date");checkDate(date);
            String cls=val(f,"travelClass");double mult=classMultiplier(s.type,cls);
            int count=intVal(f,"pcount");if(count<1||count>6)throw new IllegalArgumentException("Choose between 1 and 6 passengers");
            String[] picked=val(f,"seats").split(",");if(picked.length!=count)throw new IllegalArgumentException("Choose one seat for each passenger");
            Set<Integer> unique=new LinkedHashSet<>();List<Passenger> people=new ArrayList<>();for(int i=0;i<count;i++){
                String n=val(f,"p"+i+"name"),g=val(f,"p"+i+"gender");int a=intVal(f,"p"+i+"age");
                if(n.length()<2||n.length()>80||a<1||a>120||!Arrays.asList("Female","Male","Other").contains(g))throw new IllegalArgumentException("Check passenger "+(i+1)+" details");
                people.add(new Passenger(n,a,g));
            }
            for(String x:picked){int seat;try{seat=Integer.parseInt(x.trim());}catch(Exception e){throw new IllegalArgumentException("Invalid seat selected");}
                if(seat<1||seat>s.capacity||!unique.add(seat))throw new IllegalArgumentException("Please choose different valid seats");
                for(Ticket t:store.tickets)if(t.serviceId.equals(s.id)&&t.date.equals(date)&&t.status.equals("CONFIRMED")&&t.seats.contains(seat))throw new IllegalArgumentException("Seat "+seat+" was already booked. Refresh the seat map.");
            }
            Checkout c=new Checkout();c.id=UUID.randomUUID().toString();c.userId=u.id;c.serviceId=s.id;c.date=date;c.travelClass=cls;c.passengers=people;c.seats=new ArrayList<>(unique);
            c.fare=(int)Math.round(s.fare*mult*count);c.tax=(int)Math.round(c.fare*0.05);c.fee=19;c.insurance="1".equals(val(f,"insurance"))?count*15:0;c.total=c.fare+c.tax+c.fee+c.insurance;
            checkouts.put(c.id,c);return c.data();
        }
        if(path.equals("/api/payment/complete")&&method.equals("POST")){
            User u=required(ex);Checkout c=checkouts.get(val(f,"checkoutId"));if(c==null||!c.userId.equals(u.id))throw new IllegalArgumentException("Checkout expired. Please start again.");
            if(System.currentTimeMillis()-c.created>20*60*1000L){checkouts.remove(c.id);throw new IllegalArgumentException("Checkout expired (20 minutes). Please start again.");}
            String methodName=val(f,"paymentMode");if(!Arrays.asList("UPI QR","UPI ID","Google Pay","PhonePe","Paytm","Net Banking","Demo Card").contains(methodName))throw new IllegalArgumentException("Choose a demo payment method");
            for(Ticket old:store.tickets)if(old.serviceId.equals(c.serviceId)&&old.date.equals(c.date)&&old.status.equals("CONFIRMED"))for(int seat:c.seats)if(old.seats.contains(seat))throw new IllegalArgumentException("Seat "+seat+" was booked by someone else. Please choose again.");
            Ticket t=new Ticket();t.pnr="RY"+UUID.randomUUID().toString().replace("-","").substring(0,11).toUpperCase(Locale.ROOT);t.userId=u.id;t.serviceId=c.serviceId;t.date=c.date;t.travelClass=c.travelClass;
            t.method=methodName;t.status="CONFIRMED";t.paidAt=now();t.refundStatus="Not requested";t.fare=c.fare;t.fee=c.fee;t.insurance=c.insurance;t.tax=c.tax;t.total=c.total;t.refund=0;t.passengers=c.passengers;t.seats=c.seats;
            store.tickets.add(t);save();checkouts.remove(c.id);return t.data();
        }
        if(path.equals("/api/tickets")&&method.equals("GET")){
            User u=required(ex);List<Object> ts=new ArrayList<>();for(int i=store.tickets.size()-1;i>=0;i--){Ticket t=store.tickets.get(i);if(u.id.equals(t.userId)||u.role.equals("ADMIN"))ts.add(t.data());}
            return map("tickets",ts);
        }
        if(path.equals("/api/ticket")&&method.equals("GET")){
            User u=required(ex);Ticket t=findTicket(val(f,"pnr"));if(t==null||(!t.userId.equals(u.id)&&!u.role.equals("ADMIN")))throw new Problem(404,"Ticket not found");return t.data();
        }
        if(path.equals("/api/tickets/cancel")&&method.equals("POST")){
            User u=required(ex);Ticket t=findTicket(val(f,"pnr"));if(t==null||!t.userId.equals(u.id))throw new Problem(404,"Ticket not found");
            if(!"YES".equals(val(f,"confirm")))throw new IllegalArgumentException("Please confirm cancellation");
            if(!t.status.equals("CONFIRMED"))throw new IllegalArgumentException("This ticket has already been cancelled");
            LocalDate journey=LocalDate.parse(t.date);long days=java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(INDIA),journey);
            if(days<0)throw new IllegalArgumentException("Past journey tickets cannot be cancelled in this demo");
            t.status="CANCELLED";t.cancelledAt=now();t.refund=days>=2?(int)Math.round(t.total*0.90):days==1?(int)Math.round(t.total*0.70):(int)Math.round(t.total*0.50);
            t.refundStatus="Demo refund calculated — no real funds transferred";save();return t.data();
        }
        if(path.equals("/api/feedback")&&method.equals("POST")){
            User u=required(ex);String message=val(f,"message");int rating=intVal(f,"rating");if(message.length()<5||message.length()>800||rating<1||rating>5)throw new IllegalArgumentException("Enter feedback (5–800 characters) and a rating of 1–5");
            store.feedback.add(new Feedback(u.id,message,rating));save();return map("message","Thanks! Feedback saved on this demo server.");
        }
        if(path.equals("/api/admin/overview")&&method.equals("GET")){
            adminOnly(ex);return map("passengers",store.users.size(),"services",store.services.size(),"bookings",store.tickets.size(),"cancellations",store.tickets.stream().filter(t->t.status.equals("CANCELLED")).count(),"feedback",store.feedback.size());
        }
        if(path.equals("/api/admin/services")&&method.equals("POST")){
            adminOnly(ex);String type=val(f,"type");if(!type.equals("TRAIN")&&!type.equals("FLIGHT"))throw new IllegalArgumentException("Choose train or flight");
            String number=val(f,"number"),name=val(f,"name"),from=val(f,"from"),to=val(f,"to"),dep=val(f,"departure"),arr=val(f,"arrival"),duration=val(f,"duration");
            int fare=intVal(f,"fare"),cap=intVal(f,"capacity");
            if(number.length()<2||name.length()<2||from.length()<2||to.length()<2||from.equalsIgnoreCase(to)||fare<1||cap<1||cap>64||!dep.matches("\\d{2}:\\d{2}")||!arr.matches("\\d{2}:\\d{2}"))throw new IllegalArgumentException("Check the service details, times, fare and capacity");
            for(Service s:store.services)if(s.number.equalsIgnoreCase(number))throw new IllegalArgumentException("Service number already exists");
            Service s=new Service("NEW"+UUID.randomUUID().toString().substring(0,8),type,number,name,from,to,dep,arr,duration,fare,cap);store.services.add(s);save();return s.data(LocalDate.now(INDIA).toString());
        }
        if(path.equals("/api/admin/toggle")&&method.equals("POST")){
            adminOnly(ex);Service s=findService(val(f,"id"));if(s==null)throw new IllegalArgumentException("Service not found");s.active=!s.active;save();return map("active",s.active);
        }
        throw new Problem(404,"Unknown API endpoint");
    }
    static double classMultiplier(String type,String c){
        Map<String,Double> m=type.equals("TRAIN")?Map.of("Sleeper",1.0,"3A",1.55,"2A",2.15,"1A",3.0):Map.of("Economy",1.0,"Premium Economy",1.6,"Business",2.6);
        if(!m.containsKey(c))throw new IllegalArgumentException("Choose a valid travel class");return m.get(c);
    }
    static void checkDate(String date){try{LocalDate d=LocalDate.parse(date);if(d.isBefore(LocalDate.now(INDIA))||d.isAfter(LocalDate.now(INDIA).plusMonths(5)))throw new IllegalArgumentException("Choose a travel date within the next 5 months");}catch(java.time.format.DateTimeParseException e){throw new IllegalArgumentException("Choose a valid travel date");}}
    static void loginCookie(HttpExchange ex,User u){String token=UUID.randomUUID().toString().replace("-","");sessions.put(token,u.id);ex.getResponseHeaders().add("Set-Cookie","ry_session="+token+"; Path=/; HttpOnly; SameSite=Lax"+(PUBLIC_DEMO?"; Secure":""));}
    static void staticFile(HttpExchange ex) throws IOException {
        String path=ex.getRequestURI().getPath();if(path.equals("/"))path="/index.html";
        if(path.contains("..")){ex.sendResponseHeaders(400,-1);return;}
        String type=path.endsWith(".css")?"text/css":path.endsWith(".js")?"text/javascript":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".pdf")?"application/pdf":"text/html";
        try(InputStream stream=Main.class.getResourceAsStream("/public"+path)){
            if(stream==null){ex.sendResponseHeaders(404,-1);return;}
            byte[] bytes=stream.readAllBytes();ex.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");ex.sendResponseHeaders(200,bytes.length);try(OutputStream out=ex.getResponseBody()){out.write(bytes);}
        }
    }
    public static void main(String[] args) throws Exception {
        load();HttpServer server=null;
        String portEnv=System.getenv("PORT");
        if(portEnv!=null&&!portEnv.isBlank()){
            port=Integer.parseInt(portEnv);server=HttpServer.create(new InetSocketAddress("0.0.0.0",port),0);
        }else{
            for(int tryPort=8080;tryPort<=8086;tryPort++)try{server=HttpServer.create(new InetSocketAddress("127.0.0.1",tryPort),0);port=tryPort;break;}catch(BindException ignored){}
        }
        if(server==null)throw new IllegalStateException("Ports 8080-8086 are occupied. Close previous servers and try again.");
        server.createContext("/api/",Main::api);server.createContext("/",Main::staticFile);
        server.setExecutor(Executors.newFixedThreadPool(12));server.start();
        String url="http://localhost:"+port;
        System.out.println("\n====================================================");
        System.out.println("  RAILYATRA — RAILWAY + FLIGHT DEMO WEBSITE");
        System.out.println("  Open in Chrome / Edge: " + url);
        if(!PUBLIC_DEMO){System.out.println("  Demo passenger: demo@railyatra.test / Demo@1234");System.out.println("  Demo admin: admin@railyatra.test / Admin@123");}
        else System.out.println("  Public demo: create a temporary account; no live money or tickets.");
        System.out.println("  Educational demo only. No real tickets or money.");
        System.out.println("  Press Ctrl+C to stop the server.");
        System.out.println("====================================================\n");
        if(portEnv==null)try{if(Desktop.isDesktopSupported())Desktop.getDesktop().browse(URI.create(url));}catch(Throwable ignored){}
    }
}
