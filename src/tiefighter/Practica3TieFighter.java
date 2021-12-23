package tiefighter;

import agents.LARVAFirstAgent;
import geometry.Point;
import jade.core.AID;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import java.util.ArrayList;
//import swing.LARVACompactDash;
import swing.LARVADash;


/*
* @author Jaime
*/

public class Practica3TieFighter extends LARVAFirstAgent{  //Practica3TieFighter


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
    private String pass = "106-WING-2";
    
    private int initX;
    private int initY;
    
    private int myX;
    private int myY;
    private int myZ;
    private int myAngular;
    
    private ArrayList<String> jedisEncontrados = new ArrayList<>();
    private ArrayList<Point> puntos = new ArrayList<>();
    
    private int compass = 0;
    
    private String map;
    
    private double alturaTie;
    
    int width, height, maxFlight;
    
    ACLMessage open, session;
    String[] contentTokens,
            mySensors = new String[] {
                "ALIVE",
//                "ONTARGET",   // No 
                "GPS",        // No
//                "COMPASS",
                "LIDAR",
//                "ALTITUDE",   // No
//                "VISUAL",     // No
//                "ENERGY",
//                "PAYLOAD",
//                "DISTANCE",
                "ANGULAR",    // No
                "THERMALHQ"
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

        logger.offEcho();
//        this.enableDeepLARVAMonitoring();

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
        DFSetMyServices(new String[]{"FIGHTER " + pass});
        return Status.WAIT;
    }
    
