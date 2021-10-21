package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import swing.LARVADash;

public class MyFirstTieFighter extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, COMISSIONING, JOINSESSION, SOLVEPROBLEM, CLOSEPROBLEM, EXIT
    }
    Status mystatus;
    String service = "PManager", problem = "Dagobah",
            problemManager = "", content, sessionKey, sessionManager, storeManager,
            sensorKeys;
    int width, height, maxFlight;
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
                "ALIVE",
                "ONTARGET",
                "GPS",
                "COMPASS",
                "LIDAR",
                "ALTITUDE",
                "VISUAL",
                "ENERGY",
                "PAYLOAD",
                "DISTANCE",
                "ANGULAR",
                "THERMAL"
            };
    boolean step = true;

    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");
        this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        this.myDashboard = new LARVADash(this);
        this.doActivateLARVADash();
    }

    @Override
    public void Execute() {
        Info("Status: " + mystatus.name());
        if (step) {
            step = this.Confirm("The next status will be " + mystatus.name() + "\n\nWould you like to continue step by step?");
        }
        switch (mystatus) {
            case CHECKIN:
                mystatus = MyCheckin();
                break;
            case OPENPROBLEM:
                mystatus = MyOpenProblem();
                break;
            case COMISSIONING:
                mystatus = MyComissioning();
                break;
            case JOINSESSION:
                mystatus = MyJoinSession();
                break;
            case SOLVEPROBLEM:
                mystatus = MySolveProblem();
                break;
            case CLOSEPROBLEM:
                mystatus = MyCloseProblem();
                break;
            case CHECKOUT:
                mystatus = MyCheckout();
                break;
            case EXIT:
            default:
                exit = true;
                break;
        }
    }

    @Override
    public void takeDown() {
        Info("Taking down and deleting agent");
        this.saveSequenceDiagram("./" + this.problem + ".seqd");
        super.takeDown();
    }

    public Status MyCheckin() {
        Info("Loading passport and checking-in to LARVA");
        if (!loadMyPassport("passport/MyPassport.passport")) {
            Error("Unable to load passport file");
            return Status.EXIT;
        }
        if (!doLARVACheckin()) {
            Error("Unable to checkin");
            return Status.EXIT;
        }
        return Status.OPENPROBLEM;
    }

    public Status MyOpenProblem() {
        if (this.DFGetAllProvidersOf(service).isEmpty()) {
            Error("Service PMANAGER is down");
            return Status.CHECKOUT;
        }
        problemManager = this.DFGetAllProvidersOf(service).get(0);
        Info("Found problem manager " + problemManager);
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(problemManager, AID.ISLOCALNAME));
        outbox.setContent("Request open " + problem);
        this.LARVAsend(outbox);
        Info("Request opening problem " + problem + " to " + problemManager);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        content = open.getContent();
        contentTokens = content.split(" ");
        if (contentTokens[0].toUpperCase().equals("AGREE")) {
            sessionKey = contentTokens[4];
            session = LARVAblockingReceive();
            sessionManager = session.getSender().getLocalName();
            Info(sessionManager + " says: " + session.getContent());
            return Status.COMISSIONING;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }

    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem TieFighter, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }
    
    public Status MyComissioning() {
        String localService = "STORE " + sessionKey;
        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service STORE is down");
            return Status.CLOSEPROBLEM;
        }
        storeManager = this.DFGetAllProvidersOf(localService).get(0);
        Info("Found store manager " + storeManager);
        sensorKeys = "";
        for (String s: mySensors) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID(storeManager, AID.ISLOCALNAME));
            outbox.setContent("Request product " + s + " session " + sessionKey);
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            if (inbox.getContent().startsWith("Confirm")) {
                Info("Bought sensor " + s);
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            } else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CLOSEPROBLEM;
            }
        }
        Info("Bought all sensor keys " + sensorKeys);
        return Status.JOINSESSION;
    }
    
    public Status MyJoinSession() {
        session = session.createReply();
        session.setContent("Request join session " + sessionKey +
                " attach sensors " + sensorKeys);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        String parse[] = session.getContent().split(" ");
        if (parse[0].equals("Confirm")) {
            width = Integer.parseInt(parse[8]);
            height = Integer.parseInt(parse[10]);
            maxFlight = Integer.parseInt(parse[14]);
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }

    public Status MySolveProblem() {
        session = session.createReply();
        session.setContent("Query sensors session " + sessionKey);
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        if (session.getContent().startsWith("Refuse") ||
                session.getContent().startsWith("Failure")) {
            Alert("Reading of sensors failed due to " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
        double gps[] = myDashboard.getGPS();
        double angular = myDashboard.getAngular();
        double distance = myDashboard.getDistance();
        int thermal[][] = myDashboard.getThermal();
        
        Info("Reading of GPS\nX=" + gps[0] + " Y=" + gps[1] + " Z=" + gps[2]);
        Info("Reading of angular = " + angular + "º");
        Info("Reading of distance = " + distance + "m");
        String message = "Reading of sensor Thermal;\n";
        for (int y = 0; y < thermal.length; y++) {
            for (int x = 0; x < thermal[0].length; x++) {
                message += String.format("%03d ", thermal[x][y]);
            }
            message += "\n";
        }
        Info(message);
        
        String[] myMoves = new String[] {
                "LEFT",
                "LEFT",
                "LEFT",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "MOVE",
                "CAPTURE"
            };
        
        this.outbox = new ACLMessage();
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        
        for (String move: myMoves) {
            outbox.setContent("Request execute " + move + " session " + sessionKey);
            this.LARVAsend(outbox);
            Info("Request execute " + move + " to " + sessionManager);
            inbox = LARVAblockingReceive();
            Info(sessionManager + " says: " + inbox.getContent());
            
            session = session.createReply();
            session.setContent("Query sensors session " + sessionKey);
            this.LARVAsend(session);
            session = this.LARVAblockingReceive();
            if (session.getContent().startsWith("Refuse") ||
                    session.getContent().startsWith("Failure")) {
                Alert("Reading of sensors failed due to " + session.getContent());
                return Status.CLOSEPROBLEM;
            }
        }
        
        return Status.CLOSEPROBLEM;
    }

}
