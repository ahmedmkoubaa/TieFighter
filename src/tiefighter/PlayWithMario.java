package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import swing.LARVACompactDash;
import swing.LARVADash;

public class PlayWithMario extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, SOLVEPROBLEM, 
        CLOSEPROBLEM, EXIT
    }
    
    Status mystatus;
    String service = "PManager", problem = "PlayWithMario",
            problemManager = "", content, sessionKey, 
            sessionManager, storeManager, sensorKeys;
    
    // Resolver problema de manera sencilla usando
    // un vector de acciones fijas, util solo para esta practica
    String actions [] = {
        "LEFT", "LEFT", "LEFT", 
        "MOVE", "MOVE", "MOVE", 
        "MOVE", "MOVE", "MOVE", 
        "MOVE", "MOVE", "MOVE", 
        "MOVE", "CAPTURE"
    };
    int sigAction = 0;
    
    int width, height, maxFlight;
    
    ACLMessage open, session, mario;
    ACLMessage marioIn, marioOut;
    
    private Boolean estamosJugando = false;
    private Boolean hemosAcabado = false;
    
    
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
    }

    @Override
    public void Execute() {
        Info("Status: " + mystatus.name());
        if (step) {
            step = this.Confirm("The next status will be " + mystatus.name() 
                                + "\n\nWould you like to continue step by step?");
        }
        switch (mystatus) {
            case CHECKIN:
                mystatus = MyCheckin();
                break;
            case OPENPROBLEM:
                mystatus = MyOpenProblem();
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
        if (!loadMyPassport("MyPassport.passport")) {
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
            mario = LARVAblockingReceive();
            
            sessionManager = mario.getSender().getLocalName();
            Info(sessionManager + " says: " + mario.getContent());
            return Status.SOLVEPROBLEM;
        } else {
            Error(content);
            return Status.CHECKOUT;
        }
    }


    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        inbox = LARVAblockingReceive();
        Info(problemManager + " says: " + inbox.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }

    private String myTakeDecision() {
        String nextAction = "";
        
        // Si el dron sigue vivo y tiene energia
        if (myDashboard.getAlive() && myDashboard.getEnergy() > 0) {
            int lidar[][] = this.myDashboard.getLidar();

            // Si no estamos sobre el objetivo
            if (this.myDashboard.getDistance() > 0) {
                final int gradoTotal = 360;
                final int compass = this.myDashboard.getCompass();
                final double angular = this.myDashboard.getAngular();
                    
//                double izda = Math.abs(angular - compass);
//                double dcha = gradoTotal - izda;
//                
//                Info("Izda: " + izda + " Dcha: " + dcha);
                    
                // De ser iguales estariamos apuntando hacia el frente
//                if (izda != dcha) {
//                    // Cogemos el minimo
//                    nextAction = izda > dcha ? "RIGHT":"LEFT";
                if(compass != angular) {
                    nextAction = "LEFT";
                } else {
                    nextAction = "MOVE";
                    int alturaEnfrente = 0;
                    
                    // Hallar casilla de enfrente
                    switch(compass){
                        case 0:     alturaEnfrente = lidar[5][6]; break;
                        case 45:    alturaEnfrente = lidar[4][6]; break;
                        case 90:    alturaEnfrente = lidar[4][5]; break;
                        case 135:   alturaEnfrente = lidar[4][4]; break;
                        case 180:   alturaEnfrente = lidar[5][4]; break;
                        case 225:   alturaEnfrente = lidar[6][4]; break;
                        case 270:   alturaEnfrente = lidar[6][5]; break;
                        case 315:   alturaEnfrente = lidar[6][6]; break;
                        default: Alert("Compass no reconocido " + compass); break;
                    }
                    
                    Info("ALTURA ENFRENTE ES: " + alturaEnfrente + "\n\n\n");

                    // Si enfrente es mas alto que dron hay que subir
                    if (alturaEnfrente < 0) {
                        nextAction = "UP";
                    }
                }
            } else {
                // Si estamos sobre el objetivo pero mas altos que
                // este, habra que descender
                if (lidar[5][5] > 0) {
                    nextAction = "DOWN";
                } else {
                    // capturar objetivo
                    nextAction = "CAPTURE";
                }
            }
        } else {
            Info("TieFighter sin vida, fin del juego");
        }
        
        return nextAction;
    }
    
    // Funcion mas importante, resuelve el problema que se abra, en este caso
    // el de Dagobah
    public Status MySolveProblem() {
        while (!estamosJugando) {
//            Info("NO ESTAMOS JUGANDO CON MARIO");
            
            mario = mario.createReply();
            mario.setPerformative(ACLMessage.REQUEST);
            this.send(mario);

            mario = blockingReceive();
            Info("Mario dice: " + mario.getContent());
            
            if (mario.getPerformative() == ACLMessage.AGREE) {
                estamosJugando = true;
                break;
            } else {
//                Info("MARIO DICE QUE NO QUIERE JUGAR");
            }
        }
        
        Info("AHORA SÍ ESTAMOS JUGANDO");
        
        marioOut = mario.createReply();
        
        for (int i = 0; i <= 100 && !hemosAcabado; i++) {
            
            Info("Antes de content");
            
            marioOut.setContent(Integer.toString(i));
            marioOut.setPerformative(ACLMessage.QUERY_IF);
            this.send(marioOut);
            
            Info("Despues de content y performative");
            
            marioIn = this.LARVAblockingReceive();
            Info("Tras el bloqueo");
            
            Info("Que estamos haciendo: " + i);
            
            Info(marioIn.getContent());
            if (marioIn.getPerformative() == ACLMessage.INFORM) {
                if (marioIn.getContent().equals("That's it!")) {
                    
                    marioOut = marioIn.createReply();
                    marioOut.setPerformative(ACLMessage.CANCEL);
                    this.send(marioOut);
                    
                    
                    marioIn = blockingReceive();
                    
                    hemosAcabado = true;
                    i = 1000;
                    break;
                    
                }
            }
            
            marioOut = marioIn.createReply();
        }
        
        
        
        
       return (hemosAcabado ? Status.CLOSEPROBLEM : Status.SOLVEPROBLEM);
    }
    
    // lee sensores mediante peticiones al sensorManager, si fue lectura 
    // correcta devuelve true, en otro caso devuelve false
    private boolean myReadSensors() {
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Query sensors session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request query sensors session to " + sessionManager);
        
        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        // Comprobar que la lectura fuese correcta
        if (content.startsWith("Refuse") || content.startsWith("Failure")) {
            Alert(content);
            return false;
        }
        
        return true;
    }
    // Ejecuta una accion enviando un mensaje al session manager
    // Devuelve true en caso de que la accion se ejecutase correctamente
    // false en otro caso diferente
    private boolean myExecuteAction(String accion){
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setContent("Request execute " + accion + " session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request executing action " + accion + " to " + sessionManager);
        
        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        if (content.startsWith("Inform")) {
            return true;
        } else {
            Alert(content);
            return false;
        }        
    }
}
