package tiefighter;

import agents.LARVAFirstAgent;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import swing.LARVACompactDash;
import swing.LARVADash;

public class Practica2 extends LARVAFirstAgent{

    enum Status {
        CHECKIN, CHECKOUT, OPENPROBLEM, 
        COMISSIONING, JOINSESSION, SOLVEPROBLEM, 
        CLOSEPROBLEM, EXIT
    }
    
    
    Status mystatus;
    
    // MUNDOS 
    // P1: Dagobah 
    // P2-basicos: Abafar Batuu Chandrila Dathomir Endor 
    // P2-avanzados: Felucia Hoth Mandalore Tatooine
    
    String service = "PManager", problem = "Hoth",
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
    private int sigAction = 0;
    private final int gradoTotal = 360;
   
    private double maxEnergy = -1;
    private final double porcentajeLimite = 0.4;
    private final double porcentajeCercania = 0.8;
    private final int alturaCercania = 20;
    
    // Atributos en los que se almacenaran los valores
    // correspondientes a umbrales de recarga
    private double umbralLimiteRecarga;
    private double umbralCercaniaRecarga;
    
    // indica que estamos evitando lo que se encuentre a nuestra izquierda
    private Boolean evitandoIzquierda = false;
    
    // indica que se esta evitando lo que se encuentra a nuestra derecha
    private Boolean evitandoDerecha = true;
    
    private ArrayList<double[]> casillasProhibidas = new ArrayList<>();
    
    private ArrayList<String> acciones = new ArrayList<>();
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
                "ALIVE",
                "ONTARGET",   // No 
                "GPS",        // No
                "COMPASS",
                "LIDAR",
                "ALTITUDE",   // No
                "VISUAL",     // No
                "ENERGY",
                "PAYLOAD",
                "DISTANCE",
                "ANGULAR",
                "THERMAL"     // No
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
        
//        this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
        doActivateLARVADash();
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
    
    public Status MyComissioning(){
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
            outbox.addReceiver(new AID (storeManager, AID.ISLOCALNAME));
            outbox.setContent("Request product " + s + " session " + sessionKey);
            
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            
            if (inbox.getContent().startsWith("Confirm")) {
                Info("bought sensor " + s) ;
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            } else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CLOSEPROBLEM;
            }
        }
        
        Info("bought all sensor keys " + sensorKeys);
