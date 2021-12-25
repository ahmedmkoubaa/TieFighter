package tiefighter;

import agents.LARVAFirstAgent;
import geometry.Point;
import geometry.Vector;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
import swing.LARVADash;


/*
* @author Jaime
*/

public class Practica3Corellian extends LARVAFirstAgent{

    


    enum Status {
        CHECKIN, CHECKOUT, 
        COMISSIONING, JOINSESSION, 
        SOLVEPROBLEM, EXIT, WAIT
    }
    
    
    Status mystatus;
    
    String service = "PManager", problem = "Tatooine",
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
    
    
    private final double porcentajeLimite = 0.15; // TEMPORAL
    private final double porcentajeCercania = 0.8;
    private final int alturaCercania = 20;
    
    // Atributos en los que se almacenaran los valores
    // correspondientes a umbrales de recarga
    private final double maxEnergy = 3500;
    private double umbralLimiteRecarga = porcentajeLimite * maxEnergy;
    private double umbralCercaniaRecarga; 
    
    // indica que estamos evitando lo que se encuentre a nuestra izquierda
    private Boolean evitandoIzquierda = false;
    
    // indica que se esta evitando lo que se encuentra a nuestra derecha
    private Boolean evitandoDerecha = true;
    
    private ArrayList<double[]> casillasProhibidas = new ArrayList<>();
    
    private ArrayList<String> acciones = new ArrayList<>();
    
    /*
    *@author Ahmed
    */
    // Emular sensor de energy, se actualiza
    // en myReadSensors y en myExecuteAction
    private double myEnergy = maxEnergy;
    private final int costeAccion = 10;
    private final int costeSensor = 1;
    
    // Acciones cuya ejecucion se cobra a costeAccion
    private String [] accionesCobrables =
            new String[] {
                "UP", 
                "DOWN", 
                "LEFT", 
                "RIGHT", 
                "MOVE"
            } ;
    
    /*
    * @author Ahmed
    */
    // Emular GPS
    private double [] myGPS;
    
    /*
    * @author Jaime
    */
    private String password = "106-WING-7";
    private String type = "Corellian";
    
    private int initX;
    private int initY;
    
    private double alturaCorellian = -1;
    
    private int objetivoX;
    private int objetivoY;
    private int objetivoZ;
    private int myAngular;
    
    /*
    * @author Ahmed
    */
    private boolean recargando = false;
    private ArrayList<String> jedisEncontrados = new ArrayList<>();
    