    /*
    * @author Jaime
    */
    public Status MyWait() {
        open = LARVAblockingReceive();
        sessionKey = open.getConversationId();
        map = open.getContent().split(" ")[1];
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
                         + " as " + "Fighter" + " at " + initX + " " + initY +
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
    
    
    
    /*
    * @author Jaime
    * @author Ahmed
    */  
    public Status MySolveProblem() {

        int nextX, nextY, nextZ;
        // Recibiendo posicion a la que ir (MOVE X Y Z)
        open = this.LARVAblockingReceive();

        if (open.getPerformative() == ACLMessage.REQUEST) {
            myX = Integer.parseInt(open.getContent().split(" ")[1]);
            myY = Integer.parseInt(open.getContent().split(" ")[2]);
            myZ = Integer.parseInt(open.getContent().split(" ")[3]);
            Info("X: " + myX + ", Y: " + myY + ", Z: " + myZ);

//            Alert("vamos a: " + myX + " " + myY + " " + myZ);
            outbox = open.createReply();
            outbox.setPerformative(ACLMessage.AGREE);
            outbox.setConversationId(sessionKey);
            outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
            outbox.setContent("");
            this.LARVAsend(outbox);

            //boolean lecturaCorrecta = myReadSensors();
            myReadSensors();
            int cont = 0;
               
            String matrix = "";
            boolean jediDetected = false;
            int jediX = -1, jediY = -1;
            int posI, posJ;
            posI = posJ = -1;
            double jediXReal = -1, jediYReal = -1;
            int[][] thermal = myDashboard.getThermal();
            int pi = (int) (myDashboard.getGPS()[0]);
            int pj = (int) (myDashboard.getGPS()[1]);

            if (thermal[pi][pj] == 0) {
                jediDetected = true;
                jediX = pj - 10;
                jediY = pi - 10;

                posI = pi;
                posJ = pj;

                // Sumamos ademas ahora el GPS
                jediXReal = jediX + myDashboard.getGPS()[0];
                jediYReal = jediY + myDashboard.getGPS()[1];
            }

            if (jediDetected) {
                String pos
                        = "X: " + jediXReal + ", Y: " + jediYReal;

                Info("Esta es la posicion encontrada: " + pos);

                boolean aniadido = false;
                for (String s : jedisEncontrados) {
                    if (s.equals(pos)) {
                        aniadido = true;
                    }
                }

                if (!aniadido) {
                    jedisEncontrados.add(pos);
                    /*
                    @author Antonio
                    @author Ahmed
                    Enviamos mensaje al destroyer con los jedi encontrados
                     */

                    //Cambiamos la forma en la que se inserta la pos del jedi,
                    //para utilizar split() y así obtenemos la x e y.
                    outbox = open.createReply();
                    outbox.setPerformative(ACLMessage.INFORM_REF);
                    outbox.setConversationId(sessionKey);
                    outbox.setOntology("COMMITMENT");
                    outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
                    outbox.setContent("FOUND " + (int) jediXReal + " " + (int) jediYReal);
                    this.LARVAsend(outbox);

                }

            }


            // Hasta que no barra todo el mapa
            while (!(myDashboard.getGPS()[0] == myX && myDashboard.getGPS()[1] == myY)) {

                // Modo de actuar del agente
                String nextAction = myTakeDecision2();  // Tomo una decision
//                if(cont == 0){
//                    nextAction = "UP";
//                }

                myExecuteAction(nextAction);            // Ejecuto la accion
                myReadSensors();                        // Observo el entorno y repito

                if (myDashboard.getAlive() == false) {
                    return Status.CHECKOUT;

                }

                Info("X: " + myDashboard.getGPS()[0] + ", Y: " + myDashboard.getGPS()[1] + ", Z: " + myDashboard.getGPS()[2]);
                Info("Accion: " + nextAction);
                cont++;

                // Despues de actualizar los sensores en la posicion actual
                // Miramos a ver el thermal y si tenemos o no un Jedi detectado
                thermal = myDashboard.getThermal();
                matrix = "";
                jediDetected = false;
                jediX = -1;
                jediY = -1;
                jediXReal = -1;
                jediYReal = -1;

                Info("\n\n\n\n");
                Info("el thermal tiene length: " + thermal.length);

                posI = posJ = -1;

                for (int i = 0; i < thermal.length; i++) {
                    for (int j = 0; j < thermal[i].length; j++) {
                        matrix += thermal[i][j] + " ";
                        if (thermal[i][j] == 0) {
                            jediDetected = true;
                            // Para obtener la coordenada real del objetivo que desprendio 0
                            // restamos 10, nuestra coordenada en el thermal. 

                            // Si detectamos al Jedi informamos
                            // ERROR: estamos detectando varias veces el mismo Jedi en la misma posicion
                            jediX = j - 10;
                            jediY = i - 10;

                            posI = i;
                            posJ = j;

                            // Sumamos ademas ahora el GPS
                            jediXReal = jediX + myDashboard.getGPS()[0];
                            jediYReal = jediY + myDashboard.getGPS()[1];
                            break;

                        }
                    }
                    matrix += "\n";
                }

                if (jediDetected) {
                    String pos
                            = "X: " + jediXReal + ", Y: " + jediYReal;

                    Info("Esta es la posicion encontrada: " + pos);

                    boolean aniadido = false;
                    for (String s : jedisEncontrados) {
                        if (s.equals(pos)) {
                            aniadido = true;
                        }
                    }

                    if (!aniadido) {
                        jedisEncontrados.add(pos);
                        /*
                        @author Antonio
                        @author Ahmed
                        Enviamos mensaje al destroyer con los jedi encontrados
                         */

                        //Cambiamos la forma en la que se inserta la pos del jedi,
                        //para utilizar split() y así obtenemos la x e y.
                        outbox = open.createReply();
                        outbox.setPerformative(ACLMessage.INFORM_REF);
                        outbox.setConversationId(sessionKey);
                        outbox.setOntology("COMMITMENT");
                        outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
                        outbox.setContent("FOUND " + (int) jediXReal + " " + (int) jediYReal);
                        this.LARVAsend(outbox);

                    }

                }

            }

            /*
            @author Antonio
            Informamos al destroyer que hemos llegado a las coordenadas que mandó
             */
            outbox = open.createReply();
            outbox.setPerformative(ACLMessage.INFORM);
            outbox.setConversationId(sessionKey);
            outbox.setInReplyTo("MOVE " + myX + " " + myY + " " + myZ);
            outbox.setContent("MOVE " + myX + " " + myY + " " + myZ);
            this.LARVAsend(outbox);

            // Mostrar los jedis que se han encontrado
            jedisEncontrados.forEach(s -> {
                Info("Hemos encontrado: " + s);
            });

            return Status.SOLVEPROBLEM;
        } else if (open.getPerformative() == ACLMessage.CANCEL) {
            return Status.CHECKOUT;
        } else {
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
        
        
        Info("COMPASS: " + compass);
        Info("ANGULAR: " + myDashboard.getAngular(p));
        
        alturaTie = myZ; //aqui recibe la que le diga el destroyer
        double alturaActual = myDashboard.getGPS()[2];
      
        if(alturaActual == alturaTie){
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
            if(alturaActual < alturaTie){
            nextAction = "UP";
            }
            else if(alturaActual > alturaTie){
                nextAction = "DOWN";
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