//        return Status.CLOSEPROBLEM;
        return Status.JOINSESSION;
    }
    
    public Status MyJoinSession(){
        session = session.createReply();
        session.setContent("Request join session " + sessionKey 
                         + " attach sensors " + sensorKeys);
        
        this.LARVAsend(session);
        session = this.LARVAblockingReceive();
        
        String parse[] = session.getContent().split(" ");
        
        if (parse[0].equals("Confirm")) {
            width = Integer.parseInt(parse[8]);
            height = Integer.parseInt(parse[10]);
            maxFlight = Integer.parseInt(parse[14]);
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error: joining session: " + session.getContent());
            return Status.CLOSEPROBLEM;
        }
    }

    private String myTakeDecision() {
        String nextAction = "";
        
        if (maxEnergy < 0) {
            maxEnergy = myDashboard.getEnergy() + myDashboard.getEnergyBurnt();   
            umbralLimiteRecarga = porcentajeLimite * maxEnergy;
            umbralCercaniaRecarga = porcentajeCercania * maxEnergy;
            nextAction = "RECHARGE";
        }
        else {
            // Si el dron sigue vivo y tiene energia
            if (myDashboard.getAlive() && myDashboard.getEnergy() > 0) {
                int lidar[][] = this.myDashboard.getLidar();


                if (myDashboard.getEnergy() <= umbralLimiteRecarga ||
                        (myDashboard.getEnergy() <= umbralCercaniaRecarga && myDashboard.getAltitude() <= alturaCercania)) {

                    // Recargar
                    if (lidar[5][5] > 0) {
                        nextAction = "DOWN";
                    } else {
                        nextAction = "RECHARGE";
                    }
                    
                } else {
                    // Si no estamos sobre el objetivo
                    if (this.myDashboard.getDistance() > 0) {
                        
                        final int compass = this.myDashboard.getCompass();
                        final double angular = this.myDashboard.getAngular();
                        final double miCasilla[] = this.myDashboard.getGPS();
                        double miAltura = miCasilla[2];

                        // ------------------------------------------------------------------- //
                        /* NUEVO AHMED: 
                            si se esta evitando ir por la izquierda o la derecha
                            no se pasa a calcular la distancia de giro minima, 
                            directamente se descarta dar un giro (independientemente
                            del sentido que se este esquivando)
                        */
                        double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;
                        if( distanciaAngulo >= 45 && !(evitandoIzquierda || evitandoDerecha)) {
                           
                            // Elegir distancia de giro minimo
                            if ( distanciaAngulo < gradoTotal/2 ) {
                                 nextAction = "LEFT";       
                            }
                            else {
                                 nextAction = "RIGHT"; 
                            }
                        } else {
                            // ------------------------------------------------------------------- //
                            /* NUEVO AHMED: 
                                Si se esta esquivando algun lado, se comprueba
                                la altura en direccion objetivo, si es menor a 
                                la nuestra entonce se avanza hacia alla
                            */
                            
                            if (evitandoIzquierda || evitandoDerecha) {
                                double alturaDireccionAngular = mapearAlturaSegunAngulo((int)angular, lidar);
                                // obtener la posición de la casilla en ese sentido y comprobar
                                // si esta en el vector de prohibidas
                                
                                double casillaDireccionObjetivo[] = 
                                        getCasillaDireccionObjetivo(angular, miCasilla);
//                                
//                                Alert("Casilla de enfrente es x: " 
//                                        + casillaDireccionObjetivo[0] 
//                                        + " y: " + casillaDireccionObjetivo[1]
//                                        + " numero de casillas prohibidas: " + casillasProhibidas.size()
//                                );
                                
                                
//                                int casillaDireccionAngular[] = getCasillaAngulo(angulo, mydashBoard.getBoolean encontrada = false;
                                
                                Boolean encontrada = false;
                                for (double g[]: casillasProhibidas) {
                                    if (g[0] == casillaDireccionObjetivo[0] 
                                            && g[1] == casillaDireccionObjetivo[1]) {
                                        encontrada = true;
                                        break;
                                    }
                                        
                                }
                                
                                if (!encontrada || alturaDireccionAngular < miAltura) {
                                    evitandoIzquierda = evitandoDerecha = false;  
                                }
                                
                                
                    
                                
                                // si la altura de la casilla en direccion el objetivo es inferior a mi
                                // entonces anulo esquivar y dejo que el algoritmo vuelva a apuntar hacia alla
//                                if (alturaDireccionAngular < miAltura){
//                                  evitandoIzquierda = evitandoDerecha = false;  
//                                }
                            }
                           
                            nextAction = "MOVE";
                            double casillaDireccionObjetivo[] = 
                                        getCasillaDireccionObjetivo(compass, miCasilla);
                            Boolean encontrada = false;
                                for (double g[]: casillasProhibidas) {
                                    if (g[0] == casillaDireccionObjetivo[0] 
                                            && g[1] == casillaDireccionObjetivo[1]) {
                                        encontrada = true;
                                        break;
                                    }
                                        
                                }
                                
                            int alturaEnfrente = mapearAlturaSegunAngulo(compass, lidar);

                            // Si enfrente es mas alto que dron hay que subir
                            if (alturaEnfrente < 0 || encontrada) {

                                // ------------------------------------------------------------------- //
                                /* NUEVO AHMED: 
                                    Si estamos a maxima altura de vuelo, 
                                    entonces no podemos avanzar a la casilla de
                                    enfrente, tenemos que comenzar a evitar 
                                    casillas. Si giramos a la derecha, evitamos
                                    lo que se nos queda a la izquierda y vicecersa. 
                                    Cualquier sentido de giro es valido.
                                */
                                if (miAltura == maxFlight || encontrada) {
                                    nextAction = "RIGHT";
                                    evitandoIzquierda = true;
                                    
                                    casillasProhibidas.add(miCasilla);
                                    
                                    // casillasProhibidas.add(myDashboard.getGPS());
                                    
                                } else {
                                    nextAction = "UP";    
                                }
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
                }
            } else {
                Alert("TieFighter sin vida, fin del juego");
            }
        }
        
        return nextAction;
    }
    
    // Metodo privado que devuelve la altura de la casilla que se encuentre
    // en la direccion que apunte el compass (sobre el lidar pasado)
    private int mapearAlturaSegunAngulo (final int angulo, final int lidar [][]) {
        // Hallar altura casilla de enfrente
        int alturaBuscada = -1;
//        switch(angulo){
//            case 0:     alturaBuscada = lidar[5][6]; break;
//            case 45:    alturaBuscada = lidar[4][6]; break;
//            case 90:    alturaBuscada = lidar[4][5]; break;
//            case 135:   alturaBuscada = lidar[4][4]; break;
//            case 180:   alturaBuscada = lidar[5][4]; break;
//            case 225:   alturaBuscada = lidar[6][4]; break;
//            case 270:   alturaBuscada = lidar[6][5]; break;
//            case 315:   alturaBuscada = lidar[6][6]; break;
//            
//            default: Alert("Angulo no reconocido " + angulo); break;
//        }
//        
//        return alturaBuscada;
        
        // Mapea por rangos, mas ineficiente que Switch
        // pero mas flexible y adaptativo
        if (angulo >= 0 && angulo < 45) {
            alturaBuscada = lidar[5][6];
        } else if (angulo >= 45 && angulo < 90) {
            alturaBuscada = lidar[4][6];
        } else if (angulo >= 90 && angulo < 135) {
            alturaBuscada = lidar[4][5];
        } else if (angulo >= 135 && angulo < 180) {
            alturaBuscada = lidar[4][4]; 
        } else if (angulo >= 180 && angulo < 225) {
            alturaBuscada = lidar[5][4];
        } else if (angulo >= 225 && angulo < 270) {
            alturaBuscada = lidar[6][4];
        } else if (angulo >= 270 && angulo < 315) {
            alturaBuscada = lidar[6][5];
        } else if (angulo >= 315 && angulo < 360) {
            alturaBuscada = lidar[6][6];
        } else {
            Alert("Angulo no reconocido " + angulo); 
            alturaBuscada = -1;
        }
        
        return alturaBuscada;
    }
    
    // Metodo que devuelve las coordenadas de la casilla que se este apuntando
    // con el angulo desde la casilla actual indicada por gps[]
    private double [] getCasillaDireccionObjetivo(double angulo, final double gps[]) {
        double casillaCalculada [] = gps;
        
          if (angulo >= 0 && angulo < 45) {
            casillaCalculada[1]++;
//            alturaBuscada = lidar[5][6];
        } else if (angulo >= 45 && angulo < 90) {
            casillaCalculada[0]--;
            casillaCalculada[1]++;
            
//            alturaBuscada = lidar[4][6];
        } else if (angulo >= 90 && angulo < 135) {
            casillaCalculada[0]--;
            
//            alturaBuscada = lidar[4][5];
        } else if (angulo >= 135 && angulo < 180) {
            casillaCalculada[0]--;
            casillaCalculada[1]--;
//            alturaBuscada = lidar[4][4]; 
        } else if (angulo >= 180 && angulo < 225) {
            casillaCalculada[1]--;
//            alturaBuscada = lidar[5][4];
        } else if (angulo >= 225 && angulo < 270) {
            casillaCalculada[0]++;
            casillaCalculada[1]--;
            
//            alturaBuscada = lidar[6][4];
        } else if (angulo >= 270 && angulo < 315) {
            casillaCalculada[0]++;
//            alturaBuscada = lidar[6][5];
        } else if (angulo >= 315 && angulo < 360) {
            casillaCalculada[0]++;
            casillaCalculada[1]++;
            
//            alturaBuscada = lidar[6][6];
        } else {
            Alert("Angulo no reconocido " + angulo); 
            casillaCalculada[0] = casillaCalculada[1] = -1;
        }
          
        return casillaCalculada;
    }
    
    
    // Metodo importante, resuelve el problema que se abra, en este caso
    // el de Dagobah
    public Status MySolveProblem() {
        
        // ------------------------------------------------------------------ //
        // Obtener informacion de sensores desde LARVA
        boolean lecturaCorrecta = myReadSensors();
        boolean ejecucionCorrecta = false;
        
        // ------------------------------------------------------------------ //
        // Tomar una decision
        if (lecturaCorrecta) {
            String nextAction = myTakeDecision();
            
            // -------------------------------------------------------------- //
            // Realizar la ejecucion de la accion
            ejecucionCorrecta = myExecuteAction(nextAction);            
        }  
        
        
        // ------------------------------------------------------------------ //
        // Actualizar estado de la simulacion
        
        // Si algo fallo se cierra el problema, en otro caso se sigue
        if (!ejecucionCorrecta || !lecturaCorrecta) {
            return Status.CLOSEPROBLEM;
        } else {
            
            // Si tenemos algun objetivo capturado, cerramos el problema
            if (myDashboard.getPayload()> 0) {
                Info("Objetivo capturado correctamente, cerrando problema");
                return Status.CLOSEPROBLEM;
            }
            else { 
                // En otro caso seguimos resolviendolo
                return Status.SOLVEPROBLEM;
            }
        }
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
