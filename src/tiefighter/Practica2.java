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
    // P2-avanzados: Felucia Hoth Mandalore Tatooine Wobani
    
    String service = "PManager", problem = "Felucia",
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
    
    // Umbral a partir del cual se girara para 
    // orientarse desde el compass al angular
    private final int umbralGiro = gradoTotal/8;
    
    // indica que estamos evitando un obstaculo
    private Boolean evitando = false;
    

    
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
//        this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        
//        this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
//        doActivateLARVADash();
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
                        final double [] miCasilla = this.myDashboard.getGPS();
                        double miAltura = myDashboard.getGPS()[2];

                        
                        double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;
                        if( distanciaAngulo >= umbralGiro && !evitando) {
                            
//                            Alert("ME ORIENTO HACIA ANGULAR, DISTANCIA: "+ distanciaAngulo);
                            
                            // Elegir distancia de giro minimo
                            if ( distanciaAngulo < gradoTotal/2 ) {
                                 nextAction = "LEFT";
                            }
                            else {
                                 nextAction = "RIGHT";
                            }
                        } else {
//                            Alert("O ESTOY APUNTANDO O ESTOY EVITANDO");
                            
                            // Creacion de variables para evitar repetir llamadas 
                            // a funciones y mejorar asi la eficiencia
                            int alturaEnfrente = mapearAlturaSegunAngulo(compass, lidar);
                            double [] casillaEnfrente = getCasillaSegunAngulo(compass, this.myDashboard.getGPS());
                            boolean casillaEnfrenteProhibida = 
                                    estaCasillaProhibida(casillaEnfrente, "COMPASS");

                            // Si enfrente es mas alto que dron hay que subir
                            if (alturaEnfrente < 0 || casillaEnfrenteProhibida){
//                                Alert("CASILLA ENFRENTE ESTA PROHIBIDA O ES MAS ALTA QUE YO");
                                if(casillaEnfrenteProhibida){
                                    nextAction = "LEFT";
                                    evitando = true;
                                    
                                }else if (miAltura == maxFlight) {
                                    nextAction = "LEFT";
                                    evitando = true;
                                                                        
                                    if(!estaCasillaProhibida(myDashboard.getGPS(), "MAXFLIGHT")){
                                        casillasProhibidas.add(myDashboard.getGPS());
                                    }
                                } else {
                                    nextAction = "UP";
//                                    evitando = false;
                                }
                            } else {
                                // ANOTACION:
                                // Si estamos aqui es porque en el if anterior 
                                // se ha demostrado que lo que hay enfrente nuestra
                                // es una casilla valida a la que podemos ir
                                nextAction = "MOVE";
//                                Alert("LA CASILLA DE ENFRENTE NO ES MAS ALTA NI ESTA PROHIBIDA");
                                
                                // PSEUDOCODIGO: if evitando then nextAction : "move"
                                if (evitando) {
                                        double objetivo = -1;
                                        
                                        
                                        final int incremento = 45;
                                        int angulo = 0;
                                        boolean esAccesible = false;
                                        
                                        double casilla[];
                                        double alturaLidar;
                                        
                                        // Buscar una casilla accesible y lo mas cercana 
                                        // al angulo que nos indica el angular
                                        while (angulo < gradoTotal && !esAccesible) {
                                            objetivo = (angular + angulo) % gradoTotal;
                                            
                                            casilla = getCasillaSegunAngulo( objetivo, myDashboard.getGPS());
//                                            Alert("grado objetivo: " + objetivo + " casilla actual: " + casilla[0] + ":" + casilla[1]);
                                            alturaLidar = mapearAlturaSegunAngulo(objetivo, lidar);
                                            esAccesible =
                                                    (!estaCasillaProhibida(casilla, "BUCLE") && 
                                                    casilla[2] <= maxFlight && 
                                                    alturaLidar >= 0);
                                            
                                            angulo += incremento;
                                            
                                            Info("\n\nSigo en bucle buscando un angulo de giro interesante");
                                        }
                                        
                                        if (esAccesible) {
//                                            evitando = false; // DESCOMENTAR PARA TENER VIEJA VERSION

                                            if (objetivo == angular) {
                                                evitando = false;
                                                
                                                infoCasillasProhibidas();
//                                                Alert("COINCIDEN OBJ Y ANG FALSE EVITAR " + casillasProhibidas.size());

                                            } else {
//                                                Alert("CASILLA ANIADIDA POR NO PODER IR A ANGULAR");    
                                                if(!estaCasillaProhibida(myDashboard.getGPS() , " ")){
                                                    casillasProhibidas.add(myDashboard.getGPS());
                                                }   
                                            }
                                            
                                            if (distanciaEntreAngulos(objetivo, compass) >= umbralGiro) {
                                                nextAction = orientarnosHaciaAngulo(objetivo, compass);
                                            } else {
                                                nextAction = "MOVE";
                                            }


    //                                            Alert("ACCESIBLE--> ACCION ES: " + nextAction);
    
                                                                                            //                                            Alert("OBJETIVO ES: " + objetivo + " ANGULO ES: " + angulo);


                                        }
                                        
                                        
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
        
        Info("\n\n\n\n");
        if (evitando) Info("evitando TRUE");
        else Info("evitando FALSE");
        Info("\n\n\n\n");
        
        return nextAction;
    }
    
    
    // Devuelve la distancia entre dos angulos
    private double distanciaEntreAngulos(final double a1, final double a2) {
//        return (angular - compass + gradoTotal) % gradoTotal;
        return (a1 - a2 + gradoTotal) % gradoTotal;
    }
    
    // Devuelve el sentido de giro que conlleve menos acciones
    private String orientarnosHaciaAngulo(final double destino, final double origen) {
        double dist = distanciaEntreAngulos(destino, origen);
        
        if ( dist < gradoTotal/2 ) return "LEFT";
        else                       return "RIGHT";
    }
    // Devuelve true si la posicion pasada esta en 
    // el vector de prohibidas false en otro caso
    private Boolean estaCasillaProhibida(double[] posicion, String msg){
//        Info("\t Casilla enfrente: X" + posicion[0] + " Y: " + posicion[1]);
        
        // Se ha limitado tamanio maximo de la 
        // busqueda 5 casillas, se revisan las 5 finales
//        final int tamMaximoCasillas = 15;
//        int inicio = casillasProhibidas.size() - tamMaximoCasillas;
//        if (inicio < 0) inicio = 0;
        
        for(int i = casillasProhibidas.size() - 1; i >=0; i--){
//            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
            if(casillasProhibidas.get(i)[0] == posicion[0] && casillasProhibidas.get(i)[1] == posicion[1]){
//                Alert(msg + ": HEMOS ENCONTRADO CASILLA PROHIBIDA X:" + posicion[0] + " Y: " + posicion[1]);
                return true;
            }
        }
        return false;
    }
    
    private void infoCasillasProhibidas() {
         for(int i = casillasProhibidas.size() - 1; i >=0; i--){
            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
         }
    }
    
    // Antiguo metodo getCasillaSegunAngulo, renombrado, hace lo mismo
    private double[] getCasillaSegunAngulo(double angulo, double[] gps){
        double[] casillaFinal = gps;
        
        if (angulo >= 0 && angulo < 45) {
            casillaFinal[0]++;
        } else if (angulo >= 45 && angulo < 90) {
            casillaFinal[1]--;
            casillaFinal[0]++;
        } else if (angulo >= 90 && angulo < 135) {
            casillaFinal[1]--;
        } else if (angulo >= 135 && angulo < 180) {
            casillaFinal[1]--;
            casillaFinal[0]--;
        } else if (angulo >= 180 && angulo < 225) {
            casillaFinal[0]--;
        } else if (angulo >= 225 && angulo < 270) {
            casillaFinal[1]++;
            casillaFinal[0]--;
        } else if (angulo >= 270 && angulo < 315) {
            casillaFinal[1]++;
        } else if (angulo >= 315 && angulo < 360) {
            casillaFinal[1]++;
            casillaFinal[0]++;
        } else {
            Alert("ERROR: getCasillaSegunAngulo, no se detecto el rango");
        }
            
        
        return casillaFinal;  
    }
    
    // Metodo privado que devuelve la altura de la casilla que se encuentre
    // en la direccion que apunte el compass (sobre el lidar pasado)
    private int mapearAlturaSegunAngulo (final double angulo, final int lidar [][]) {
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
                for (double[] casillasProhibida : casillasProhibidas) {
                    Info("X: " + casillasProhibida[0] + "Y: " + casillasProhibida[1]+ "Z: "+ casillasProhibida[2]);
                }
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