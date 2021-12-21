package tiefighter;

import agents.LARVAFirstAgent;
import geometry.Point;
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
    
    /*
    * @author Jaime
    */
    private String pass = "106-WING";
    private String type = "Corellian";
    
    private int initX;
    private int initY;
    
    private double alturaCorellian;
    
    private int myX;
    private int myY;
    private int myZ;
    private int myAngular;
    
    private ArrayList<String> jedisEncontrados = new ArrayList<>();
    
    private int compass = 0;
    
    private String map;
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
                "ALIVE",  
                "GPS",     
                "LIDAR",
//                "DISTANCE",
                "ANGULAR",
            };
    boolean step = true;
    
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
        DFSetMyServices(new String[]{"CORELLIAN " + pass});
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
        outbox.setInReplyTo("Recruit crew for session " + pass);
        outbox.setContent("");
        this.LARVAsend(outbox);
        
        open = this.LARVAblockingReceive();
        initX = Integer.parseInt(open.getContent().split(" ")[0]);
        initY = Integer.parseInt(open.getContent().split(" ")[1]);
        myAngular = 0;
        Info("X: " + initX + ", Y: " + initY);
        
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
            outbox.setInReplyTo("TAKEOFF " + pass);
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
                
                myX = Integer.parseInt(content[1]);
                myY = Integer.parseInt(content[2]);
                myZ = Integer.parseInt(content[3]);
                
                
                Info("X: " + myX + ", Y: " + myY + ", Z: " + myZ);
                boolean lecturaCorrecta = myReadSensors();
               
                if(myDashboard.getAlive() == false){
                    Error("CORELLIAN SIN VIDA SE MURIO");
                    return Status.CHECKOUT;
                }   String nextAction;
                
                if (content[0].toUpperCase().equals("MOVE")){
                    // Si toca moverse confirmamos que nos vamos a mover
                    outbox = open.createReply();
                    outbox.setPerformative(ACLMessage.AGREE);
                    outbox.setConversationId(sessionKey);
                    outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
                    outbox.setContent("");
                    this.LARVAsend(outbox);
                } 
                
                
                int cont = 0;
                // Hasta que no barra todo el mapa
                while(!(myDashboard.getGPS()[0] == myX && myDashboard.getGPS()[1] == myY) && cont < 50){
                    
                    // Modo de actuar del agente
                    nextAction = myTakeDecision2();  // Tomo una decision
                    myExecuteAction(nextAction);     // Ejecuto la accion
                    myReadSensors();                 // Observo el entorno y repito
                    
                    Info("X: " + myDashboard.getGPS()[0] + ", Y: " + myDashboard.getGPS()[1] + ", Z: " + myDashboard.getGPS()[2]);
                    Info("Accion: " + nextAction);
                    cont++;
                    
                }   Info("POSICION FINAL: X: " + myDashboard.getGPS()[0] + ", Y: " + myDashboard.getGPS()[1] + ", Z: " + myDashboard.getGPS()[2]);
                
                // Informar que nos hemos movido adecuadamente
                if (content[0].toUpperCase().equals("MOVE")){
                    // Si toca moverse nos movemos a donde se nos indique
                    outbox = open.createReply();

                    
                    outbox.setPerformative(ACLMessage.INFORM);
                    outbox.setConversationId(sessionKey);

                    outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
                    outbox.setContent("MOVE" + myX + " " + myY + " " + myZ);
                    this.LARVAsend(outbox);
                    
                    
                } else if (content[0].toUpperCase().equals("CAPTURE")){
                    // Si toca capturar hacemos lo que se debe hacer
                    while(myDashboard.getLidar()[5][5] > 0){
                        nextAction = "DOWN";
                        myExecuteAction(nextAction);            // Ejecuto la accion
                        myReadSensors();
                    }  
                    
                    nextAction = "CAPTURE";
                
                    boolean capture = myExecuteAction(nextAction);
                    outbox = open.createReply();

                    if(capture){
                        outbox.setPerformative(ACLMessage.INFORM);
                    }else{
                        outbox.setPerformative(ACLMessage.FAILURE);
                    }   outbox.setConversationId(sessionKey);

                    outbox.setInReplyTo("CAPTURE " + myX + " " + myY + " " + myZ);
                    outbox.setContent("CAPTURE " + myX + " " + myY + " " + myZ);
                    this.LARVAsend(outbox);
                } else {
                    
                }
                
                
                
                return Status.SOLVEPROBLEM;
                
            case ACLMessage.CANCEL:
                return Status.CHECKOUT;
                
            default:
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
                        myX+=1;
                        break;
                    case 90:
                        myY-=1;
                        break;
                    case 180:
                        myX-=1;
                        break;
                    case 270:
                        myY+=1;
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
                myZ=+5;
                break;
            case "DOWN":
                myZ-=5;
                break;
        }
    }
    
    
    /*
    * @author Jaime
    * @author Antonio
    * @author Ahmed
    */  
    private String myTakeDecision2(){
        String nextAction = "";
        Point p = new Point(myX,myY);
        
        alturaCorellian = myZ; //aqui recibe la que le diga el destroyer
        double alturaActual = myDashboard.getGPS()[2];
      
        if(alturaActual == alturaCorellian){
            final double angular = this.myDashboard.getAngular(p);
            double miAltura = myDashboard.getGPS()[2];

            double distanciaAngulo = (angular - compass + gradoTotal) % gradoTotal;
    //        Info("\n\n\nDistanciaAngulo: " + distanciaAngulo);
    //        Info("\n\n\n");
    //        Info("\n\n\nAngular: " + angular);
    //        Info("\n\n\n");
    //        Info("\n\n\nCompass: " + compass);
    //        Info("\n\n\n");
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
            }else{
                nextAction = "MOVE";
            }

            
        }
        else {
            // Si la altura nos coincide, calculamos si subir o bajar
            if(alturaActual < alturaCorellian){
            nextAction = "UP";
            }
            else if(alturaActual > alturaCorellian){
                nextAction = "DOWN";
            }
        }
        
        return nextAction;
    }

    
    private String myTakeDecision() {
        String nextAction = "";
        
        if (false) {
            maxEnergy = myDashboard.getEnergy() + myDashboard.getEnergyBurnt();   
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
                        double miAltura = myDashboard.getGPS()[2];

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
                            if (alturaEnfrente < 0 || estaCasillaProhibida(casillaEnfrente(compass, myDashboard.getGPS()))){

                                // ------------------------------------------------------------------- //
                                /* NUEVO AHMED: 
                                    Si estamos a maxima altura de vuelo, 
                                    entonces no podemos avanzar a la casilla de
                                    enfrente, tenemos que comenzar a evitar 
                                    casillas. Si giramos a la derecha, evitamos
                                    lo que se nos queda a la izquierda y vicecersa. 
                                    Cualquier sentido de giro es valido.
                                */
                                if(estaCasillaProhibida(casillaEnfrente(compass, myDashboard.getGPS()))){
                                    nextAction = "LEFT";
                                    evitandoDerecha = true;
                                }else if (miAltura == maxFlight) {
                                    nextAction = "LEFT";
                                    evitandoDerecha = true;
                                    if(!estaCasillaProhibida(myDashboard.getGPS())){
                                        casillasProhibidas.add(myDashboard.getGPS());
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
            return true;
        } else {
            Info("MyExecuteAction: " + content);
            return false;
        }        
    }
}