    private int compass = 0;
    private boolean recargaPedida = false;
    private String map;
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
//                "ALIVE", 
//                "ENERGY",
//                "GPS",     
//                "LIDAR",
//                "DISTANCE",
//                "ANGULAR",
            };
    
    boolean step = false;   // TEMPORAL
    
    /*
    * @author Jaime
    */
    @Override
    public void setup() {
        super.setup();
        logger.onOverwrite();
        logger.setLoggerFileName("mylog.json");
//        logger.offEcho();

        //this.enableDeepLARVAMonitoring();
        Info("Setup and configure agent");
        mystatus = Status.CHECKIN;
        exit = false;
        
        //this.myDashboard = new LARVADash(LARVADash.Layout.DASHBOARD, this);
        this.myDashboard = new LARVADash(this);
        //doActivateLARVADash();
    }

    /*
    * @author Jaime
    */
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
            case WAIT:
                mystatus = MyWait();
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

    /*
    * @author Jaime
    */
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
        DFSetMyServices(new String[]{"CORELLIAN " + password});
        return Status.WAIT;
    }
    
    /*
    * @author Jaime
    * @author Ahmed
    */
    public Status MyWait() {
        open = LARVAblockingReceive();
        sessionKey = open.getConversationId();
        map = open.getContent();
        outbox = open.createReply();
        outbox.setPerformative(ACLMessage.AGREE);
        outbox.setConversationId(sessionKey);
        outbox.setInReplyTo("Recruit crew for session " + password);
        outbox.setContent("");
        this.LARVAsend(outbox);
        
        open = this.LARVAblockingReceive();
        initX = Integer.parseInt(open.getContent().split(" ")[0]);
        initY = Integer.parseInt(open.getContent().split(" ")[1]);
        myAngular = 0;
        Info("X: " + initX + ", Y: " + initY);
        
        // Inicializar el GPS
        myGPS = new double [] {
            initX,                                  // X de Spawn
            initY,                                  // Y de Spawn
            myDashboard.getMapLevel(initX, initY)   // Z calculada de Spawn
        };
        
        return Status.COMISSIONING;
    }

    /*
    * @author Jaime
    */
    public Status MyCloseProblem() {
        outbox = open.createReply();
        outbox.setContent("Cancel session " + sessionKey);
        Info("Closing problem Helloworld, session " + sessionKey);
        this.LARVAsend(outbox);
        open = LARVAblockingReceive();
        Info(problemManager + " says: " + open.getContent());
        return Status.CHECKOUT;
    }

    public Status MyCheckout() {
        this.doLARVACheckout();
        return Status.EXIT;
    }
    
    /*
    * @author Jaime
    */
    public Status MyComissioning(){
        String localService = "STORE " + sessionKey;
        
        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service STORE is down");
            return Status.CHECKOUT;
        }
        
        storeManager = this.DFGetAllProvidersOf(localService).get(0);
        Info("Found store manager " + storeManager);
        
        sensorKeys = "";
        for (String s: mySensors) {
            outbox = new ACLMessage();
            outbox.setSender(this.getAID());
            outbox.addReceiver(new AID (storeManager, AID.ISLOCALNAME));
            outbox.setPerformative(ACLMessage.REQUEST);
            outbox.setContent("Request product " + s + " session " + sessionKey);
            
            this.LARVAsend(outbox);
            inbox = this.LARVAblockingReceive();
            Info("Sensor: " + inbox.getContent());
            
            if (inbox.getContent().startsWith("Confirm")) {
                Info("bought sensor " + s) ;
                sensorKeys += inbox.getContent().split(" ")[2] + " ";
            } else {
                this.Alert("Sensor " + s + " could not be obtained");
                return Status.CHECKOUT;
            }
        }
        
        Info("bought all sensor keys " + sensorKeys);
        return Status.JOINSESSION;
    }

    /*
    * @author Jaime
    */    
    public Status MyJoinSession(){
        String localService = "SESSION MANAGER " + sessionKey;
        
        if (this.DFGetAllProvidersOf(localService).isEmpty()) {
            Error("Service SESSION is down");
            return Status.CHECKOUT;
        }
        
        sessionManager = this.DFGetAllProvidersOf(localService).get(0);
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setContent("Request join session " + sessionKey 
                         + " as " + type + " at " + initX + " " + initY +
                         " attach sensors " + sensorKeys);
        
        this.LARVAsend(outbox);
        session = this.LARVAblockingReceive();
        
        String parse[] = session.getContent().split(" ");
        
        if (parse[0].equals("Confirm")) {
            outbox = open.createReply();
            outbox.setPerformative(ACLMessage.INFORM);
            outbox.setConversationId(sessionKey);
            outbox.setInReplyTo("TAKEOFF " + password);
            outbox.setContent(initX + " " + initY);
            this.LARVAsend(outbox);
            return Status.SOLVEPROBLEM;
        } else {
            Alert("Error: joining session: " + session.getContent());
            return Status.CHECKOUT;
        }
    }
    
    
    // Metodo importante, resuelve el problema que se abra, en este caso
    // el de Dagobah
    /*
    * @author Jaime
    * @author Ahmed
    */  
    public Status MySolveProblem() {
        
        Info("Esperando peticion");
        open = this.LARVAblockingReceive();
        
        switch (open.getPerformative()) {
            case ACLMessage.REQUEST:
                int nextX, nextY, nextZ;
                
                String [] content = open.getContent().split(" ");
                
                objetivoX = Integer.parseInt(content[1]);
                objetivoY = Integer.parseInt(content[2]);
                objetivoZ = Integer.parseInt(content[3]);
                
                // La primera vez que obtengamos Z nos la guardamos
                if (alturaCorellian < 0) alturaCorellian = objetivoZ; 
                
                
                Info("X: " + objetivoX + ", Y: " + objetivoY + ", Z: " + objetivoZ);
                boolean lecturaCorrecta = myReadSensors();
               
                if(myEnergy == 0){
                    Error("CORELLIAN SIN VIDA SE MURIO");
                    return Status.CHECKOUT;
                }   
                
                String nextAction;
                
                if (content[0].toUpperCase().equals("MOVE")){
                    // Si toca moverse confirmamos que nos vamos a mover
                    outbox = open.createReply();
                    
                    outbox.setPerformative(ACLMessage.AGREE);
                    
                    outbox.setConversationId(sessionKey);
                    outbox.setInReplyTo("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
                    outbox.setContent("");
                    
                    this.LARVAsend(outbox);
                } else {
//                    Alert("ME MANDAN CAPTURAR");
                }
                
                // Hasta que no barra todo el mapa
                while(!(myGPS[0] == objetivoX &&   // X
                        myGPS[1] == objetivoY )){   // y
                    
                    // Modo de actuar del agente
                    nextAction = myTakeDecision2();                    
                    
                    // Ejecuto la accion
                    if (!myExecuteAction(nextAction)) {
                        Error("FALLO EN EL EXECUTE ACTION");
                        return Status.CHECKOUT;
                        
                    }
                    
                    // Observo el entorno y repito
                    if (!myReadSensors()) {
                        Error("FALLO EN EL READ SENSORS");
                        return Status.CHECKOUT;
                        
                    }
                    
                    Info("\n\n\n\n\n\n");
                    Info("MY ENERGY ES: " + myEnergy);
                    Info("LIMITE ES: " +  umbralLimiteRecarga);
                    Info("\n\n\n\n\n\n");
                    Info("GPS: " + 
                            myGPS[0] + " " + 
                            myGPS[1] + " " + 
                            myGPS[2]
                    );
                    
                    Info("myGPS: " + 
                            myGPS[0] + " " + 
                            myGPS[1] + " " + 
                            myGPS[2]
                    );
                    
                    Info("\n\n\n\n\n\n");
                }
                
                // Info("POSICION FINAL: X: " + myGPS[0] + ", Y: " + myGPS[1] + ", Z: " + myGPS[2]);
                
                
                // Informar que nos hemos movido adecuadamente
                if (content[0].toUpperCase().equals("MOVE")){
                    // Crear respuesta
                    outbox = open.createReply();
                    
                    // Indicar performativas e id
                    outbox.setPerformative(ACLMessage.INFORM);
                    outbox.setConversationId(sessionKey);
                    
                    // Contenido
                    outbox.setInReplyTo("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
                    outbox.setContent("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
                    
                    // Enviar
                    this.LARVAsend(outbox);
                    
                } else if (content[0].toUpperCase().equals("CAPTURE")){
                    
                    // Si toca capturar hacemos lo que se debe hacer
                    while(!estaSobreElSuelo()){
                        nextAction = "DOWN";
                        myExecuteAction(nextAction);            // Ejecuto la accion
                        myReadSensors();
                    }  
                    
                    nextAction = "CAPTURE";
                
                    boolean capture = myExecuteAction(nextAction);
                    outbox = open.createReply();

//                    Alert("CAPTURANDO");
                    if(capture){
                        outbox.setPerformative(ACLMessage.INFORM);
                    }else{
                        outbox.setPerformative(ACLMessage.FAILURE);
                    }   
                    
                    // Aniadir detalles al mensaje
                    outbox.setConversationId(sessionKey);
                    outbox.setInReplyTo("CAPTURE " + objetivoX + " " + objetivoY + " " + objetivoZ);
                    outbox.setContent("CAPTURE " + objetivoX + " " + objetivoY + " " + objetivoZ);
                    
                    this.LARVAsend(outbox);
                } else {
                    Error("NO SE RECONOCIO EL MENSAJE");
                }
                
                return Status.SOLVEPROBLEM;
                
            case ACLMessage.CANCEL:
                return Status.CHECKOUT;
                
            default:
                Error("MENSAJE NO RECONOCIDO O ESPERADO: " + open);
                return Status.SOLVEPROBLEM;
        }
    }
    
    
    /*
    * @author Jaime
    */  
    private void updatePosition(String action){
        switch(action){
            case "MOVE":
                switch(myAngular){
                    case 0:
                        objetivoX+=1;
                        break;
                    case 90:
                        objetivoY-=1;
                        break;
                    case 180:
                        objetivoX-=1;
                        break;
                    case 270:
                        objetivoY+=1;
                        break;
                        
                }
                break;
            case "LEFT":
                myAngular+=45;
                myAngular%=360;
                break;
            case "RIGHT":
                myAngular-=45;
                myAngular%=360;
                break;
            case "UP":
                objetivoZ=+5;
                break;
            case "DOWN":
                objetivoZ-=5;
                break;
        }
    }
    
    /*
    * @author Ahmed
    */
    // Devuelve true si el agente esta posado justamente sobre el suelo
    // false en caso contrario. Se supone que si la altura de la casilla
    // en la que esta el agente y la altura del agente son la misma
    // entonces esta posado sobre al suelo (a nivel del suelo)
    private boolean estaSobreElSuelo() {
        return (
            myGPS[2] ==                 // Altura en Z
            myDashboard.getMapLevel(                   // Map level
                (int) myGPS[0],     // Coordenada X
                (int) myGPS[1]      // Coordenada Y
            ));
    }
    
    /*
    *@author Ahmed
    */
    // Pide recarga al destroyer y espera la respuesta
    // actualiza ademas el estado pertinente
    private void pedirRecarga(){
        //------------------------------------------------
        // Crear respuesta
        outbox = open.createReply();
        
        // Indicar performativas e id
        outbox.setPerformative(ACLMessage.QUERY_IF);
        outbox.setConversationId(sessionKey);
            
        // Contenido
        outbox.setInReplyTo("MOVE " + objetivoX + " " + objetivoY + " " + objetivoZ);
        outbox.setContent("RECHARGE");
        outbox.setOntology("COMMITMENT");
        recargaPedida = true;
                    
        // Enviar
        this.LARVAsend(outbox);
        
        
        // Esperar a recibir respuesta 
        open = this.LARVAblockingReceive();
        if (open.getPerformative() == ACLMessage.CONFIRM) {
            recargando = true;
            
        } else {
            recargando = false;
            Alert("NOS HAN DENEGADO LA RECARGA, LISTOS PARA MORIR");
        }
    }
    
    /*
    * @author Ahmed
    */
    // Metodo que devuelve el angulo del agente 
    // con respecto a un punto P pasado. Es geometria
    // pura y dura adaptada a Larva
    private double myGetAngular(Point p) {
        double [] getGPS = myGPS;
        
        Vector Norte = new Vector(new Point(0, 0), new Point(0, -10));
        Point me = new Point(myGPS[0], myGPS[1], myGPS[2]);
        Vector Busca = new Vector(me, p);
        
        int v = (int) Norte.angleXYTo(Busca);;
        v = 360+90-v;   
        return v%360;  
    }
    
    /*
    * @author Jaime
    * @author Antonio
    * @author Ahmed
    */  
    private String myTakeDecision2(){
        String nextAction = "";
        Point p = new Point(objetivoX,objetivoY);
        
        double alturaActual = myGPS[2];
        double [] gps = new double [] {
            myGPS[0], myGPS[1], myGPS[2]
        };
        
        if (!recargaPedida && myEnergy < umbralLimiteRecarga) {
            pedirRecarga();
        }
        
        // Si estamos recargando
        if (recargando) {
            
            Info("\n\n\n\n");
            Info("ESTAMOS YENDO A RECARGAR");
            Info("\n\n\n\n");
            
            // Bajamos al suelo para ejecutar la accion de recarga
//            if (myDashboard.getLidar()[5][5] > 0) {

            
            // Si no estamos sobre el suelo bajamos, 
            // en otro caso recargamos y reseteamos estado
            if (!estaSobreElSuelo()) {
                nextAction = "DOWN";
                
            } else {
                nextAction = "RECHARGE";
                recargando = false;
                recargaPedida = false;
            }
        } else {
            
            // si no estamos recargando, seguimos yendo al objetivo

        
            // Si estoy a una altura elevada sigo
            // Si no estoy sobre el objetivo me desplazo
            if ( objetivoX != myGPS[0] || objetivoY != myGPS[1]){

//                final double angular = this.myDashboard.getAngular(p);
                final double angular = myGetAngular(p);

                double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;

                if( distanciaAngulo >= 45) {

                    // Elegir distancia de giro minimo
                    if ( distanciaAngulo < gradoTotal/2 ) {
                        nextAction = "LEFT";
                        compass = (compass + 45 + gradoTotal) % gradoTotal;
                    }
                    else {
                        nextAction = "RIGHT";
                        compass = (compass - 45 + gradoTotal) % gradoTotal;
                    }
                } 
                else {
                    // Nos movemos pues estamos alineados con el objetivo
                    nextAction = "MOVE";
                    
                    // Comprobamos si la casilla a la que nos vamos a mover
                    // es mas alta que nosotros (requiere mayor altura)
                    
                    double [] res = casillaEnfrente(compass, gps);
                    int alturaEnFrente = myDashboard.getMapLevel((int)res[0], (int)res[1]);
                    
                    Info("\n\n\n\n\n\n");
                    Info(
                            "MI GPS: " + gps[0] + " " + gps[1] + " " + gps[2]  + " " + 
                            "GPS RES: " + res[0] + " " + res[1] + " " + res[2]  + " " + 
                            "COMPASS: " + compass + " " + 
                            "ALTURA ENFRENTE: " + alturaEnFrente + " " + 
                            "MI ALTURA ACTUAL: " + alturaActual
                    );
                    Info("\n\n\n\n\n\n");


                    // Si nuestra altura es menor que la de enfrente, entonces tengo que subir
                    if (alturaActual < alturaEnFrente) {
                        nextAction = "UP";
                        Info("TENGO QUE SUBIR");
                    }
                }
            } else {
                nextAction = "DOWN";
            }
        }
        
        return nextAction;
    }

    
    private String myTakeDecision() {
        String nextAction = "";
        
        if (false) {
//            maxEnergy = myDashboard.getEnergy() + myDashboard.getEnergyBurnt();   
            umbralLimiteRecarga = porcentajeLimite * maxEnergy;
            umbralCercaniaRecarga = porcentajeCercania * maxEnergy;
            nextAction = "RECHARGE";
        }
        else {
            // Si el dron sigue vivo y tiene energia
            Info("Alive: " + myDashboard.getAlive() + ", Energy: " + myDashboard.getEnergy());
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
                        double miAltura = myGPS[2];

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
                            
                            int alturaEnfrente = mapearAlturaSegunAngulo(compass, lidar);

                            // Si enfrente es mas alto que dron hay que subir
                            if (alturaEnfrente < 0 || estaCasillaProhibida(casillaEnfrente(compass, myGPS))){

                                // ------------------------------------------------------------------- //
                                /* NUEVO AHMED: 
                                    Si estamos a maxima altura de vuelo, 
                                    entonces no podemos avanzar a la casilla de
                                    enfrente, tenemos que comenzar a evitar 
                                    casillas. Si giramos a la derecha, evitamos
                                    lo que se nos queda a la izquierda y vicecersa. 
                                    Cualquier sentido de giro es valido.
                                */
                                if(estaCasillaProhibida(casillaEnfrente(compass, myGPS))){
                                    nextAction = "LEFT";
                                    evitandoDerecha = true;
                                }else if (miAltura == maxFlight) {
                                    nextAction = "LEFT";
                                    evitandoDerecha = true;
                                    if(!estaCasillaProhibida(myGPS)){
                                        casillasProhibidas.add(myGPS);
                                    }
                                } else {
                                    nextAction = "UP";
                                }
                            }else{
                                if (evitandoIzquierda || evitandoDerecha) {
                                    double alturaDireccionAngular = mapearAlturaSegunAngulo((int)angular, lidar);

                                    // si la altura de la casilla en direccion el objetivo es inferior a mi
                                    // entonces anulo esquivar y dejo que el algoritmo vuelva a apuntar hacia alla
                                    if (alturaDireccionAngular < miAltura){
                                      evitandoIzquierda = evitandoDerecha = false;
                                      nextAction = "MOVE";
                                    }else{
                                        if(evitandoIzquierda){
                                            nextAction = "RIGTH";
                                        }else{
                                            nextAction = "LEFT";
                                        }
                                    }
                                } else {
                                    nextAction = "MOVE";
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
    
    private Boolean estaCasillaProhibida(double[] posicion){
        Info("\t Casilla enfrente: X" + posicion[0] + " Y: " + posicion[1]);
        for(int i=0; i<casillasProhibidas.size(); i++){
            Info("\t Casilla prohibida: X" + casillasProhibidas.get(i)[0] + " Y: " + casillasProhibidas.get(i)[1]);
            if(casillasProhibidas.get(i)[0] == posicion[0] && casillasProhibidas.get(i)[1] == posicion[1]){
                return true;
            }
        }
        return false;
    }
    
    private double[] casillaEnfrente(int compass, double[] gps){
        double[] casillaFinal = gps;
        
        if (compass >= 0 && compass < 45) {
            casillaFinal[0]++;
        } else if (compass >= 45 && compass < 90) {
            casillaFinal[1]--;
            casillaFinal[0]++;
        } else if (compass >= 90 && compass < 135) {
            casillaFinal[1]--;
        } else if (compass >= 135 && compass < 180) {
            casillaFinal[1]--;
            casillaFinal[0]--;
        } else if (compass >= 180 && compass < 225) {
            casillaFinal[0]--;
        } else if (compass >= 225 && compass < 270) {
            casillaFinal[1]++;
            casillaFinal[0]--;
        } else if (compass >= 270 && compass < 315) {
            casillaFinal[1]++;
        } else if (compass >= 315 && compass < 360) {
            casillaFinal[1]++;
            casillaFinal[0]++;
        }
        
        return casillaFinal;  
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
    
    
    
    // lee sensores mediante peticiones al sensorManager, si fue lectura 
    // correcta devuelve true, en otro caso devuelve false
    private boolean myReadSensors() {
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.QUERY_REF);
        outbox.setContent("Query sensors session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request query sensors session to " + sessionManager);
        
        inbox = this.LARVAblockingReceive(new MessageTemplate(new MessageTemplate.MatchExpression() {
            @Override
            public boolean match(ACLMessage aclm) {
                return aclm.getSender().equals(new AID(sessionManager, AID.ISLOCALNAME));
            }
        }));
        
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        // Comprobar que la lectura fuese correcta
        if(inbox.getPerformative() == ACLMessage.REFUSE 
                || inbox.getPerformative() == ACLMessage.FAILURE) {
            Info(content);
            return false;
        }
        
        // ACTUALIZAR COSTE SENSORES
        myEnergy -= (mySensors.length * costeSensor);
        
        
        return true;
    }
    
    // Ejecuta una accion enviando un mensaje al session manager
    // Devuelve true en caso de que la accion se ejecutase correctamente
    // false en otro caso diferente
    private boolean myExecuteAction(String accion){
        this.outbox = new ACLMessage();
        
        outbox.setSender(getAID());
        outbox.addReceiver(new AID(sessionManager, AID.ISLOCALNAME));
        outbox.setPerformative(ACLMessage.REQUEST);
        outbox.setContent("Request execute " + accion + " session " + sessionKey);
        
        this.LARVAsend(outbox);
        Info("Request executing action " + accion + " to " + sessionManager);
        
        inbox = LARVAblockingReceive();
        Info(sessionManager + " says: " + inbox.getContent());
        content = inbox.getContent();
        
        if (inbox.getPerformative() == ACLMessage.INFORM) {
            
            // Actualizar sensor de energia emulado
            actualizarSensorEnergy(accion);
            
            
            // Actualizar GPS emulado
            actualizarSensorGPS(accion);
            
            return true;
        } else {
            Info("MyExecuteAction: " + content);
            return false;
        }        
    }
    
    
    /*
    * @author Ahmed
    */
    // Actualizar sensor de energia para tenerlo emulado
    private void actualizarSensorEnergy(String accion){
        
        // ACTUALIZAR ENERGIA COMO ES DEBIDO
        if (accion.equals("RECHARGE")) {
            myEnergy = maxEnergy;
            Info("ENERGIA RECARGADA COMPLETAMENTE");
        }
        else {
                
            // En otro caso, tenemos que identificar cual fue 
            // la accion que se llevo a cabo y si es necesario
            // actualizar la energia o no

            // Busca y actualiza
            for (String s: accionesCobrables) {
                if (s.equals(accion)) {
                    myEnergy -= costeAccion;
                    break;
                }
            }
        }
    }
    
    /*
    * @author Ahmed
    */
    // Actualiza sensor GPS en base a accion para emularlo
    private void actualizarSensorGPS(String accion) {
        if (accion.equals("MOVE")) {
            double [] res = casillaEnfrente(compass, myGPS);
            myGPS = res;
                    
        } else if (accion.equals("UP")) {
            myGPS[2] += 5;
            
        } else if (accion.equals("DOWN")) {
            myGPS[2] -= 5;
        } else {
            Info("NADA QUE ACTUALIZAR: " + accion);
        }
    }
}
